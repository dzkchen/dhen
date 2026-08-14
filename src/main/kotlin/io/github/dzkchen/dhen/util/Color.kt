package io.github.dzkchen.dhen.util

import kotlin.math.roundToInt

@JvmInline
value class Color(val argb: Int) {

	val alpha: Int get() = argb ushr 24 and 0xFF
	val red: Int get() = argb ushr 16 and 0xFF
	val green: Int get() = argb ushr 8 and 0xFF
	val blue: Int get() = argb and 0xFF
	val rgb: Int get() = argb and 0xFFFFFF

	val brightness: Float get() = peak / CHANNEL_MAX

	val saturation: Float
		get() {
			val top = peak
			return if (top == 0f) 0f else (top - trough) / top
		}

	val hue: Float
		get() {
			val top = peak
			val spread = top - trough
			if (spread == 0f) return 0f
			val sector = when (top) {
				red.toFloat() -> (green - blue) / spread + if (green < blue) SECTORS else 0f
				green.toFloat() -> (blue - red) / spread + 2f
				else -> (red - green) / spread + 4f
			}
			return sector / SECTORS
		}

	fun opaque(): Color = Color(argb or (0xFF shl 24))

	private val peak: Float get() = maxOf(red, green, blue).toFloat()

	private val trough: Float get() = minOf(red, green, blue).toFloat()

	companion object {
		private const val CHANNEL_MAX = 255f
		private const val SECTORS = 6f

		fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 0xFF): Color =
			Color((alpha and 0xFF shl 24) or (red and 0xFF shl 16) or (green and 0xFF shl 8) or (blue and 0xFF))

		fun hsv(hue: Float, saturation: Float, brightness: Float, alpha: Int = 0xFF): Color {
			val top = brightness.coerceIn(0f, 1f)
			val spread = top * saturation.coerceIn(0f, 1f)
			val sector = wrapped(hue) * SECTORS
			val index = sector.toInt().coerceIn(0, SECTORS.toInt() - 1)
			val rise = spread * (sector - index)
			val bottom = top - spread
			return when (index) {
				0 -> rgba(channel(top), channel(bottom + rise), channel(bottom), alpha)
				1 -> rgba(channel(top - rise), channel(top), channel(bottom), alpha)
				2 -> rgba(channel(bottom), channel(top), channel(bottom + rise), alpha)
				3 -> rgba(channel(bottom), channel(top - rise), channel(top), alpha)
				4 -> rgba(channel(bottom + rise), channel(bottom), channel(top), alpha)
				else -> rgba(channel(top), channel(bottom), channel(top - rise), alpha)
			}
		}

		private fun wrapped(hue: Float): Float {
			val fraction = hue % 1f
			return if (fraction < 0f) fraction + 1f else fraction
		}

		private fun channel(level: Float): Int = (level * CHANNEL_MAX).roundToInt().coerceIn(0, 0xFF)
	}
}
