package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.stats.ActionBarSegment
import io.github.dzkchen.dhen.data.stats.PlayerStats
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.PlayerStatsEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object PlayerStatsHud : Module(
	name = "Player Stats HUD",
	category = Category.VISUAL,
	description = "Shows movable SkyBlock health, defense, mana, vitality, EHP, and speed readouts."
) {
	internal val healthSetting = BooleanSetting("Health")
	internal val healthColorSetting = ColorSetting(
		"Health Color",
		Color.rgba(255, 85, 85),
		allowAlpha = true
	).withDependency { healthShown }
	internal val defenseSetting = BooleanSetting("Defense")
	internal val defenseColorSetting = ColorSetting(
		"Defense Color",
		Color.rgba(85, 255, 85),
		allowAlpha = true
	).withDependency { defenseShown }
	internal val manaSetting = BooleanSetting("Mana")
	internal val manaColorSetting = ColorSetting(
		"Mana Color",
		Color.rgba(85, 255, 255),
		allowAlpha = true
	).withDependency { manaShown }
	internal val overflowSetting = BooleanSetting("Overflow Mana")
	internal val overflowColorSetting = ColorSetting(
		"Overflow Mana Color",
		Color.rgba(0, 170, 170),
		allowAlpha = true
	).withDependency { overflowShown }
	internal val vitalitySetting = BooleanSetting("Vitality")
	internal val vitalityColorSetting = ColorSetting(
		"Vitality Color",
		Color.rgba(170, 0, 0),
		allowAlpha = true
	).withDependency { vitalityShown }
	internal val effectiveHealthSetting = BooleanSetting("Effective HP")
	internal val effectiveHealthColorSetting = ColorSetting(
		"Effective HP Color",
		Color.rgba(0, 170, 0),
		allowAlpha = true
	).withDependency { effectiveHealthShown }
	internal val speedSetting = BooleanSetting("Speed")
	internal val speedColorSetting = ColorSetting(
		"Speed Color",
		Color.rgba(255, 255, 255),
		allowAlpha = true
	).withDependency { speedShown }

	internal val separateOverflowSetting = BooleanSetting("Separate Overflow Mana", true)
	internal val hideZeroOverflowSetting = BooleanSetting("Hide 0 Overflow", true)
		.withDependency { separateOverflow }
	internal val showIconsSetting = BooleanSetting("Show Icons", true)

	internal val hideHealthSetting = BooleanSetting("Hide Health From Action Bar", true)
	internal val hideDefenseSetting = BooleanSetting("Hide Defense From Action Bar", true)
	internal val hideManaSetting = BooleanSetting("Hide Mana From Action Bar", true)
	internal val hideOverflowSetting = BooleanSetting("Hide Overflow Mana From Action Bar", true)
	internal val hideVitalitySetting = BooleanSetting("Hide Vitality From Action Bar", true)
	internal val hideSecretsSetting = BooleanSetting("Hide Dungeon Room Secrets From Action Bar")
	internal val hideArmorStacksSetting = BooleanSetting("Hide Armor Stacks From Action Bar")
	internal val hideTerminatorStacksSetting = BooleanSetting("Hide Terminator Stacks From Action Bar")

	internal val hideHeartsSetting = BooleanSetting("Hide Hearts")
	internal val hideFoodSetting = BooleanSetting("Hide Food")
	internal val hideArmorSetting = BooleanSetting("Hide Armor")
	internal val hideExperienceSetting = BooleanSetting("Hide XP")
	private var healthShown by healthSetting
	private var healthColor by healthColorSetting
	private var defenseShown by defenseSetting
	private var defenseColor by defenseColorSetting
	private var manaShown by manaSetting
	private var manaColor by manaColorSetting
	private var overflowShown by overflowSetting
	private var overflowColor by overflowColorSetting
	private var vitalityShown by vitalitySetting
	private var vitalityColor by vitalityColorSetting
	private var effectiveHealthShown by effectiveHealthSetting
	private var effectiveHealthColor by effectiveHealthColorSetting
	private var speedShown by speedSetting
	private var speedColor by speedColorSetting
	private var separateOverflow by separateOverflowSetting
	private var hideZeroOverflow by hideZeroOverflowSetting
	private var showIcons by showIconsSetting
	private var hideHealth by hideHealthSetting
	private var hideDefense by hideDefenseSetting
	private var hideMana by hideManaSetting
	private var hideOverflow by hideOverflowSetting
	private var hideVitality by hideVitalitySetting
	private var hideSecrets by hideSecretsSetting
	private var hideArmorStacks by hideArmorStacksSetting
	private var hideTerminatorStacks by hideTerminatorStacksSetting
	private var hideHearts by hideHeartsSetting
	private var hideFood by hideFoodSetting
	private var hideArmor by hideArmorSetting
	private var hideExperience by hideExperienceSetting

	internal val healthElement = stat(PlayerStat.HEALTH, healthSetting) { healthColor.argb }
	internal val defenseElement = stat(PlayerStat.DEFENSE, defenseSetting) { defenseColor.argb }
	internal val manaElement = stat(PlayerStat.MANA, manaSetting) { manaColor.argb }
	internal val overflowElement = stat(PlayerStat.OVERFLOW, overflowSetting) { overflowColor.argb }
	internal val vitalityElement = stat(PlayerStat.VITALITY, vitalitySetting) { vitalityColor.argb }
	internal val effectiveHealthElement = stat(
		PlayerStat.EFFECTIVE_HEALTH,
		effectiveHealthSetting
	) { effectiveHealthColor.argb }
	internal val speedElement = stat(PlayerStat.SPEED, speedSetting) { speedColor.argb }

	init {
		on<PlayerStatsEvent> { refresh() }
		on<ClientTickEvent.End> {
			synchronizeHiddenSegments()
			refresh()
		}
	}

	override fun onEnabled() {
		synchronizeHiddenSegments()
		refresh()
	}

	override fun onDisabled() {
		clearHiddenSegments()
	}

	override fun onReset() {
		synchronizeHiddenSegments()
		refresh()
	}

	@JvmStatic
	fun shouldHideHearts(): Boolean = hidesVanilla(hideHearts, SkyBlockLocation.inSkyBlock)

	@JvmStatic
	fun shouldHideFood(): Boolean = hidesVanilla(hideFood, SkyBlockLocation.inSkyBlock)

	@JvmStatic
	fun shouldHideArmor(): Boolean = hidesVanilla(hideArmor, SkyBlockLocation.inSkyBlock)

	@JvmStatic
	fun shouldHideExperience(): Boolean = hidesVanilla(hideExperience, SkyBlockLocation.inSkyBlock)

	internal fun hidesVanilla(selected: Boolean, inSkyBlock: Boolean): Boolean = enabled && inSkyBlock && selected

	internal fun synchronizeHiddenSegments() {
		synchronizeHiddenSegment(ActionBarSegment.HEALTH, hideHealth)
		synchronizeHiddenSegment(ActionBarSegment.DEFENSE, hideDefense)
		synchronizeHiddenSegment(ActionBarSegment.MANA, hideMana)
		synchronizeHiddenSegment(ActionBarSegment.OVERFLOW_MANA, hideOverflow)
		synchronizeHiddenSegment(ActionBarSegment.VITALITY, hideVitality)
		synchronizeHiddenSegment(ActionBarSegment.SECRETS, hideSecrets)
		synchronizeHiddenSegment(ActionBarSegment.ARMOR_STACKS, hideArmorStacks)
		synchronizeHiddenSegment(ActionBarSegment.TERMINATOR_STACKS, hideTerminatorStacks)
	}

	internal fun clearHiddenSegments() {
		PlayerStats.hide(ActionBarSegment.HEALTH, false)
		PlayerStats.hide(ActionBarSegment.DEFENSE, false)
		PlayerStats.hide(ActionBarSegment.MANA, false)
		PlayerStats.hide(ActionBarSegment.OVERFLOW_MANA, false)
		PlayerStats.hide(ActionBarSegment.VITALITY, false)
		PlayerStats.hide(ActionBarSegment.SECRETS, false)
		PlayerStats.hide(ActionBarSegment.ARMOR_STACKS, false)
		PlayerStats.hide(ActionBarSegment.TERMINATOR_STACKS, false)
	}

	internal fun refresh() {
		healthElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		defenseElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		manaElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		overflowElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		vitalityElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		effectiveHealthElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
		speedElement.update(showIcons, separateOverflow, hideZeroOverflow, overflowShown)
	}

	private fun synchronizeHiddenSegment(segment: ActionBarSegment, selected: Boolean) {
		val hidden = enabled && selected
		if (PlayerStats.hidden(segment) != hidden) PlayerStats.hide(segment, hidden)
	}

	private fun stat(
		stat: PlayerStat,
		toggle: BooleanSetting,
		ink: () -> Int
	): PlayerStatElement = hud(PlayerStatElement(stat, toggle, ink))
}

