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
		fill: Int,
		squared: Boolean
	) {
		val bottom = top + HEADER_HEIGHT
		RoundedGui.fill(
			graphics,
			left + HAIRLINE_INSET,
			top + HAIRLINE_INSET,
			right - HAIRLINE_INSET,
			bottom - HAIRLINE_INSET,
			COLUMN_RADIUS - HAIRLINE_INSET,
			fill
		)
		if (squared) {
			SharpGui.fill(graphics, left + HAIRLINE_INSET, bottom - COLUMN_RADIUS.toInt(), right - HAIRLINE_INSET, bottom, fill)
		}
		DhenType.text(graphics, font, title, left + CONTENT_PAD, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_PRIMARY)
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
	private var text: String? = null
	private var columnLeft = 0
	private var rowTop = 0

	fun clear() {
		text = null
	}

	fun hover(description: String, columnLeft: Int, rowTop: Int) {
		text = description
		this.columnLeft = columnLeft
		this.rowTop = rowTop
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, viewportWidth: Int, viewportHeight: Int) {
		val shown = text ?: return
		val measured = DhenType.width(font, shown) + 2 * TOOLTIP_PAD
		val boxWidth = minOf(measured, viewportWidth - 2 * MARGIN)
		val boxHeight = DhenType.lineHeight(font) + 2 * TOOLTIP_PAD
		val left = ClickGuiShell.tooltipLeft(columnLeft, COLUMN_WIDTH, boxWidth, viewportWidth, TOOLTIP_GAP, MARGIN)
		val top = ClickGuiShell.tooltipTop(rowTop, boxHeight, viewportHeight, MARGIN)
		val right = left + boxWidth
		val bottom = top + boxHeight
		val truncated = measured > boxWidth
		GlassGui.roundedFrame(graphics, left, top, right, bottom, TOOLTIP_RADIUS, GlassGui.raised(), DhenPalette.BORDER)
		if (truncated) graphics.enableScissor(left, top, right, bottom)
		DhenType.text(graphics, font, shown, left + TOOLTIP_PAD, top + TOOLTIP_PAD, DhenPalette.TEXT_SECONDARY)
		if (truncated) graphics.disableScissor()
	}
}
