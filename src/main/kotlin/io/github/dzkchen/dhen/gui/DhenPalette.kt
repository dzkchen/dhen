package io.github.dzkchen.dhen.gui

import kotlin.math.roundToInt

internal object DhenPalette {
	val DEFAULT_ACCENT: Int = 0xFFF5A9C6u.toInt()

	private const val MUTED_BLEND = 0.55f
	private const val CONTRAST_PIVOT = 140
	private val ALPHA_MASK = 0xFF000000u.toInt()

	val CANVAS = 0xFF08080Au.toInt()
	val SURFACE = 0xFF0D0D10u.toInt()
	val SURFACE_RAISED = 0xFF141418u.toInt()
	val SURFACE_INTERACTIVE = 0xFF1D1D23u.toInt()
	val BORDER = 0xFF2B2B33u.toInt()

	val TEXT_PRIMARY = 0xFFF6F4F6u.toInt()
	val TEXT_SECONDARY = 0xFFA9A6AEu.toInt()
	val TEXT_DISABLED = 0xFF6B6872u.toInt()
	val TEXT_ON_ACCENT = 0xFF17070Eu.toInt()

	val GLASS_CANVAS = 0xA608080Au.toInt()
	val GLASS_SURFACE = 0xC20D0D10u.toInt()
	val GLASS_SURFACE_RAISED = 0xD4141418u.toInt()
	val GLASS_SURFACE_INTERACTIVE = 0xE01D1D23u.toInt()
	val GLASS_SCRIM = 0x8C08080Au.toInt()
	val GLASS_SHADOW = 0x73000000u.toInt()
	val GLASS_SHEEN = 0x24FFFFFFu.toInt()
	val GLASS_VEIL = 0xE608080Au.toInt()

	var accent: Int = DEFAULT_ACCENT
		set(value) {
			field = value
			accentMuted = muted(value)
			textOnAccent = contrasting(value)
		}

	var accentMuted: Int = muted(DEFAULT_ACCENT)
		private set

	var textOnAccent: Int = contrasting(DEFAULT_ACCENT)
		private set

	fun label(highlighted: Boolean): Int = if (highlighted) TEXT_PRIMARY else TEXT_SECONDARY

	fun luminance(color: Int): Int {
		val red = color ushr 16 and 0xFF
		val green = color ushr 8 and 0xFF
		val blue = color and 0xFF
		return (red * 299 + green * 587 + blue * 114) / 1000
	}

	private fun contrasting(color: Int): Int = if (luminance(color) >= CONTRAST_PIVOT) TEXT_ON_ACCENT else TEXT_PRIMARY

	private fun muted(color: Int): Int {
		val red = towardsSurface(color ushr 16 and 0xFF, SURFACE ushr 16 and 0xFF)
		val green = towardsSurface(color ushr 8 and 0xFF, SURFACE ushr 8 and 0xFF)
		val blue = towardsSurface(color and 0xFF, SURFACE and 0xFF)
		return (color and ALPHA_MASK) or (red shl 16) or (green shl 8) or blue
	}

	private fun towardsSurface(channel: Int, surface: Int): Int =
		(channel + (surface - channel) * MUTED_BLEND).roundToInt().coerceIn(0, 0xFF)
}