internal enum class PlayerStat(val label: String) {
	HEALTH("Health"),
	DEFENSE("Defense"),
	MANA("Mana"),
	OVERFLOW("Overflow Mana"),
	VITALITY("Vitality"),
	EFFECTIVE_HEALTH("Effective HP"),
	SPEED("Speed")
}

internal class PlayerStatElement(
	private val stat: PlayerStat,
	private val toggle: BooleanSetting,
	private val ink: () -> Int
) : HudElement(stat.label) {
	private val memo = DhenType.memo()
	private val builder = StringBuilder(TEXT_CAPACITY)
	private val digits = CharArray(DIGIT_CAPACITY)
	private var first = Int.MIN_VALUE
	private var second = Int.MIN_VALUE
	private var overflow = Int.MIN_VALUE
	private var flags = Int.MIN_VALUE
	internal var liveText: String = ""
		private set

	override val hasContent: Boolean
		get() = contentAvailable(SkyBlockLocation.inSkyBlock, editingHud())

	override fun width(font: Font): Int = memo.width(font, shownText(editingHud()))

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val editing = editingHud()
		val color = if (!editing && stat == PlayerStat.HEALTH && first > second) OVERHEAL_COLOR else ink()
		memo.shadowed(graphics, font, shownText(editing), 0, 0, color, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	internal fun contentAvailable(inSkyBlock: Boolean, editing: Boolean): Boolean =
		toggle.on && (editing || inSkyBlock && liveText.isNotEmpty())

	internal fun shownText(editing: Boolean): String = if (editing) exampleText() else liveText

	internal fun update(
		showIcons: Boolean,
		separateOverflow: Boolean,
		hideZeroOverflow: Boolean,
		overflowEnabled: Boolean
	) {
		val nextFirst = firstValue()
		val nextSecond = secondValue()
		val nextOverflow = if (stat == PlayerStat.MANA) PlayerStats.overflowMana else 0
		val nextFlags = (if (showIcons) ICONS else 0) or
			(if (separateOverflow) SEPARATE_OVERFLOW else 0) or
			(if (hideZeroOverflow) HIDE_ZERO_OVERFLOW else 0) or
			(if (stat == PlayerStat.MANA && overflowEnabled) OVERFLOW_ENABLED else 0) or
			(if (stat == PlayerStat.VITALITY && PlayerStats.vitalityShown) VITALITY_SHOWN else 0)
		if (first == nextFirst && second == nextSecond && overflow == nextOverflow && flags == nextFlags) return
		first = nextFirst
		second = nextSecond
		overflow = nextOverflow
		flags = nextFlags
		builder.setLength(0)
		when (stat) {
			PlayerStat.HEALTH -> pair("❤")
			PlayerStat.DEFENSE -> if (first != 0) single("❈")
			PlayerStat.MANA -> {
				pair("✎")
				if (flag(OVERFLOW_ENABLED) && !flag(SEPARATE_OVERFLOW) && (!flag(HIDE_ZERO_OVERFLOW) || overflow != 0)) {
					builder.append(' ')
					appendNumber(overflow)
					icon("ʬ")
				}
			}
			PlayerStat.OVERFLOW -> if (flag(SEPARATE_OVERFLOW) && (!flag(HIDE_ZERO_OVERFLOW) || first != 0)) single("ʬ")
			PlayerStat.VITALITY -> if (flag(VITALITY_SHOWN)) pair("♨")
			PlayerStat.EFFECTIVE_HEALTH -> appendNumber(first)
			PlayerStat.SPEED -> if (first != 0) single("✦")
		}
		liveText = if (builder.isEmpty()) "" else builder.toString()
	}

	private fun firstValue(): Int = when (stat) {
		PlayerStat.HEALTH -> PlayerStats.health
		PlayerStat.DEFENSE -> PlayerStats.defense
		PlayerStat.MANA -> PlayerStats.mana
		PlayerStat.OVERFLOW -> PlayerStats.overflowMana
		PlayerStat.VITALITY -> PlayerStats.vitality
		PlayerStat.EFFECTIVE_HEALTH -> PlayerStats.effectiveHp
		PlayerStat.SPEED -> PlayerStats.speed
	}

	private fun secondValue(): Int = when (stat) {
		PlayerStat.HEALTH -> PlayerStats.maxHealth
		PlayerStat.MANA -> PlayerStats.maxMana
		PlayerStat.VITALITY -> PlayerStats.maxVitality
		else -> 0
	}

	private fun pair(icon: String) {
		appendNumber(first)
		builder.append('/')
		appendNumber(second)
		icon(icon)
	}

	private fun single(icon: String) {
		appendNumber(first)
		icon(icon)
	}

	private fun icon(icon: String) {
		if (flag(ICONS)) builder.append(icon)
	}

	private fun appendNumber(value: Int) {
		var magnitude = if (value < 0) -value.toLong() else value.toLong()
		var cursor = digits.size
		var group = 0
		do {
			if (group == GROUP_SIZE) {
				digits[--cursor] = ','
				group = 0
			}
			digits[--cursor] = ('0'.code + (magnitude % 10).toInt()).toChar()
			magnitude /= 10
			group++
		} while (magnitude != 0L)
		if (value < 0) digits[--cursor] = '-'
		builder.appendRange(digits, cursor, digits.size)
	}

	private fun flag(mask: Int): Boolean = flags and mask != 0

	private fun exampleText(): String = when (stat) {
		PlayerStat.HEALTH -> if (flag(ICONS)) "3,000/4,000❤" else "3,000/4,000"
		PlayerStat.DEFENSE -> if (flag(ICONS)) "1,000❈" else "1,000"
		PlayerStat.MANA -> when {
			flag(SEPARATE_OVERFLOW) && flag(ICONS) -> "2,000/20,000✎"
			flag(SEPARATE_OVERFLOW) -> "2,000/20,000"
			flag(ICONS) -> "2,000/20,000✎ 333ʬ"
			else -> "2,000/20,000 333"
		}
		PlayerStat.OVERFLOW -> if (flag(ICONS)) "333ʬ" else "333"
		PlayerStat.VITALITY -> if (flag(ICONS)) "100/100♨" else "100/100"
		PlayerStat.EFFECTIVE_HEALTH -> "1,000,000"
		PlayerStat.SPEED -> if (flag(ICONS)) "100✦" else "100"
	}

	private companion object {
		val OVERHEAL_COLOR = Color.rgba(255, 255, 85).argb
		const val TEXT_CAPACITY = 32
		const val DIGIT_CAPACITY = 16
		const val GROUP_SIZE = 3
		const val ICONS = 1
		const val SEPARATE_OVERFLOW = 2
		const val HIDE_ZERO_OVERFLOW = 4
		const val VITALITY_SHOWN = 8
		const val OVERFLOW_ENABLED = 16
	}
}
