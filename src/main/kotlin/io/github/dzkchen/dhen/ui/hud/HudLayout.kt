package io.github.dzkchen.dhen.ui.hud

import kotlin.math.roundToInt

object HudLayout {
	fun scaled(size: Int, scale: Float): Int = (size * scale).roundToInt()

	fun place(fraction: Float, screen: Int, size: Int, offset: Int): Int =
		((screen - size) * fraction).roundToInt() + offset

	fun clamp(position: Int, size: Int, screen: Int): Int =
		position.coerceIn(0, maxOf(0, screen - size))
}
