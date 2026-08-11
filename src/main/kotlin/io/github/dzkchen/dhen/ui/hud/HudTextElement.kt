package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.FlatGui
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudTextElement(
	name: String,
	text: String = name,
	var color: Int = DhenPalette.TEXT_PRIMARY,
	anchor: HudAnchor = HudAnchor.TOP_LEFT,
	offsetX: Int = 0,
	offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	visible: Boolean = true
) : HudElement(name, anchor, offsetX, offsetY, scale, visible) {
	private var measuredWidth = UNMEASURED

	var text: String = text
		set(value) {
			if (field == value) return
			field = value
			measuredWidth = UNMEASURED
		}

	override fun width(font: Font): Int {
		if (measuredWidth == UNMEASURED) measuredWidth = font.width(text)
		return measuredWidth
	}

	override fun height(font: Font): Int = font.lineHeight

	override fun invalidateMeasurement() {
		measuredWidth = UNMEASURED
	}

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		FlatGui.text(graphics, font, text, 0, 0, color, shadow = true)
	}

	private companion object {
		const val UNMEASURED = -1
	}
}
