package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.ChatFormatting

internal enum class ItemAbility(
	val abilityName: String,
	private val cooldownSeconds: Int,
	val alternativePosition: Boolean,
	private val ignoreMageReduction: Boolean,
	val newVariant: Boolean,
	private val names: Array<out String>
) {
	WITHER_IMPACT(5, ignoreMageReduction = true),
	WITHER_SHIELD_SCROLL(10, alternativePosition = true, ignoreMageReduction = true),
	SHADOW_WARP_SCROLL(10),
	IMPLOSION_SCROLL(10),
	GYROKINETIC_WAND_LEFT(30, "GYROKINETIC_WAND", alternativePosition = true),
	GYROKINETIC_WAND_RIGHT(10, "GYROKINETIC_WAND"),
	GIANTS_SWORD(30),
	ICE_SPRAY_WAND(5, "STARRED_ICE_SPRAY_WAND"),
	RAGNAROCK_AXE(20),
	WAND_OF_ATONEMENT(7, "WAND_OF_HEALING", "WAND_OF_MENDING", "WAND_OF_RESTORATION"),
	SOS_FLARE(10),
	ALERT_FLARE(20, "WARNING_FLARE"),
	GOLEM_SWORD(3),
	END_STONE_SWORD(5),
	SOUL_ESOWARD(20),
	PIGMAN_SWORD(5),
	EMBER_ROD(30),
	STAFF_OF_THE_VOLCANO(30),
	STARLIGHT_WAND(2),
	VOODOO_DOLL(5),
	WEIRD_TUBA(20),
	WEIRDER_TUBA(30),
	FIRE_FREEZE_STAFF(10),
	SWORD_OF_BAD_HEALTH(5),
	WITHER_CLOAK(10),
	HOLY_ICE(4),
	VOODOO_DOLL_WILTED(3),
	FIRE_FURY_STAFF(20),
	SHADOW_FURY(15, "STARRED_SHADOW_FURY"),
	ROYAL_PIGEON(5),
	WAND_OF_STRENGTH(10),
	TACTICAL_INSERTION(20),
	TOTEM_OF_CORRUPTION(20),
	ENRAGER(20),
	ENDER_BOW("Ender Warp", 5, "Ender Bow"),
	LIVID_DAGGER("Throw", 5, "Livid Dagger"),
	FIRE_VEIL("Fire Veil", 5, "Fire Veil Wand"),
	INK_WAND("Ink Bomb", 30, "Ink Wand"),
	ROGUE_SWORD("Speed Boost", 30, "Rogue Sword", ignoreMageReduction = true),
	TALBOTS_THEODOLITE("Track", 10, "Talbot's Theodolite"),
	ATOMSPLIT_KATANA("Soulcry", 4, "Atomsplit Katana", "Vorpal Katana", "Voidedge Katana", ignoreMageReduction = true),
	ECHO("Echo", 3, "Ancestral Spade");

	constructor(
		cooldownSeconds: Int,
		vararg ids: String,
		alternativePosition: Boolean = false,
		ignoreMageReduction: Boolean = false
	) : this("", cooldownSeconds, alternativePosition, ignoreMageReduction, true, ids)

	constructor(
		abilityName: String,
		cooldownSeconds: Int,
		vararg displayNames: String,
		ignoreMageReduction: Boolean = false
	) : this(abilityName, cooldownSeconds, false, ignoreMageReduction, false, displayNames)

	val textMemo = DhenType.memo()
	var lastActivation = FAR_PAST
	var lastItemClick = FAR_PAST
	var lastHeld = FAR_PAST
	var specialColor: ChatFormatting? = null
	var label = ""
		private set
	var ink = 0
		private set
	var onCooldown = false
		private set

	fun matches(id: String, displayName: String): Boolean =
		if (newVariant) id == name || names.any { it == id } else names.any { displayName.contains(it) }

	fun multiplier(): Double = if (ignoreMageReduction) 1.0 else ItemAbilities.mageCooldownMultiplier

	fun cooldownMillis(): Long {
		val base = cooldownSeconds * MILLIS
		if (this == WAND_OF_ATONEMENT || this == RAGNAROCK_AXE) return base
		return (base * multiplier()).toLong()
	}

	fun activate(now: Long, color: ChatFormatting? = null, customMillis: Long = cooldownSeconds * MILLIS) {
		specialColor = color
		lastActivation = now - (cooldownSeconds * MILLIS - customMillis)
	}

	fun isOnCooldown(now: Long): Boolean = now - lastActivation < cooldownMillis()

	fun remaining(now: Long): Long = lastActivation + cooldownMillis() - now

	fun sound(now: Long) {
		if (now - lastItemClick < CLICK_WINDOW) activate(now)
	}

	fun recentlyHeld(now: Long): Boolean = now - lastHeld < HELD_WINDOW

	fun forget() {
		lastActivation = FAR_PAST
		specialColor = null
		onCooldown = false
		label = ""
	}

	fun refresh(now: Long, readyLabel: String, builder: StringBuilder) {
		if (isOnCooldown(now)) {
			onCooldown = true
			ink = legacyColor(specialColor ?: if (remaining(now) < RED_THRESHOLD) ChatFormatting.RED else ChatFormatting.YELLOW)
			label = durationText(now, builder)
			return
		}
		val carried = specialColor
		if (carried != null) {
			specialColor = null
			nextPhase(now, carried)
			if (isOnCooldown(now)) {
				refresh(now, readyLabel, builder)
				return
			}
		}
		onCooldown = false
		ink = legacyColor(ChatFormatting.GREEN)
		label = readyLabel
	}

	private fun nextPhase(now: Long, carried: ChatFormatting) {
		when (this) {
			GYROKINETIC_WAND_RIGHT -> if (carried == ChatFormatting.BLUE) activate(now, null, GYRO_SECOND_PHASE)
			RAGNAROCK_AXE -> if (carried == ChatFormatting.DARK_PURPLE) {
				activate(now, null, phaseMillis(RAGNAROCK_TOTAL, RAGNAROCK_SPENT))
			}

			WITHER_SHIELD_SCROLL -> if (carried == ChatFormatting.DARK_PURPLE) {
				activate(now, null, phaseMillis(SHIELD_TOTAL, SHIELD_SPENT))
			}

			else -> Unit
		}
	}

	private fun phaseMillis(total: Long, spent: Long): Long =
		((total * multiplier()) - spent).toLong().coerceAtLeast(0L)

	private fun durationText(now: Long, builder: StringBuilder): String {
		val left = remaining(now)
		builder.setLength(0)
		if (left < DECIMAL_THRESHOLD) {
			val tenths = (left + HALF_TENTH) / TENTH
			builder.append(tenths / TENTHS_PER_SECOND).append('.').append(tenths % TENTHS_PER_SECOND)
		} else {
			builder.append(left / MILLIS + 1)
		}
		return builder.toString()
	}

	companion object {
		val table: Array<ItemAbility> = entries.toTypedArray()

		fun of(item: SkyBlockItem, displayName: String, after: ItemAbility? = null): ItemAbility? {
			if (after == null) scrollAbility(scrollsOf(item))?.let { return it }
			val id = item.id
			var seen = after == null
			for (index in table.indices) {
				val ability = table[index]
				if (!seen) {
					if (ability === after) seen = true
					continue
				}
				if (ability.matches(id, displayName)) return ability
			}
			return null
		}

		fun byId(id: String): ItemAbility? {
			for (index in table.indices) {
				val ability = table[index]
				if (ability.newVariant && ability.matches(id, "")) return ability
			}
			return null
		}

		fun byAbilityName(abilityName: String): ItemAbility? {
			for (index in table.indices) {
				val ability = table[index]
				if (!ability.newVariant && ability.abilityName == abilityName) return ability
			}
			return null
		}

		fun scrollsOf(item: SkyBlockItem): Int {
			var held = 0
			for (scroll in item.abilityScrolls) {
				held = held or when (scroll) {
					ULTIMATE_WITHER_SCROLL -> SCROLL_ALL
					SHIELD_SCROLL_ID -> SCROLL_SHIELD
					WARP_SCROLL_ID -> SCROLL_WARP
					IMPLOSION_SCROLL_ID -> SCROLL_IMPLOSION
					else -> 0
				}
			}
			return held
		}

		fun scrollAbility(scrolls: Int): ItemAbility? = when {
			scrolls == SCROLL_ALL -> WITHER_IMPACT
			scrolls and SCROLL_SHIELD != 0 -> WITHER_SHIELD_SCROLL
			scrolls and SCROLL_WARP != 0 -> SHADOW_WARP_SCROLL
			scrolls and SCROLL_IMPLOSION != 0 -> IMPLOSION_SCROLL
			else -> null
		}

		fun forgetAll() {
			for (index in table.indices) table[index].forget()
		}
	}
}

