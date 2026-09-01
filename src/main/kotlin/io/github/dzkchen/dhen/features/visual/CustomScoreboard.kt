package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PartyEvent
import io.github.dzkchen.dhen.event.QuiverUpdateEvent
import io.github.dzkchen.dhen.event.ScoreboardUpdateEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object CustomScoreboard : Module(
	name = "Custom Scoreboard",
	category = Category.VISUAL,
	description = "Replaces the vanilla SkyBlock sidebar with a movable Dhen scoreboard."
) {
	private val DEFAULT_LINES = listOf(
		ScoreboardLine.LOBBY_CODE,
		ScoreboardLine.SEPARATOR_1,
		ScoreboardLine.DATE,
		ScoreboardLine.TIME,
		ScoreboardLine.ISLAND,
		ScoreboardLine.LOCATION,
		ScoreboardLine.SEPARATOR_2,
		ScoreboardLine.PURSE,
		ScoreboardLine.BITS,
		ScoreboardLine.SEPARATOR_3,
		ScoreboardLine.QUIVER,
		ScoreboardLine.SEPARATOR_4,
		ScoreboardLine.SLAYER,
		ScoreboardLine.PARTY,
		ScoreboardLine.FOOTER,
		ScoreboardLine.EXTRA
	).map { it.label }

	internal val linesSetting = OrderedSelectionSetting(
		"Lines",
		ScoreboardLine.labels,
		DEFAULT_LINES,
		description = "Which typed lines the scoreboard shows, in the order it shows them."
	)
	private var enabledLines by linesSetting

	private val composer = ScoreboardComposer()

	internal val element = hud(CustomScoreboardElement())

	private var shownLines: List<String> = emptyList()

	init {
		on<ScoreboardUpdateEvent> { rebuild() }
		on<QuiverUpdateEvent> { rebuild() }
		on<PartyEvent> { rebuild() }
		on<IslandChangeEvent> { rebuild() }
		on<ClientTickEvent.End> { if (composer.faded() || enabledLines !== shownLines) rebuild() }
	}

	override fun onEnabled() = rebuild()

	internal fun rebuild() {
		composer.sampled()
		shownLines = enabledLines
		element.update(ScoreboardState.title, composer.compose(enabledLines))
	}

	@JvmStatic
	fun shouldHideVanilla(): Boolean = enabled
}

internal class CustomScoreboardElement : HudElement("Scoreboard", HudAnchor.MIDDLE_RIGHT, -RIGHT_MARGIN, 0) {
	private val titleMemo = DhenType.memo()
	private val lineMemos = Array(MAX_LINES) { DhenType.memo() }
	private val liveLines = Array(MAX_LINES) { "" }
	private var liveTitle = ""
	private var liveLineCount = 0

	override val hasContent: Boolean
		get() = contentAvailable(editingHud())

	override fun width(font: Font): Int = contentWidth(font, editingHud())

	override fun height(font: Font): Int {
		val count = shownCount(editingHud())
		val lineHeight = DhenType.lineHeight(font)
		return PADDING * 2 + lineHeight + TITLE_GAP + count * (lineHeight + LINE_GAP)
	}

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val editing = editingHud()
		val title = shownTitle(editing)
		val lines = shownLines(editing)
		val count = shownCount(editing)
		val width = contentWidth(font, editing)
		val height = height(font)
		val lineHeight = DhenType.lineHeight(font)
		GlassGui.roundedFrame(graphics, 0, 0, width, height, RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		SharpGui.fill(graphics, ACCENT_INSET, 1, width - ACCENT_INSET, 1 + ACCENT_HEIGHT, DhenPalette.accent)
		titleMemo.text(
			graphics,
			font,
			title,
			(width - titleMemo.width(font, title)) / 2,
			PADDING,
			DhenPalette.TEXT_PRIMARY
		)
		val firstLineY = PADDING + lineHeight + TITLE_GAP
		for (index in 0 until count) {
			lineMemos[index].text(
				graphics,
				font,
				lines[index],
				PADDING,
				firstLineY + index * (lineHeight + LINE_GAP),
				DhenPalette.TEXT_PRIMARY
			)
		}
	}

	override fun invalidateMeasurement() {
		titleMemo.invalidate()
		for (memo in lineMemos) memo.invalidate()
	}

	internal fun update(title: String, lines: List<String>) {
		liveTitle = title
		liveLineCount = minOf(lines.size, MAX_LINES)
		for (index in 0 until liveLineCount) liveLines[index] = lines[index]
	}

	internal fun contentAvailable(editing: Boolean): Boolean = editing || liveLineCount > 0

	internal fun shownTitle(editing: Boolean): String = if (editing) PREVIEW_TITLE else liveTitle

	internal fun shownLine(index: Int, editing: Boolean): String = shownLines(editing)[index]

	internal fun shownCount(editing: Boolean): Int = if (editing) PREVIEW_LINES.size else liveLineCount

	private fun contentWidth(font: Font, editing: Boolean): Int {
		val title = shownTitle(editing)
		val lines = shownLines(editing)
		val count = shownCount(editing)
		var width = titleMemo.width(font, title)
		for (index in 0 until count) width = maxOf(width, lineMemos[index].width(font, lines[index]))
		return width + PADDING * 2
	}

	private fun shownLines(editing: Boolean): Array<String> = if (editing) PREVIEW_LINES else liveLines

	private companion object {
		const val MAX_LINES = 40
		const val RIGHT_MARGIN = 5
		const val PADDING = 8
		const val TITLE_GAP = 4
		const val LINE_GAP = 2
		const val ACCENT_HEIGHT = 2
		const val ACCENT_INSET = 4
		const val RADIUS = 4f
		const val PREVIEW_TITLE = "SKYBLOCK"
		val PREVIEW_LINES = arrayOf(
			"§7Late Summer 1st",
			"§7⏣ §bVillage",
			"§fPurse: §612,345,678 §7(§6+1,000§7)",
			"§fBits: §b2,400"
		)
	}
}
