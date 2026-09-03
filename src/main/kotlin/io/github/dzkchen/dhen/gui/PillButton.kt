package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal fun pillButton(
	graphics: GuiGraphicsExtractor,
	font: Font,
	memo: TextMemo,
	label: String,
	left: Int,
	right: Int,
	top: Int,
	height: Int,
	mouseX: Int,
	mouseY: Int,
	textPad: Int
) {
	val hovered = mouseX in left until right && mouseY in top until top + height
	RoundedGui.pill(graphics, left, top, right, top + height, GlassGui.raised(hovered))
	centeredText(graphics, font, memo, label, left, right, textTop(font, top, height), DhenPalette.TEXT_PRIMARY, textPad)
}

internal fun centeredText(
	graphics: GuiGraphicsExtractor,
	font: Font,
	memo: TextMemo,
	text: String,
	left: Int,
	right: Int,
	top: Int,
	color: Int,
	textPad: Int
) {
	val shown = memo.fit(font, text, right - left - 2 * textPad)
	memo.text(graphics, font, shown, left + (right - left - memo.width(font, shown)) / 2, top, color)
}
