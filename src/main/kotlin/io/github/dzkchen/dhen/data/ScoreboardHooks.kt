package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.ScoreboardAreaChangeEvent
import io.github.dzkchen.dhen.event.ScoreboardUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard

internal object ScoreboardHooks : GuardedHooks<ScoreboardHooks.Channels> {
	override val feed = "Scoreboard"

	private const val SIDEBAR_LINES = 15

	private val displayOrder: Comparator<PlayerScoreEntry> =
		compareByDescending<PlayerScoreEntry> { it.value() }
			.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }

	private val areaLine = Regex("\\s*\u00a7\\d. \u00a7.(.*)")

	override val failsafe = Failsafe("Dhen {} failed, its scoreboard state is off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, sidebar: () -> Scoreboard? = { Minecraft.getInstance().level?.scoreboard }) {
		uninstall()
		channels = Channels(bus, sidebar)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post>(BEFORE_FEATURES) { received(it.packet) },
			bus.subscribe<ClientTickEvent.Start>(BEFORE_FEATURES) { ticked() },
			bus.subscribe<WorldChangeEvent> { if (it.phase != WorldChange.INIT) forget() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		ScoreboardState.reset()
	}

	override fun bound() = channels

	fun refresh() = guarded("scoreboard read") { it.refresh() }

	private fun received(packet: Packet<*>) {
		if (!changesSidebar(packet)) return
		guarded("scoreboard packet") { it.dirty() }
	}

	private fun ticked() = guarded("scoreboard tick") { it.flush() }

	private fun forget() = guarded("scoreboard world change") { it.forget() }

	private fun changesSidebar(packet: Packet<*>): Boolean = packet is ClientboundSetScorePacket ||
		packet is ClientboundResetScorePacket ||
		packet is ClientboundSetObjectivePacket ||
		packet is ClientboundSetDisplayObjectivePacket ||
		packet is ClientboundSetPlayerTeamPacket

	internal class Channels(bus: EventBus, private val sidebar: () -> Scoreboard?) {
		private val updates = bus.type<ScoreboardUpdateEvent>()
		private val areas = bus.type<ScoreboardAreaChangeEvent>()
		private var pending = false

		fun dirty() {
			pending = true
		}

		fun forget() {
			pending = false
			cleared()
		}

		fun flush() {
			if (!pending) return
			pending = false
			refresh()
		}

		fun refresh() {
			val scoreboard = sidebar()
			val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return cleared()
			ScoreboardState.heading(objective.name, legacyCodes(objective.displayName))
			HypixelLocationHooks.scoreboardTitled(objective.name, ScoreboardState.strippedTitle)
			val lines = ArrayList<String>(SIDEBAR_LINES)
			val stripped = ArrayList<String>(SIDEBAR_LINES)
			for (entry in scoreboard.listPlayerScores(objective).sortedWith(displayOrder)) {
				if (entry.isHidden) continue
				if (lines.size == SIDEBAR_LINES) break
				val line = joined(scoreboard.getPlayersTeam(entry.owner()), entry)
				lines += line
				stripped += withoutCodes(line)
			}
			publish(lines, stripped)
		}

		private fun cleared() {
			ScoreboardState.heading("", "")
			HypixelLocationHooks.scoreboardTitled("", "")
			publish(emptyList(), emptyList())
		}

		private fun publish(lines: List<String>, stripped: List<String>) {
			val previous = ScoreboardState.lines
			if (!ScoreboardState.read(lines, stripped)) return
			updates.dispatch(ScoreboardUpdateEvent(lines, previous))
			locate(lines)
		}

		private fun locate(lines: List<String>) {
			val previous = ScoreboardState.area
			val area = lines.firstNotNullOfOrNull { areaLine.matchEntire(it)?.groupValues?.get(1) }
			if (ScoreboardState.locate(area?.let(::withoutCodes))) {
				areas.dispatch(ScoreboardAreaChangeEvent(ScoreboardState.area, previous))
			}
		}

		private fun joined(team: PlayerTeam?, entry: PlayerScoreEntry): String {
			team ?: return legacyCodes(entry.ownerName())
			val prefix = legacyCodes(team.playerPrefix)
			val suffix = legacyCodes(team.playerSuffix)
			val active = lastCodeRun(prefix)
			var carried = 0
			while (carried + 1 < suffix.length && suffix[carried] == CODE && active.holds(suffix, carried)) carried += 2
			return prefix + suffix.substring(carried)
		}

		private fun lastCodeRun(text: String): String {
			var end = text.length - 1
			while (end >= 1 && text[end - 1] != CODE) end--
			if (end < 1) return ""
			end++
			var start = end
			while (start >= 2 && text[start - 2] == CODE) start -= 2
			return text.substring(start, end)
		}

		private fun String.holds(code: String, at: Int): Boolean {
			var index = 0
			while (index + 1 < length) {
				if (this[index + 1] == code[at + 1]) return true
				index += 2
			}
			return false
		}
	}

	private const val CODE = '\u00a7'
}
