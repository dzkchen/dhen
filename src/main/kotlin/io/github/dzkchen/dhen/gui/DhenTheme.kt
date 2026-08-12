package io.github.dzkchen.dhen.gui

internal data class DhenTheme(
	val canvas: Int = 0xFF08080Au.toInt(),
	val surface: Int = 0xFF0D0D10u.toInt(),
	val surfaceRaised: Int = 0xFF141418u.toInt(),
	val surfaceInteractive: Int = 0xFF1D1D23u.toInt(),
	val border: Int = 0xFF2B2B33u.toInt(),
	val textPrimary: Int = 0xFFF6F4F6u.toInt(),
	val textSecondary: Int = 0xFFA9A6AEu.toInt(),
	val textDisabled: Int = 0xFF6B6872u.toInt(),
	val textOnAccent: Int = 0xFF17070Eu.toInt(),
	val splashCanvas: Int = 0xFFF8D7E3u.toInt(),
	val splashTrack: Int = 0xFFEBB4CBu.toInt(),
	val splashInk: Int = 0xFF2A0E18u.toInt(),
	val glassCanvas: Int = 0xA608080Au.toInt(),
	val glassSurface: Int = 0xC20D0D10u.toInt(),
	val glassSurfaceRaised: Int = 0xD4141418u.toInt(),
	val glassSurfaceInteractive: Int = 0xE01D1D23u.toInt(),
	val glassScrim: Int = 0x8C08080Au.toInt(),
	val glassShadow: Int = 0x73000000u.toInt(),
	val glassSheen: Int = 0x24FFFFFFu.toInt(),
	val glassVeil: Int = 0xE608080Au.toInt(),
	val accent: Int = 0xFFF5A9C6u.toInt(),
	val entryMillis: Long = 200L,
	val entryRise: Float = 10f,
	val tabMillis: Long = 150L,
	val tabSlide: Float = 14f,
	val toggleMillis: Long = 100L
) {
	val accentMuted: Int =
		((accent ushr ALPHA_SHIFT) shl ALPHA_SHIFT) or (DhenPalette.mix(accent, surface, MUTED_BLEND) and RGB_MASK)

	val accentForeground: Int =
		if (DhenPalette.luminance(accent) >= CONTRAST_PIVOT) textOnAccent else textPrimary

	fun withAccent(color: Int): DhenTheme = if (color == accent) this else copy(accent = color)

	companion object {
		private const val MUTED_BLEND = 0.55f
		private const val CONTRAST_PIVOT = 140
		private const val RGB_MASK = 0xFFFFFF
		private const val ALPHA_SHIFT = 24

		val DEFAULT = DhenTheme()

		var active: DhenTheme = DEFAULT
	}
}
