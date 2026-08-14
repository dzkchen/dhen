package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudTextElement(
	name: String,
	var text: String = name,
	var color: Int? = null,
	anchor: HudAnchor = HudAnchor.TOP_LEFT,
	offsetX: Int = 0,
	offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	visible: Boolean = true,
	background: Boolean = false
) : HudElement(name, anchor, offsetX, offsetY, scale, visible, background) {
	private val memo = DhenType.memo()

	override fun width(font: Font): Int = memo.width(font, text)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val ink = color ?: if (background) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_ON_WORLD
		memo.shadowed(graphics, font, text, 0, 0, ink, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()
}
