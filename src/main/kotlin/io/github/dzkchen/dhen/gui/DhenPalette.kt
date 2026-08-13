package io.github.dzkchen.dhen.gui

import kotlin.math.abs
import kotlin.math.roundToInt

internal object DhenPalette {
	val DEFAULT_ACCENT: Int get() = DhenTheme.DEFAULT.accent

	val CANVAS: Int get() = DhenTheme.active.canvas
	val SURFACE: Int get() = DhenTheme.active.surface
	val SURFACE_RAISED: Int get() = DhenTheme.active.surfaceRaised
	val SURFACE_INTERACTIVE: Int get() = DhenTheme.active.surfaceInteractive
	val BORDER: Int get() = DhenTheme.active.border

	val TEXT_PRIMARY: Int get() = DhenTheme.active.textPrimary
	val TEXT_SECONDARY: Int get() = DhenTheme.active.textSecondary
	val TEXT_DISABLED: Int get() = DhenTheme.active.textDisabled
	val TEXT_ON_ACCENT: Int get() = DhenTheme.active.textOnAccent
	val TEXT_ON_WORLD: Int get() = DhenTheme.active.textOnWorld

	val SPLASH_CANVAS: Int get() = DhenTheme.active.splashCanvas
	val SPLASH_TRACK: Int get() = DhenTheme.active.splashTrack
	val SPLASH_INK: Int get() = DhenTheme.active.splashInk

	val GLASS_CANVAS: Int get() = DhenTheme.active.glassCanvas
	val GLASS_SURFACE: Int get() = DhenTheme.active.glassSurface
	val GLASS_SURFACE_RAISED: Int get() = DhenTheme.active.glassSurfaceRaised
	val GLASS_SURFACE_INTERACTIVE: Int get() = DhenTheme.active.glassSurfaceInteractive
	val GLASS_SCRIM: Int get() = DhenTheme.active.glassScrim
	val GLASS_SHADOW: Int get() = DhenTheme.active.glassShadow
	val GLASS_SHEEN: Int get() = DhenTheme.active.glassSheen
	val GLASS_VEIL: Int get() = DhenTheme.active.glassVeil

	val accent: Int get() = DhenTheme.active.accent
	val accentMuted: Int get() = DhenTheme.active.accentMuted
	val accentForeground: Int get() = DhenTheme.active.accentForeground

	fun label(highlighted: Boolean): Int = if (highlighted) TEXT_PRIMARY else TEXT_SECONDARY

	fun mix(from: Int, to: Int, fraction: Float): Int {
		if (from == to || fraction >= 1f) return to
		if (fraction <= 0f) return from
		return (channelBetween(from, to, 24, fraction) shl 24) or
			(channelBetween(from, to, 16, fraction) shl 16) or
			(channelBetween(from, to, 8, fraction) shl 8) or
			channelBetween(from, to, 0, fraction)
	}

	private fun channelBetween(from: Int, to: Int, shift: Int, fraction: Float): Int =
		blend(from ushr shift and 0xFF, to ushr shift and 0xFF, fraction)

	private fun blend(from: Int, to: Int, fraction: Float): Int =
		(from + (to - from) * fraction).roundToInt().coerceIn(0, 0xFF)

	fun luminance(color: Int): Int {
		val red = color ushr 16 and 0xFF
		val green = color ushr 8 and 0xFF
		val blue = color and 0xFF
		return (red * 299 + green * 587 + blue * 114) / 1000
	}

	fun contrast(from: Int, to: Int): Int = abs(luminance(from) - luminance(to))
}
