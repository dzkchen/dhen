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
	private val nameMemo = DhenType.memo()
	private val textMemo = DhenType.memo()
	private var name = ""
	private var text: String? = null
	private var hostLeft = 0
	private var hostWidth = 0
	private var rowTop = 0

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
		nameMemo.invalidate()
		textMemo.invalidate()
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, viewportWidth: Int, viewportHeight: Int) {
		val description = text ?: return
		val lead = name
		val rows = (if (lead.isEmpty()) 0 else 1) + (if (description.isEmpty()) 0 else 1)
		if (rows == 0) return
		val widest = maxOf(
			if (lead.isEmpty()) 0 else nameMemo.width(font, lead),
			if (description.isEmpty()) 0 else textMemo.width(font, description)
		)
		val boxWidth = minOf(widest + 2 * TOOLTIP_PAD, viewportWidth - 2 * MARGIN)
		val lineHeight = DhenType.lineHeight(font)
		val boxHeight = rows * lineHeight + 2 * TOOLTIP_PAD
		val left = ClickGuiShell.tooltipLeft(hostLeft, hostWidth, boxWidth, viewportWidth, TOOLTIP_GAP, MARGIN)
		val top = ClickGuiShell.tooltipTop(rowTop, boxHeight, viewportHeight, MARGIN)
		val room = boxWidth - 2 * TOOLTIP_PAD
		GlassGui.roundedFrame(graphics, left, top, left + boxWidth, top + boxHeight, TOOLTIP_RADIUS, GlassGui.raised(), DhenPalette.BORDER)
		var lineTop = top + TOOLTIP_PAD
		if (lead.isNotEmpty()) {
			val fittedName = nameMemo.fit(font, lead, room)
			nameMemo.text(graphics, font, fittedName, left + TOOLTIP_PAD, lineTop, DhenPalette.TEXT_PRIMARY)
			lineTop += lineHeight
		}
		if (description.isEmpty()) return
		val fitted = textMemo.fit(font, description, room)
		textMemo.text(graphics, font, fitted, left + TOOLTIP_PAD, lineTop, DhenPalette.TEXT_SECONDARY)
	}
}

internal fun headerTitleRoom(width: Int, reservedWidth: Int): Int {
	val reserve = if (reservedWidth > 0) LABEL_GAP + reservedWidth else 0
	return maxOf(width - 2 * CONTENT_PAD - reserve, 0)
}
