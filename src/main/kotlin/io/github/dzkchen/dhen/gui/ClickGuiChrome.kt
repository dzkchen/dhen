package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import java.util.function.IntSupplier

internal fun interface TabBody {
	fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int)
}

internal class ClickGuiChrome(
	private val viewportWidth: IntSupplier,
	private val viewportHeight: IntSupplier,
	private val body: TabBody
) {
	private val openedAt = Util.getMillis()
	private val tabWidths = IntArray(TAB_LABELS.size)
	private val tabLefts = IntArray(TAB_LABELS.size)
	private val tabText = Array(TAB_LABELS.size) { DhenType.memo() }
	private val queryText = DhenType.memo()
	private val placeholderText = DhenType.memo()
	private val noMatchText = DhenType.memo()
	private var barWidth = 0
	private var previousTab = FEATURES_TAB
	private var tabSwitchedAt = 0L
	private var tabSettled = true
	private var settled = false

	var activeTab = FEATURES_TAB
		private set
	var query = ""
		private set

	val onFeatures: Boolean
		get() = activeTab == FEATURES_TAB

	val acceptsTextInput: Boolean
		get() = onFeatures

	fun measure(font: Font) {
		for (i in TAB_LABELS.indices) tabWidths[i] = DhenType.width(font, TAB_LABELS[i]) + 2 * TAB_PAD
		barWidth = 2 * BAR_PAD + ClickGuiShell.segmentsWidth(tabWidths, TAB_GAP)
		for (i in TAB_LABELS.indices) tabLefts[i] = BAR_PAD + ClickGuiShell.segmentStart(i, tabWidths, TAB_GAP)
	}

	fun invalidateMeasurements(font: Font) {
		measure(font)
		queryText.invalidate()
		placeholderText.invalidate()
		noMatchText.invalidate()
		for (i in tabText.indices) tabText[i].invalidate()
	}

	fun switchTab(tab: Int) {
		if (tab == activeTab) return
		previousTab = activeTab
		activeTab = tab
		tabSwitchedAt = Util.getMillis()
		tabSettled = false
	}

	fun tabAt(x: Int, y: Int): Int {
		if (y < TAB_TOP || y >= TAB_TOP + BAR_HEIGHT) return ClickGuiShell.NONE
		return ClickGuiShell.segmentAt(x - (barLeft() + BAR_PAD), tabWidths, TAB_GAP)
	}

	fun searchContains(x: Int, y: Int): Boolean =
		x >= searchLeft() && x < searchLeft() + SEARCH_WIDTH && y >= SEARCH_TOP && y < SEARCH_TOP + SEARCH_HEIGHT

	fun typed(codepoint: Int): Boolean {
		if (query.length >= SEARCH_MAX_LENGTH) return false
		query += codepoint.toChar()
		return true
	}

	fun backspaced(): Boolean {
		if (query.isEmpty()) return false
		query = query.substring(0, query.length - 1)
		return true
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (settled) {
			drawTabbed(graphics, font, mouseX, mouseY)
			return
		}
		val entry = GlassGui.entryProgress(openedAt)
		settled = entry >= GlassGui.SETTLED
		shifted(graphics, 0f, GlassGui.offset(entry, GlassGui.ENTRY_RISE)) { drawTabbed(graphics, font, mouseX, mouseY) }
		if (!settled) GlassGui.veil(graphics, viewportWidth.asInt, viewportHeight.asInt, entry)
	}

	fun drawSearch(graphics: GuiGraphicsExtractor, font: Font, showCaret: Boolean, noMatches: Boolean) {
		val left = searchLeft()
		val right = left + SEARCH_WIDTH
		val bottom = SEARCH_TOP + SEARCH_HEIGHT
		val outline = if (query.isEmpty()) DhenPalette.BORDER else DhenPalette.accent
		GlassGui.roundedFrame(graphics, left, SEARCH_TOP, right, bottom, RoundedQuad.FULL, GlassGui.surface(), outline)
		val textLeft = left + SEARCH_PAD
		val top = textTop(font, SEARCH_TOP, SEARCH_HEIGHT)
		val caretRoom = if (showCaret) CARET_WIDTH else 0
		val room = SEARCH_WIDTH - 2 * SEARCH_PAD - caretRoom
		if (query.isEmpty()) {
			val shown = placeholderText.fit(font, SEARCH_PLACEHOLDER, room)
			placeholderText.text(graphics, font, shown, textLeft, top, DhenPalette.TEXT_DISABLED)
			return
		}
		val shownQuery = queryText.fit(font, query, room, fromEnd = true)
		queryText.text(graphics, font, shownQuery, textLeft, top, DhenPalette.TEXT_PRIMARY)
		if (showCaret) caret(graphics, font, textLeft + queryText.width(font, shownQuery), top)
		if (noMatches) {
			val shown = noMatchText.fit(font, NO_MATCH_LABEL, viewportWidth.asInt - 2 * MARGIN)
			val labelLeft = ClickGuiShell.centeredLeft(viewportWidth.asInt, noMatchText.width(font, shown))
			noMatchText.text(graphics, font, shown, labelLeft, bottom + SEARCH_PAD, DhenPalette.TEXT_SECONDARY)
		}
	}

	private fun drawTabbed(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (tabSettled) {
			drawTabs(graphics, font, GlassGui.SETTLED)
			body.draw(graphics, mouseX, mouseY)
			return
		}
		val switch = GlassGui.tabProgress(tabSwitchedAt)
		tabSettled = switch >= GlassGui.SETTLED
		drawTabs(graphics, font, switch)
		shifted(graphics, GlassGui.offset(switch, tabTravel()), 0f) { body.draw(graphics, mouseX, mouseY) }
	}

	private fun drawTabs(graphics: GuiGraphicsExtractor, font: Font, switch: Float) {
		val barLeft = barLeft()
		val bottom = TAB_TOP + BAR_HEIGHT
		GlassGui.roundedFrame(graphics, barLeft, TAB_TOP, barLeft + barWidth, bottom, RoundedQuad.FULL, GlassGui.raised(), DhenPalette.BORDER)
		val pillLeft = barLeft + RoundedQuad.between(tabLefts[previousTab], tabLefts[activeTab], switch)
		val pillWidth = RoundedQuad.between(tabWidths[previousTab], tabWidths[activeTab], switch)
		RoundedGui.pill(graphics, pillLeft, TAB_TOP + BAR_PAD, pillLeft + pillWidth, bottom - BAR_PAD, DhenPalette.accent)
		val top = textTop(font, TAB_TOP, BAR_HEIGHT)
		for (i in TAB_LABELS.indices) {
			val color = DhenPalette.mix(tabLabelColor(i, previousTab), tabLabelColor(i, activeTab), switch)
			val shown = tabText[i].fit(font, TAB_LABELS[i], tabWidths[i] - 2 * TAB_PAD)
			tabText[i].text(graphics, font, shown, barLeft + tabLefts[i] + TAB_PAD, top, color)
		}
	}

	private fun tabLabelColor(tab: Int, active: Int): Int =
		if (tab == active) DhenPalette.accentForeground else DhenPalette.TEXT_SECONDARY

	private fun tabTravel(): Float =
		if (activeTab > previousTab) GlassGui.TAB_SLIDE else -GlassGui.TAB_SLIDE

	private fun barLeft(): Int = ClickGuiShell.centeredLeft(viewportWidth.asInt, barWidth)

	private fun searchLeft(): Int = ClickGuiShell.centeredLeft(viewportWidth.asInt, SEARCH_WIDTH)

	private inline fun shifted(graphics: GuiGraphicsExtractor, dx: Float, dy: Float, content: () -> Unit) {
		if (dx == 0f && dy == 0f) {
			content()
			return
		}
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(dx, dy)
		content()
		pose.popMatrix()
	}
}
