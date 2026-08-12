package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudTextElement(
	name: String,
	var text: String = name,
	var color: Int = DhenPalette.TEXT_PRIMARY,
	anchor: HudAnchor = HudAnchor.TOP_LEFT,
	offsetX: Int = 0,
	offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	visible: Boolean = true
) : HudElement(name, anchor, offsetX, offsetY, scale, visible) {
	override fun width(font: Font): Int = DhenType.width(font, text)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		DhenType.text(graphics, font, text, 0, 0, color, shadow = true)
	}
}
