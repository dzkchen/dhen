package io.github.dzkchen.dhen.gui

import kotlin.math.abs
import kotlin.math.roundToInt

internal object DhenPalette {
	val DEFAULT_ACCENT: Int get() = DhenTheme.DEFAULT.accent

	val CANVAS: Int get() = DhenTheme.activeOnRenderThread.canvas
	val SURFACE: Int get() = DhenTheme.activeOnRenderThread.surface
	val SURFACE_RAISED: Int get() = DhenTheme.activeOnRenderThread.surfaceRaised
	val SURFACE_INTERACTIVE: Int get() = DhenTheme.activeOnRenderThread.surfaceInteractive
	val BORDER: Int get() = DhenTheme.activeOnRenderThread.border

	val TEXT_PRIMARY: Int get() = DhenTheme.activeOnRenderThread.textPrimary
	val TEXT_SECONDARY: Int get() = DhenTheme.activeOnRenderThread.textSecondary
	val TEXT_DISABLED: Int get() = DhenTheme.activeOnRenderThread.textDisabled
	val TEXT_ON_ACCENT: Int get() = DhenTheme.activeOnRenderThread.textOnAccent
	val TEXT_ON_WORLD: Int get() = DhenTheme.activeOnRenderThread.textOnWorld

	val SPLASH_CANVAS: Int get() = DhenTheme.activeOnRenderThread.splashCanvas
	val SPLASH_TRACK: Int get() = DhenTheme.activeOnRenderThread.splashTrack
	val SPLASH_INK: Int get() = DhenTheme.activeOnRenderThread.splashInk

	val GLASS_CANVAS: Int get() = DhenTheme.activeOnRenderThread.glassCanvas
	val GLASS_SURFACE: Int get() = DhenTheme.activeOnRenderThread.glassSurface
	val GLASS_SURFACE_RAISED: Int get() = DhenTheme.activeOnRenderThread.glassSurfaceRaised
	val GLASS_SURFACE_INTERACTIVE: Int get() = DhenTheme.activeOnRenderThread.glassSurfaceInteractive
	val GLASS_SCRIM: Int get() = DhenTheme.activeOnRenderThread.glassScrim
	val GLASS_SHADOW: Int get() = DhenTheme.activeOnRenderThread.glassShadow
	val GLASS_SHEEN: Int get() = DhenTheme.activeOnRenderThread.glassSheen
	val GLASS_VEIL: Int get() = DhenTheme.activeOnRenderThread.glassVeil

	val accent: Int get() = DhenTheme.activeOnRenderThread.accent
	val accentMuted: Int get() = DhenTheme.activeOnRenderThread.accentMuted
	val accentForeground: Int get() = DhenTheme.activeOnRenderThread.accentForeground

	val HYPIXEL_COMMON: Int get() = DhenTheme.HYPIXEL_COMMON
	val HYPIXEL_UNCOMMON: Int get() = DhenTheme.HYPIXEL_UNCOMMON
	val HYPIXEL_RARE: Int get() = DhenTheme.HYPIXEL_RARE
	val HYPIXEL_EPIC: Int get() = DhenTheme.HYPIXEL_EPIC
	val HYPIXEL_LEGENDARY: Int get() = DhenTheme.HYPIXEL_LEGENDARY
	val HYPIXEL_MYTHIC: Int get() = DhenTheme.HYPIXEL_MYTHIC
	val HYPIXEL_DIVINE: Int get() = DhenTheme.HYPIXEL_DIVINE
	val HYPIXEL_ULTIMATE: Int get() = DhenTheme.HYPIXEL_ULTIMATE
	val HYPIXEL_SPECIAL: Int get() = DhenTheme.HYPIXEL_SPECIAL

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
