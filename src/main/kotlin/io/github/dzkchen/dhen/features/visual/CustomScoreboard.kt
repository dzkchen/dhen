package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SkyBlockLocation
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
	internal val hideServerIdSetting = BooleanSetting(
		"Hide Server ID",
		description = "Replaces the server code on the date line with a plain date label."
	)
	internal var hideServerId by hideServerIdSetting

	internal val element = hud(CustomScoreboardElement())

	init {
		on<ScoreboardUpdateEvent> { element.update(ScoreboardState.title, it.lines) }
	}

	override fun onEnabled() {
		element.update(ScoreboardState.title, ScoreboardState.lines)
	}

	@JvmStatic
	fun shouldHideVanilla(): Boolean = enabled
}

internal class CustomScoreboardElement : HudElement("Scoreboard", HudAnchor.MIDDLE_RIGHT, -RIGHT_MARGIN, 0) {
	private val titleMemo = DhenType.memo()
	private val lineMemos = Array(MAX_LINES) { DhenType.memo() }
	private val liveLines = Array(MAX_LINES) { "" }
	private val hiddenLines = Array(MAX_LINES) { "" }
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
		for (index in 0 until liveLineCount) {
			val line = lines[index]
			liveLines[index] = line
			hiddenLines[index] = if (index == 0) hiddenServerId(line) else line
		}
	}

	internal fun contentAvailable(editing: Boolean): Boolean = editing || liveLineCount > 0

	internal fun shownTitle(editing: Boolean): String = if (editing) PREVIEW_TITLE else liveTitle

	internal fun shownLine(index: Int, editing: Boolean, inSkyBlock: Boolean): String =
		shownLines(editing, inSkyBlock)[index]

	internal fun shownCount(editing: Boolean): Int = if (editing) PREVIEW_LINES.size else liveLineCount

	private fun contentWidth(font: Font, editing: Boolean): Int {
		val title = shownTitle(editing)
		val lines = shownLines(editing)
		val count = shownCount(editing)
		var width = titleMemo.width(font, title)
		for (index in 0 until count) width = maxOf(width, lineMemos[index].width(font, lines[index]))
		return width + PADDING * 2
	}

	private fun shownLines(editing: Boolean): Array<String> = shownLines(editing, SkyBlockLocation.inSkyBlock)

	private fun shownLines(editing: Boolean, inSkyBlock: Boolean): Array<String> = when {
		editing -> PREVIEW_LINES
		CustomScoreboard.hideServerId && inSkyBlock -> hiddenLines
		else -> liveLines
	}

	private fun hiddenServerId(line: String): String = "§7Date: ${line.substringBefore(SERVER_ID_SEPARATOR)}"

	private companion object {
		const val MAX_LINES = 15
		const val RIGHT_MARGIN = 5
		const val PADDING = 8
		const val TITLE_GAP = 4
		const val LINE_GAP = 2
		const val ACCENT_HEIGHT = 2
		const val ACCENT_INSET = 4
		const val RADIUS = 4f
		const val SERVER_ID_SEPARATOR = " §8"
		const val PREVIEW_TITLE = "SKYBLOCK"
		val PREVIEW_LINES = arrayOf(
			"§7Date: 09/01/26",
			"§eVillage",
			"§6Purse: 12,345,678",
			"§bBits: 2,400"
		)
	}
}
