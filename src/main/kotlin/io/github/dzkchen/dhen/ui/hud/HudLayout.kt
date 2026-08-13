package io.github.dzkchen.dhen.ui.hud

import kotlin.math.roundToInt

object HudLayout {
	const val PLATE_PAD = 3
	const val PLATE_RADIUS = 4f

	fun scaled(size: Int, scale: Float): Int = (size * scale).roundToInt()

	fun platePad(scale: Float): Int = maxOf(1, scaled(PLATE_PAD, scale))

	fun plateRadius(scale: Float): Float = PLATE_RADIUS * scale

	fun place(fraction: Float, screen: Int, size: Int, offset: Int): Int =
		((screen - size) * fraction).roundToInt() + offset

	fun offsetFor(fraction: Float, screen: Int, size: Int, position: Int): Int =
		position - ((screen - size) * fraction).roundToInt()

	fun clamp(position: Int, size: Int, screen: Int): Int =
		position.coerceIn(0, maxOf(0, screen - size))

	fun placeOnScreen(fraction: Float, screen: Int, size: Int, offset: Int): Int =
		clamp(place(fraction, screen, size, offset), size, screen)
}
