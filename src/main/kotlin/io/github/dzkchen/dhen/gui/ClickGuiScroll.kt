package io.github.dzkchen.dhen.gui

internal object ClickGuiScroll {
	fun maxScroll(contentBottom: Int, viewportHeight: Int, margin: Int): Int =
		maxOf(0, contentBottom + margin - viewportHeight)

	fun clampOffset(offset: Int, maxScroll: Int): Int =
		offset.coerceIn(0, maxOf(0, maxScroll))
}
