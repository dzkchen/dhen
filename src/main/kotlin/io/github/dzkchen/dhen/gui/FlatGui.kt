package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor

internal object FlatGui {
	fun fill(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		color: Int
	) {
		if (left >= right || top >= bottom) return
		graphics.fill(left, top, right, bottom, color)
	}

	fun border(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		color: Int
	) {
		if (left >= right || top >= bottom) return
		fill(graphics, left, top, right, top + 1, color)
		fill(graphics, left, bottom - 1, right, bottom, color)
		fill(graphics, left, top, left + 1, bottom, color)
		fill(graphics, right - 1, top, right, bottom, color)
	}
}
