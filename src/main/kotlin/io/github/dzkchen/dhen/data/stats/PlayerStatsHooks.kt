package io.github.dzkchen.dhen.data.stats

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ActionBarEvent
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PlayerStatsEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.roundToInt

internal object PlayerStatsHooks : GuardedHooks<PlayerStatsHooks.Channels> {
	override val feed = "Player stats"

	private const val NO_PLAYER = -1f

	override val failsafe = Failsafe("Dhen {} failed, its action bar stats are off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(
		bus: EventBus,
		inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock },
		inDungeon: () -> Boolean = { SkyBlockLocation.island == Island.CATACOMBS },
		healthRatio: () -> Float = ::vanillaHealthRatio,
		walkSpeed: () -> Double = ::vanillaWalkSpeed
	) {
		uninstall()
		channels = Channels(bus, inDungeon, healthRatio, walkSpeed)
		subscriptions = arrayOf(
			bus.subscribe<ActionBarEvent>(BEFORE_FEATURES) { if (inSkyBlock()) parsed(it) },
			bus.subscribe<ClientTickEvent.End>(BEFORE_FEATURES) { if (inSkyBlock()) ticked() },
			bus.subscribe<WorldChangeEvent> { if (it.phase != WorldChange.INIT) forget() },
			bus.subscribe<IslandChangeEvent> { if (it.resetsWorldState) forget() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		PlayerStats.reset()
	}

	override fun bound() = channels

	private fun parsed(event: ActionBarEvent) = guarded("action bar parse") { it.parse(event) }

	private fun ticked() = guarded("action bar tick") { it.follow() }

	private fun forget() = guarded("action bar world change") { PlayerStats.reset() }

	private fun vanillaHealthRatio(): Float {
		val player = Minecraft.getInstance().player ?: return NO_PLAYER
		return player.health / player.maxHealth
	}

	private fun vanillaWalkSpeed(): Double {
		val player = Minecraft.getInstance().player ?: return 0.0
		return player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
	}

	internal class Channels(
		bus: EventBus,
		private val inDungeon: () -> Boolean,
		private val healthRatio: () -> Float,
		private val walkSpeed: () -> Double
	) {
		private val updates = bus.type<PlayerStatsEvent>()
		private val health = matcher("(?:\u00a7.)?([\\d,]+)/([\\d,]+)(?:\u00a7.)?[\uE010❤]")
		private val defense = matcher("(?:\u00a7.)?([\\d,]+)(?:\u00a7.)?[\uE008❈](?: Defense)?")
		private val mana = matcher("(?:\u00a7.)?([\\d,]+)/([\\d,]+)(?:\u00a7.)?[\uE003✎](?: Mana)?")
		private val overflow = matcher("(?:\u00a7.)?([\\d,]+)(?:\u00a7.)?[\uE017ʬ]")
		private val vitality = matcher("(?:\u00a7.)?([\\d.,]+)/([\\d.,]+)(?:\u00a7.)?[\uE028♨](?: Vitality)?")
		private val stacks = matcher("(?:\u00a7.)?([\\d,]+)(?:\u00a7.)?([ᝐ⁑Ѫ])")
		private val salvation = matcher("T([1-3])!")
		private val manaUsage = matcher("\u00a7b-([\\d,]+) Mana \\(\u00a76.+?\u00a7b\\)|\u00a7c\u00a7lNOT ENOUGH MANA")
		private val secrets = matcher("\\s*(?:\u00a7.)?(\\d+)/(\\d+) Secrets")

		fun parse(event: ActionBarEvent) {
			val line = event.styled
			read(line)
			if (PlayerStats.anyHidden) event.text = Component.literal(withoutHiddenSegments(line))
			updates.dispatch(PlayerStatsEvent)
		}

		fun follow() {
			val ratio = healthRatio()
			if (ratio >= 0f) PlayerStats.health = (PlayerStats.maxHealth * ratio).toInt()
			PlayerStats.speed = (walkSpeed() * 1000.0).roundToInt()
		}

		private fun read(line: String) {
			if (health.reset(line).find()) {
				PlayerStats.health = number(line, health, 1, PlayerStats.health)
				PlayerStats.maxHealth = number(line, health, 2, PlayerStats.maxHealth)
			}
			if (defense.reset(line).find()) PlayerStats.defense = number(line, defense, 1, PlayerStats.defense)
			if (mana.reset(line).find()) {
				PlayerStats.mana = number(line, mana, 1, PlayerStats.mana)
				PlayerStats.maxMana = number(line, mana, 2, PlayerStats.maxMana)
			}
			PlayerStats.overflowMana = if (overflow.reset(line).find()) number(line, overflow, 1, 0) else 0
			if (manaUsage.reset(line).find()) {
				PlayerStats.mana = (PlayerStats.mana - number(line, manaUsage, 1, 0)).coerceAtLeast(0)
			}
			PlayerStats.vitalityShown = vitality.reset(line).find()
			if (PlayerStats.vitalityShown) {
				PlayerStats.vitality = number(line, vitality, 1, PlayerStats.vitality)
				PlayerStats.maxVitality = number(line, vitality, 2, PlayerStats.maxVitality)
			}
			if (stacks.reset(line).find()) {
				PlayerStats.netherArmorStacks = number(line, stacks, 1, PlayerStats.netherArmorStacks)
				symbol(line[stacks.start(2)])
			}
			if (salvation.reset(line).find()) PlayerStats.salvation = number(line, salvation, 1, PlayerStats.salvation)
			if (inDungeon() && secrets.reset(line).find()) {
				PlayerStats.secrets = number(line, secrets, 1, PlayerStats.secrets)
				PlayerStats.maxSecrets = number(line, secrets, 2, PlayerStats.maxSecrets)
			} else {
				PlayerStats.secrets = 0
				PlayerStats.maxSecrets = 0
			}
		}

		private fun withoutHiddenSegments(line: String): String {
			var result = line
			if (PlayerStats.hidden(ActionBarSegment.HEALTH)) result = health.reset(result).replaceAll("")
			if (PlayerStats.hidden(ActionBarSegment.DEFENSE)) result = defense.reset(result).replaceAll("")
			if (PlayerStats.hidden(ActionBarSegment.MANA)) result = mana.reset(result).replaceAll("")
			if (PlayerStats.hidden(ActionBarSegment.OVERFLOW_MANA)) result = overflow.reset(result).replaceAll("")
			if (PlayerStats.hidden(ActionBarSegment.VITALITY)) result = vitality.reset(result).replaceAll("")
			if (PlayerStats.hidden(ActionBarSegment.SECRETS)) result = secrets.reset(result).replaceAll("")
			return result.trim()
		}

		private fun symbol(marker: Char) {
			if (PlayerStats.stackSymbol.length == 1 && PlayerStats.stackSymbol[0] == marker) return
			PlayerStats.stackSymbol = marker.toString()
		}

		private fun number(line: String, matcher: Matcher, group: Int, fallback: Int): Int {
			var index = matcher.start(group)
			if (index < 0) return fallback
			val end = matcher.end(group)
			var value = 0L
			var digits = 0
			while (index < end) {
				val char = line[index++]
				if (char == '.') break
				if (char == ',') continue
				value = value * 10 + (char - '0')
				if (value > Int.MAX_VALUE) return fallback
				digits++
			}
			return if (digits == 0) fallback else value.toInt()
		}

		private fun matcher(pattern: String): Matcher = Pattern.compile(pattern).matcher("")
	}
}
