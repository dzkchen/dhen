package io.github.dzkchen.dhen.render

internal object BoxStyle {
	const val OUTLINE = "Outline"
	const val FILL = "Fill"
	const val FILLED_OUTLINE = "Filled Outline"

	val options = listOf(OUTLINE, FILL, FILLED_OUTLINE)

	fun outlines(style: String): Boolean = style != FILL

	fun fills(style: String): Boolean = style != OUTLINE
}