internal const val SCROLL_SHIELD = 1
internal const val SCROLL_WARP = 2
internal const val SCROLL_IMPLOSION = 4
internal const val SCROLL_ALL = SCROLL_SHIELD or SCROLL_WARP or SCROLL_IMPLOSION
internal const val FAR_PAST = Long.MIN_VALUE / 2
internal const val CLICK_WINDOW = 400L
internal const val HELD_WINDOW = 30_000L

private const val MILLIS = 1000L
private const val RED_THRESHOLD = 600L
private const val DECIMAL_THRESHOLD = 1600L
private const val TENTH = 100L
private const val HALF_TENTH = 50L
private const val TENTHS_PER_SECOND = 10L
private const val GYRO_SECOND_PHASE = 4_000L
private const val RAGNAROCK_TOTAL = 20_000L
private const val RAGNAROCK_SPENT = 13_000L
private const val SHIELD_TOTAL = 10_000L
private const val SHIELD_SPENT = 5_000L
private const val ULTIMATE_WITHER_SCROLL = "ULTIMATE_WITHER_SCROLL"
private const val SHIELD_SCROLL_ID = "WITHER_SHIELD_SCROLL"
private const val WARP_SCROLL_ID = "SHADOW_WARP_SCROLL"
private const val IMPLOSION_SCROLL_ID = "IMPLOSION_SCROLL"
