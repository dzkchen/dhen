package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.font.FontStore

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
	val textOnWorld: Int = 0xFFF6F4F6u.toInt(),
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
	val font: String = FontStore.INTER,
	val entryMillis: Long = 200L,
	val entryRise: Float = 10f,
	val tabMillis: Long = 150L,
	val tabSlide: Float = 14f,
	val toggleMillis: Long = 100L
) {
	val accentMuted: Int =
		((accent ushr ALPHA_SHIFT) shl ALPHA_SHIFT) or (DhenPalette.mix(accent, surface, MUTED_BLEND) and RGB_MASK)

	val accentForeground: Int =
		if (DhenPalette.contrast(accent, textOnAccent) >= DhenPalette.contrast(accent, textPrimary)) textOnAccent else textPrimary

	fun resolved(accent: Int, font: String): DhenTheme =
		if (accent == this.accent && font == this.font) this else copy(accent = accent, font = font)

	companion object {
		private const val MUTED_BLEND = 0.55f
		private const val RGB_MASK = 0xFFFFFF
		private const val ALPHA_SHIFT = 24

		val HYPIXEL_COMMON = 0xFFFFFFFFu.toInt()
		val HYPIXEL_UNCOMMON = 0xFF21FF2Au.toInt()
		val HYPIXEL_RARE = 0xFF459BFFu.toInt()
		val HYPIXEL_EPIC = 0xFFA335EEu.toInt()
		val HYPIXEL_LEGENDARY = 0xFFFFA216u.toInt()
		val HYPIXEL_MYTHIC = 0xFFFF55FFu.toInt()
		val HYPIXEL_DIVINE = 0xFF55FFFFu.toInt()
		val HYPIXEL_ULTIMATE = 0xFFD13228u.toInt()
		val HYPIXEL_SPECIAL = 0xFFFF5555u.toInt()

		val SLOT_CREAM = 0xFFFFDDC1u.toInt()
		val SLOT_GOLD = 0xFFE5B80Bu.toInt()
		val SLOT_BLUE = 0xFF74C7ECu.toInt()
		val SLOT_RED = 0xFFFF7276u.toInt()
		val SLOT_GREEN = 0xFF90EE90u.toInt()
		val SLOT_ORANGE = 0xFFFAB387u.toInt()
		val SLOT_YELLOW = 0xFFF9E2AFu.toInt()
		val SLOT_PURPLE = 0xFFCBA6F7u.toInt()
		val SLOT_CLAIM = 0xFFA6E3A1u.toInt()
		val SLOT_CHIP_TEN = 0xFF1D8DE8u.toInt()
		val SLOT_CHIP_FIFTEEN = 0xFFA40BE2u.toInt()
		val SLOT_TOOLKIT = 0xFFFF5555u.toInt()
		val SLOT_ENCHANT = 0xFF5555FFu.toInt()
		val SLOT_ULTIMATE = 0xFFFF55FFu.toInt()

		val SLOT_STAR_DUNGEON = intArrayOf(
			0xFFFFFFE0u.toInt(),
			0xFFFFFFE0u.toInt(),
			0xFFFFFF00u.toInt(),
			0xFFFFA500u.toInt(),
			0xFFFF8C00u.toInt(),
			0xFFFF4500u.toInt(),
			0xFFFF4500u.toInt(),
			0xFFFF0000u.toInt(),
			0xFFCD5C5Cu.toInt(),
			0xFF8B0000u.toInt()
		)

		val SLOT_STAR_NORMAL = intArrayOf(
			0xFFFFFFE0u.toInt(),
			0xFFFFFFE0u.toInt(),
			0xFFFFFF00u.toInt(),
			0xFFFFA500u.toInt(),
			0xFFFF8C00u.toInt(),
			0xFFFFB6C1u.toInt(),
			0xFFFFB6C1u.toInt(),
			0xFFFF69B4u.toInt(),
			0xFFFF1493u.toInt(),
			0xFFFFC0CBu.toInt(),
			0xFFE0FFFFu.toInt(),
			0xFF00FFFFu.toInt(),
			0xFF87CEFAu.toInt(),
			0xFF6495EDu.toInt(),
			0xFF4682B4u.toInt()
		)

		val DEFAULT = DhenTheme()

		val LIGHT = DhenTheme(
			canvas = 0xFFE9EAEFu.toInt(),
			surface = 0xFFF4F5F8u.toInt(),
			surfaceRaised = 0xFFFBFBFDu.toInt(),
			surfaceInteractive = 0xFFFFFFFFu.toInt(),
			border = 0xFFC9CCD8u.toInt(),
			textPrimary = 0xFF15151Au.toInt(),
			textSecondary = 0xFF5A5865u.toInt(),
			textDisabled = 0xFF9C99A6u.toInt(),
			textOnAccent = 0xFFFFF2F7u.toInt(),
			textOnWorld = 0xFFF7EEF3u.toInt(),
			splashCanvas = 0xFFFDF3F7u.toInt(),
			splashTrack = 0xFFF0CBDCu.toInt(),
			splashInk = 0xFF5A1233u.toInt(),
			glassCanvas = 0xA6E9EAEFu.toInt(),
			glassSurface = 0xC2F4F5F8u.toInt(),
			glassSurfaceRaised = 0xD4FBFBFDu.toInt(),
			glassSurfaceInteractive = 0xE0FFFFFFu.toInt(),
			glassScrim = 0x8CE9EAEFu.toInt(),
			glassShadow = 0x33202430u.toInt(),
			glassSheen = 0x1F1A2030u.toInt(),
			glassVeil = 0xE6E9EAEFu.toInt(),
			accent = 0xFFC63F79u.toInt()
		)

		@Volatile
		var active: DhenTheme = DEFAULT
			private set

		var activeOnRenderThread: DhenTheme = DEFAULT
			private set

		fun activate(theme: DhenTheme) {
			if (theme != active) active = theme
			activeOnRenderThread = theme
		}
	}
}
