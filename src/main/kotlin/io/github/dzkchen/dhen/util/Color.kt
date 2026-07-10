package io.github.dzkchen.dhen.util

@JvmInline
value class Color(val argb: Int) {

	val alpha: Int get() = argb ushr 24 and 0xFF
	val red: Int get() = argb ushr 16 and 0xFF
	val green: Int get() = argb ushr 8 and 0xFF
	val blue: Int get() = argb and 0xFF

	fun opaque(): Color = Color(argb or (0xFF shl 24))

	companion object {
		fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 0xFF): Color =
			Color((alpha and 0xFF shl 24) or (red and 0xFF shl 16) or (green and 0xFF shl 8) or (blue and 0xFF))
	}
}
