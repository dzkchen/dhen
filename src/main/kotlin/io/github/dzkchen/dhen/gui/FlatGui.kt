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
}
