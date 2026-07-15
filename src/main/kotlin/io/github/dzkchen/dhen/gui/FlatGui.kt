package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
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

	fun roundedFill(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Int,
		color: Int
	) {
		val width = right - left
		val height = bottom - top
		if (width <= 0 || height <= 0) return

		val actualRadius = minOf(radius.coerceAtLeast(0), (width - 1) / 2, (height - 1) / 2)
		if (actualRadius == 0) {
			graphics.fill(left, top, right, bottom, color)
			return
		}

		for (row in 0 until actualRadius) {
			val inset = roundedInset(actualRadius, row)
			graphics.fill(left + inset, top + row, right - inset, top + row + 1, color)
			graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color)
		}
		graphics.fill(left, top + actualRadius, right, bottom - actualRadius, color)
	}

	fun text(
		graphics: GuiGraphicsExtractor,
		font: Font,
		text: String,
		x: Int,
		y: Int,
		color: Int
	) {
		graphics.text(font, text, x, y, color, false)
	}

	internal fun roundedInset(radius: Int, row: Int): Int {
		if (radius <= 0 || row < 0 || row >= radius) return 0

		val y = radius - row
		val radiusSquared = radius * radius
		var inset = 0
		while (true) {
			val x = radius - inset
			if (x * x + y * y <= radiusSquared) return inset
			inset++
		}
	}
}
