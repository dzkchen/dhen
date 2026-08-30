package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object ClickGuiPaint {
	fun headerBand(
		graphics: GuiGraphicsExtractor,
		font: Font,
		left: Int,
		right: Int,
		top: Int,
		title: String,
		titleText: TextMemo,
		reservedWidth: Int,
		fill: Int,
		squared: Boolean
	) {
		val bottom = top + HEADER_HEIGHT
		val curveBottom = if (squared) bottom + COLUMN_RADIUS.toInt() else bottom - HAIRLINE_INSET
		if (squared) graphics.enableScissor(left, top, right, bottom)
		RoundedGui.fill(
			graphics,
			left + HAIRLINE_INSET,
			top + HAIRLINE_INSET,
			right - HAIRLINE_INSET,
			curveBottom,
			COLUMN_RADIUS - HAIRLINE_INSET,
			fill
		)
		if (squared) graphics.disableScissor()
		val shown = titleText.fit(font, title, headerTitleRoom(right - left, reservedWidth))
		titleText.text(graphics, font, shown, left + CONTENT_PAD, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_PRIMARY)
	}

	fun headerRule(graphics: GuiGraphicsExtractor, left: Int, right: Int, bottom: Int) {
		SharpGui.fill(graphics, left + HEADER_RULE_INSET, bottom - 1, right - HEADER_RULE_INSET, bottom, DhenPalette.accent)
	}

	fun stripScrollbar(
		graphics: GuiGraphicsExtractor,
		viewportWidth: Int,
		viewportHeight: Int,
		offset: Int,
		max: Int
	) {
		val trackWidth = chromeRoom(viewportWidth)
		if (trackWidth <= 0 || max <= ClickGuiScroll.TOP) return
		val top = stripBarTop(viewportHeight)
		val bottom = top + STRIP_BAR_HEIGHT
		RoundedGui.pill(graphics, MARGIN, top, MARGIN + trackWidth, bottom, DhenPalette.BORDER)
		val thumbLeft = stripThumbLeft(viewportWidth, offset, max)
		RoundedGui.pill(graphics, thumbLeft, top, thumbLeft + stripThumbWidth(viewportWidth, max), bottom, DhenPalette.accent)
	}

	fun scrollbar(
		graphics: GuiGraphicsExtractor,
		right: Int,
		areaTop: Int,
		areaHeight: Int,
		offset: Int,
		max: Int
	) {
		val trackTop = areaTop + SCROLLBAR_INSET
		val trackHeight = areaHeight - 2 * SCROLLBAR_INSET
		if (trackHeight <= 0) return
		val thumbHeight = ClickGuiScroll.thumbHeight(trackHeight, areaHeight, max, SCROLLBAR_MIN_THUMB)
		val thumbTop = ClickGuiScroll.thumbTop(trackTop, trackHeight, thumbHeight, offset, max)
		val thumbRight = right - SCROLLBAR_INSET
		RoundedGui.pill(graphics, thumbRight - SCROLLBAR_WIDTH, thumbTop, thumbRight, thumbTop + thumbHeight, DhenPalette.accentMuted)
	}
}

internal class ClickGuiTooltip {
	private val nameWrap = DhenType.wrap()
	private val bodyWrap = DhenType.wrap()
	private var name = ""
	private var text: String? = null
	private var hostLeft = 0
	private var hostWidth = 0
	private var rowTop = 0
	private var nameLines = 0
	private var bodyLines = 0

	var left = 0
		private set
	var top = 0
		private set
	var boxWidth = 0
		private set
	var boxHeight = 0
		private set

	fun clear() {
		text = null
	}

	fun hover(name: String, description: String, hostLeft: Int, hostWidth: Int, rowTop: Int) {
		this.name = name
		text = description
		this.hostLeft = hostLeft
		this.hostWidth = hostWidth
		this.rowTop = rowTop
	}

	fun invalidateMeasurement() {
		nameWrap.invalidate()
		bodyWrap.invalidate()
	}

	fun measure(font: Font, viewportWidth: Int, viewportHeight: Int): Boolean {
		val description = text ?: return false
		if (name.isEmpty() && description.isEmpty()) return false
		val lineHeight = DhenType.lineHeight(font)
		val room = maxOf(chromeWidth(TOOLTIP_MAX_WIDTH, viewportWidth) - 2 * TOOLTIP_PAD, 0)
		val budget = lineBudget(viewportHeight, lineHeight)
		nameLines = if (name.isEmpty()) 0 else {
			nameWrap.measure(font, name, room, minOf(TOOLTIP_NAME_LINES, budget))
			nameWrap.lines
		}
		val rest = budget - nameLines
		bodyLines = if (description.isEmpty() || rest <= 0) 0 else {
			bodyWrap.measure(font, description, room, minOf(TOOLTIP_BODY_LINES, rest))
			bodyWrap.lines
		}
		boxWidth = widest(font) + 2 * TOOLTIP_PAD
		boxHeight = (nameLines + bodyLines) * lineHeight + 2 * TOOLTIP_PAD
		left = ClickGuiShell.tooltipLeft(hostLeft, hostWidth, boxWidth, viewportWidth, TOOLTIP_GAP, MARGIN)
		top = ClickGuiShell.tooltipTop(rowTop, boxHeight, viewportHeight, MARGIN)
		return true
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, viewportWidth: Int, viewportHeight: Int) {
		if (!measure(font, viewportWidth, viewportHeight)) return
		GlassGui.roundedFrame(graphics, left, top, left + boxWidth, top + boxHeight, TOOLTIP_RADIUS, GlassGui.raised(), DhenPalette.BORDER)
		val textLeft = left + TOOLTIP_PAD
		var lineTop = top + TOOLTIP_PAD
		if (nameLines > 0) {
			nameWrap.draw(graphics, font, textLeft, lineTop, DhenPalette.TEXT_PRIMARY)
			lineTop += nameLines * DhenType.lineHeight(font)
		}
		if (bodyLines > 0) bodyWrap.draw(graphics, font, textLeft, lineTop, DhenPalette.TEXT_SECONDARY)
	}

	private fun lineBudget(viewportHeight: Int, lineHeight: Int): Int =
		if (lineHeight <= 0) TOOLTIP_NAME_LINES + TOOLTIP_BODY_LINES
		else maxOf((viewportHeight - 2 * MARGIN - 2 * TOOLTIP_PAD) / lineHeight, 1)

	private fun widest(font: Font): Int = maxOf(
		if (nameLines == 0) 0 else nameWrap.widest(font),
		if (bodyLines == 0) 0 else bodyWrap.widest(font)
	)
}

internal fun headerTitleRoom(width: Int, reservedWidth: Int): Int {
	val reserve = if (reservedWidth > 0) LABEL_GAP + reservedWidth else 0
	return maxOf(width - 2 * CONTENT_PAD - reserve, 0)
}

internal fun stripBarTop(viewportHeight: Int): Int = fieldBottomOf(viewportHeight) + (MARGIN - STRIP_BAR_HEIGHT) / 2

internal fun stripThumbWidth(viewportWidth: Int, max: Int): Int =
	ClickGuiScroll.thumbHeight(chromeRoom(viewportWidth), viewportWidth, max, SCROLLBAR_MIN_THUMB)

internal fun stripThumbLeft(viewportWidth: Int, offset: Int, max: Int): Int =
	ClickGuiScroll.thumbTop(MARGIN, chromeRoom(viewportWidth), stripThumbWidth(viewportWidth, max), offset, max)
