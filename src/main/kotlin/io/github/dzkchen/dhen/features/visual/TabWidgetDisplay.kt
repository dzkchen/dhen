package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.memoAt
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal enum class WidgetGroup(val label: String, vararg val widgets: TabWidget) {
	SOULFLOW("Soulflow", TabWidget.SOULFLOW),
	COINS("Bank and Interest", TabWidget.BANK, TabWidget.INTEREST),
	SB_LEVEL("Skyblock Level", TabWidget.SB_LEVEL),
	PROFILE("Profile", TabWidget.PROFILE),
	PLAYER_LIST("Players", TabWidget.PLAYER_LIST),
	PET("Pet", TabWidget.PET),
	PET_TRAINING("Pet Upgrade Info", TabWidget.PET_SITTER, TabWidget.PET_TRAINING),
	STATS("Stats", TabWidget.STATS, TabWidget.DUNGEON_SKILLS_AND_STATS),
	DUNGEON_TEAM("Dungeon Info about every person", TabWidget.DUNGEON_PARTY),
	DUNGEON_PUZZLE("Dungeon Info about puzzles", TabWidget.DUNGEON_PUZZLE),
	DUNGEON_OVERALL("Dungeon General Info (very long)", TabWidget.DUNGEON_STATS),
	BESTIARY("Bestiary", TabWidget.BESTIARY),
	DRAGON("Dragon Fight Info", TabWidget.DRAGON),
	PROTECTOR("Protector State", TabWidget.PROTECTOR),
	SHEN_RIFT("Shen's Auction inside the Rift", TabWidget.RIFT_SHEN),
	MINION("Minion Info", TabWidget.MINION),
	COLLECTION("Collection", TabWidget.COLLECTION),
	TIMERS("Timers", TabWidget.TIMERS),
	FIRE_SALE("Fire Sale", TabWidget.FIRE_SALE),
	RAIN("Park Rain", TabWidget.RAIN),
	PEST_TRAPS("Pest Traps", TabWidget.PEST_TRAPS, TabWidget.FULL_TRAPS, TabWidget.NO_BAIT),
	FULL_PROFILE_WIDGET(
		"Profile Widget",
		TabWidget.PROFILE,
		TabWidget.SB_LEVEL,
		TabWidget.BANK,
		TabWidget.INTEREST,
		TabWidget.SOULFLOW,
		TabWidget.FAIRY_SOULS
	),
	EYES("Eyes placed", TabWidget.EYES_PLACED),
	MOONGLADE_BEACON("Foraging Beacon", TabWidget.MOONGLADE_BEACON),
	STARBORN_TEMPLE("Starborn Temple", TabWidget.STARBORN_TEMPLE),
	SHARD_TRAPS("Shard Traps", TabWidget.SHARD_TRAPS),
	FOREST_WHISPERS("Whispers", TabWidget.FOREST_WHISPERS),
	AGATHA_CONTEST("Agatha's Contest", TabWidget.AGATHA_CONTEST),
	COMMISSIONS("Mining Commissions", TabWidget.COMMISSIONS),
	SLAYER("Slayer", TabWidget.SLAYER),
	PITY("Pity", TabWidget.PITY),
	PICKAXE_COOLDOWN("Pickaxe Cooldown", TabWidget.PICKAXE_COOLDOWN),
	MIRIA_CONTEST("Miria's Contest", TabWidget.MIRIA_CONTEST);

	companion object {
		val labels: List<String> = entries.map { it.label }
	}
}

object TabWidgetDisplay : Module(
	name = "Tab Widget Display",
	category = Category.VISUAL,
	description = "Lifts chosen tab list widgets out of the tab list into their own movable readouts."
) {
	internal val widgetsSetting = OrderedSelectionSetting(
		"Widgets",
		WidgetGroup.labels,
		emptyList(),
		description = "Which tab list widgets get a readout. A widget Hypixel is not showing draws nothing — " +
			"turn it on with /widget first. Every readout starts in the same corner, so move one before adding the next."
	)
	private val shownGroups by widgetsSetting

	internal val elements: List<TabWidgetElement> = WidgetGroup.entries.map { hud(TabWidgetElement(it)) }

	internal fun shows(group: WidgetGroup): Boolean = group.label in shownGroups
}

internal class TabWidgetElement(private val group: WidgetGroup) :
	HudElement(group.label, HudAnchor.TOP_LEFT, MARGIN, MARGIN) {
	private val memos = ArrayList<TextMemo>()

	override val listed: Boolean
		get() = TabWidgetDisplay.shows(group)

	override val hasContent: Boolean
		get() = contentAvailable(editingHud())

	override fun width(font: Font): Int {
		var widest = 0
		eachLine(editingHud()) { slot, line -> widest = maxOf(widest, memos.memoAt(slot).width(font, line)) }
		return widest
	}

	override fun height(font: Font): Int = shownCount(editingHud()) * (DhenType.lineHeight(font) + LINE_GAP)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val step = DhenType.lineHeight(font) + LINE_GAP
		eachLine(editingHud()) { slot, line ->
			memos.memoAt(slot).shadowed(graphics, font, line, 0, slot * step, textInk, scale)
		}
	}

	override fun invalidateMeasurement() {
		for (index in memos.indices) memos[index].invalidate()
	}

	internal fun contentAvailable(editing: Boolean): Boolean = listed && shownCount(editing) > 0

	internal fun shownCount(editing: Boolean): Int {
		var shown = 0
		eachLine(editing) { _, _ -> shown++ }
		return shown
	}

	internal fun shownLine(slot: Int, editing: Boolean): String {
		var shown = ""
		eachLine(editing) { index, line -> if (index == slot) shown = line }
		return shown
	}

	private inline fun eachLine(editing: Boolean, action: (Int, String) -> Unit) {
		var slot = 0
		val widgets = group.widgets
		for (index in widgets.indices) {
			val lines = TabWidgetState.lines(widgets[index])
			for (line in lines.indices) action(slot++, lines[line])
		}
		if (slot == 0 && editing) action(0, group.label)
	}

	private companion object {
		const val MARGIN = 2
		const val LINE_GAP = 1
	}
}
