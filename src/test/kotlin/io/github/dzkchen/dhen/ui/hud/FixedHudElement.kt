package io.github.dzkchen.dhen.ui.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class FixedHudElement(
	name: String,
	private val fixedWidth: Int = 20,
	private val fixedHeight: Int = 9,
	anchor: HudAnchor = HudAnchor.TOP_LEFT,
	offsetX: Int = 0,
	offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	visible: Boolean = true,
	background: Boolean = false
) : HudElement(name, anchor, offsetX, offsetY, scale, visible, background) {
	override fun width(font: Font): Int = fixedWidth

	override fun height(font: Font): Int = fixedHeight

	override fun render(graphics: GuiGraphicsExtractor, font: Font) = Unit
}
