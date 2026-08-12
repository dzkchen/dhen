package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

class DhenThemeTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		DhenTheme.active = DhenTheme.DEFAULT
	}

	@Test
	fun `the built-in theme carries the colors the design system shipped`() {
		val theme = DhenTheme.DEFAULT

		assertEquals(0xFF08080Au.toInt(), theme.canvas)
		assertEquals(0xFF0D0D10u.toInt(), theme.surface)
		assertEquals(0xFF141418u.toInt(), theme.surfaceRaised)
		assertEquals(0xFF1D1D23u.toInt(), theme.surfaceInteractive)
		assertEquals(0xFF2B2B33u.toInt(), theme.border)
		assertEquals(0xFFF6F4F6u.toInt(), theme.textPrimary)
		assertEquals(0xFFA9A6AEu.toInt(), theme.textSecondary)
		assertEquals(0xFF6B6872u.toInt(), theme.textDisabled)
		assertEquals(0xFF17070Eu.toInt(), theme.textOnAccent)
		assertEquals(0xFFF8D7E3u.toInt(), theme.splashCanvas)
		assertEquals(0xFFEBB4CBu.toInt(), theme.splashTrack)
		assertEquals(0xFF2A0E18u.toInt(), theme.splashInk)
		assertEquals(0xA608080Au.toInt(), theme.glassCanvas)
		assertEquals(0xC20D0D10u.toInt(), theme.glassSurface)
		assertEquals(0xD4141418u.toInt(), theme.glassSurfaceRaised)
		assertEquals(0xE01D1D23u.toInt(), theme.glassSurfaceInteractive)
		assertEquals(0x8C08080Au.toInt(), theme.glassScrim)
		assertEquals(0x73000000u.toInt(), theme.glassShadow)
		assertEquals(0x24FFFFFFu.toInt(), theme.glassSheen)
		assertEquals(0xE608080Au.toInt(), theme.glassVeil)
		assertEquals(0xFFF5A9C6u.toInt(), theme.accent)
		assertEquals(0xFF755362u.toInt(), theme.accentMuted)
		assertEquals(theme.textOnAccent, theme.accentForeground)
	}

	@Test
	fun `the built-in theme carries the motion the design system shipped`() {
		val theme = DhenTheme.DEFAULT

		assertEquals(200L, theme.entryMillis)
		assertEquals(10f, theme.entryRise)
		assertEquals(150L, theme.tabMillis)
		assertEquals(14f, theme.tabSlide)
		assertEquals(100L, theme.toggleMillis)
	}

	@Test
	fun `every token the palette exposes reads the active theme`() {
		DhenTheme.active = DhenTheme.DEFAULT.withAccent(TEAL)

		assertEquals(DhenTheme.active.canvas, DhenPalette.CANVAS)
		assertEquals(DhenTheme.active.surface, DhenPalette.SURFACE)
		assertEquals(DhenTheme.active.surfaceRaised, DhenPalette.SURFACE_RAISED)
		assertEquals(DhenTheme.active.surfaceInteractive, DhenPalette.SURFACE_INTERACTIVE)
		assertEquals(DhenTheme.active.border, DhenPalette.BORDER)
		assertEquals(DhenTheme.active.textPrimary, DhenPalette.TEXT_PRIMARY)
		assertEquals(DhenTheme.active.textSecondary, DhenPalette.TEXT_SECONDARY)
		assertEquals(DhenTheme.active.textDisabled, DhenPalette.TEXT_DISABLED)
		assertEquals(DhenTheme.active.textOnAccent, DhenPalette.TEXT_ON_ACCENT)
		assertEquals(DhenTheme.active.splashCanvas, DhenPalette.SPLASH_CANVAS)
		assertEquals(DhenTheme.active.splashTrack, DhenPalette.SPLASH_TRACK)
		assertEquals(DhenTheme.active.splashInk, DhenPalette.SPLASH_INK)
		assertEquals(DhenTheme.active.glassCanvas, DhenPalette.GLASS_CANVAS)
		assertEquals(DhenTheme.active.glassSurface, DhenPalette.GLASS_SURFACE)
		assertEquals(DhenTheme.active.glassSurfaceRaised, DhenPalette.GLASS_SURFACE_RAISED)
		assertEquals(DhenTheme.active.glassSurfaceInteractive, DhenPalette.GLASS_SURFACE_INTERACTIVE)
		assertEquals(DhenTheme.active.glassScrim, DhenPalette.GLASS_SCRIM)
		assertEquals(DhenTheme.active.glassShadow, DhenPalette.GLASS_SHADOW)
		assertEquals(DhenTheme.active.glassSheen, DhenPalette.GLASS_SHEEN)
		assertEquals(DhenTheme.active.glassVeil, DhenPalette.GLASS_VEIL)
		assertEquals(TEAL, DhenPalette.accent)
		assertEquals(DhenTheme.active.accentMuted, DhenPalette.accentMuted)
		assertEquals(DhenTheme.active.accentForeground, DhenPalette.textOnAccent)
		assertEquals(DhenTheme.DEFAULT.accent, DhenPalette.DEFAULT_ACCENT)
	}

	@Test
	fun `the motion the animations read comes from the active theme`() {
		DhenTheme.active = DhenTheme.DEFAULT.copy(entryMillis = 40L, entryRise = 3f, tabMillis = 30L, tabSlide = 4f, toggleMillis = 20L)

		assertEquals(40L, GlassGui.ENTRY_MILLIS)
		assertEquals(3f, GlassGui.ENTRY_RISE)
		assertEquals(30L, GlassGui.TAB_MILLIS)
		assertEquals(4f, GlassGui.TAB_SLIDE)
		assertEquals(20L, TOGGLE_MILLIS)
	}

	@Test
	fun `an accent override leaves the theme it was resolved from untouched`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertEquals(TEAL, themed.accent)
		assertEquals(0xFFF5A9C6u.toInt(), DhenTheme.DEFAULT.accent)
		assertEquals(DhenTheme.DEFAULT.surface, themed.surface)
	}

	@Test
	fun `re-resolving the accent already in place allocates nothing`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertSame(DhenTheme.DEFAULT, DhenTheme.DEFAULT.withAccent(DhenTheme.DEFAULT.accent))
		assertSame(themed, themed.withAccent(TEAL))
	}

	@Test
	fun `text on the accent stays readable whichever accent the user picks`() {
		for (candidate in listOf(DhenTheme.DEFAULT.accent, TEAL, 0xFF3A0B5Fu.toInt(), 0xFF000000u.toInt())) {
			val themed = DhenTheme.DEFAULT.withAccent(candidate)
			val gap = abs(DhenPalette.luminance(candidate) - DhenPalette.luminance(themed.accentForeground))

			assertTrue(gap >= 100, "accent ${Integer.toHexString(candidate)} left only $gap luminance of contrast")
		}
	}

	@Test
	fun `the muted accent follows the slot it is derived from`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertNotEquals(DhenTheme.DEFAULT.accentMuted, themed.accentMuted)
		assertEquals(0xFF, themed.accentMuted ushr 24)
		assertTrue(DhenPalette.luminance(themed.accentMuted) < DhenPalette.luminance(themed.accent))
		assertTrue(DhenPalette.luminance(themed.accentMuted) > DhenPalette.luminance(themed.surface))
	}

	@Test
	fun `a transparent accent keeps its alpha through the muted derivation`() {
		assertEquals(0x80, DhenTheme.DEFAULT.withAccent(0x80F5A9C6u.toInt()).accentMuted ushr 24)
	}

	@Test
	fun `no color literal lives outside the token table`() {
		val scanned = SourceScan.files(SOURCE_ROOT, SOURCES)
		assertTrue(scanned.any { it.name == TOKENS }) { "scan missed the sources at ${SOURCE_ROOT.absolutePath}" }
		assertTrue(scanned.any { it.extension == "java" }) { "scan missed the mixins at ${SOURCE_ROOT.absolutePath}" }

		val offenders = SourceScan.offenders(scanned, TOKENS, ARGB)

		assertTrue(offenders.isEmpty()) {
			"Every color belongs to $TOKENS, but these hold their own:\n${offenders.joinToString("\n")}"
		}
	}

	private companion object {
		const val TOKENS = "DhenTheme.kt"
		val TEAL = 0xFF55D6C2u.toInt()
		val SOURCE_ROOT = File("src/main")
		val SOURCES = setOf("kt", "java")
		val ARGB = Regex("""0[xX][0-9A-Fa-f]{8}""")
	}
}
