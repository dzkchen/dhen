package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DhenPaletteTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		DhenPalette.accent = DhenPalette.DEFAULT_ACCENT
	}

	@Test
	fun `the palette is opaque, distinct, and ordered from canvas to text`() {
		val colors = intArrayOf(
			DhenPalette.CANVAS,
			DhenPalette.SURFACE,
			DhenPalette.SURFACE_RAISED,
			DhenPalette.SURFACE_INTERACTIVE,
			DhenPalette.BORDER,
			DhenPalette.accent,
			DhenPalette.TEXT_PRIMARY,
			DhenPalette.TEXT_SECONDARY,
			DhenPalette.TEXT_DISABLED,
			DhenPalette.TEXT_ON_ACCENT
		)

		assertTrue(colors.all { it ushr 24 == 0xFF })
		assertEquals(colors.size, colors.distinct().size)
		assertTrue(DhenPalette.luminance(DhenPalette.TEXT_PRIMARY) > DhenPalette.luminance(DhenPalette.TEXT_SECONDARY))
		assertTrue(DhenPalette.luminance(DhenPalette.TEXT_SECONDARY) > DhenPalette.luminance(DhenPalette.TEXT_DISABLED))
	}

	@Test
	fun `the surface set stays near black so panels read as glass`() {
		val surfaces = intArrayOf(
			DhenPalette.CANVAS,
			DhenPalette.SURFACE,
			DhenPalette.SURFACE_RAISED,
			DhenPalette.SURFACE_INTERACTIVE
		)

		assertTrue(surfaces.all { DhenPalette.luminance(it) < 40 })
		assertTrue(DhenPalette.luminance(DhenPalette.CANVAS) < DhenPalette.luminance(DhenPalette.SURFACE_INTERACTIVE))
		assertTrue(DhenPalette.luminance(DhenPalette.BORDER) > DhenPalette.luminance(DhenPalette.SURFACE_INTERACTIVE))
	}

	@Test
	fun `the default accent is an opaque pink that carries readable text`() {
		assertEquals(DhenPalette.DEFAULT_ACCENT, DhenPalette.accent)
		assertEquals(0xFF, DhenPalette.accent ushr 24)

		val red = DhenPalette.accent ushr 16 and 0xFF
		val blue = DhenPalette.accent and 0xFF
		val green = DhenPalette.accent ushr 8 and 0xFF
		assertTrue(red > green && blue > green, "baby pink is a light red-and-blue tint")
		assertTrue(DhenPalette.luminance(DhenPalette.accent) > 150)
	}

	@Test
	fun `text on the accent stays readable whichever accent the user picks`() {
		for (candidate in listOf(DhenPalette.DEFAULT_ACCENT, 0xFF55D6C2u.toInt(), 0xFF3A0B5Fu.toInt(), 0xFF000000u.toInt())) {
			DhenPalette.accent = candidate
			val gap = abs(DhenPalette.luminance(candidate) - DhenPalette.luminance(DhenPalette.textOnAccent))
			assertTrue(gap >= 100, "accent ${Integer.toHexString(candidate)} left only $gap luminance of contrast")
		}
	}

	@Test
	fun `the muted accent follows the slot it is derived from`() {
		val default = DhenPalette.accentMuted
		DhenPalette.accent = 0xFF55D6C2u.toInt()

		assertNotEquals(default, DhenPalette.accentMuted)
		assertEquals(0xFF, DhenPalette.accentMuted ushr 24)
		assertTrue(DhenPalette.luminance(DhenPalette.accentMuted) < DhenPalette.luminance(DhenPalette.accent))
		assertTrue(DhenPalette.luminance(DhenPalette.accentMuted) > DhenPalette.luminance(DhenPalette.SURFACE))
	}

	@Test
	fun `a transparent accent keeps its alpha through the muted derivation`() {
		DhenPalette.accent = 0x80F5A9C6u.toInt()

		assertEquals(0x80, DhenPalette.accentMuted ushr 24)
	}
}
