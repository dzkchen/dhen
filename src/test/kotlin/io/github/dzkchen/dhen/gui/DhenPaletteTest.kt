package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DhenPaletteTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		DhenTheme.active = DhenTheme.DEFAULT
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
	fun `the splash set is a light pink ground with ink dark enough to read on it`() {
		val splash = intArrayOf(DhenPalette.SPLASH_CANVAS, DhenPalette.SPLASH_TRACK, DhenPalette.SPLASH_INK)

		assertTrue(splash.all { it ushr 24 == 0xFF })
		assertEquals(splash.size, splash.distinct().size)
		assertTrue(DhenPalette.luminance(DhenPalette.SPLASH_CANVAS) > 190, "the splash ground is not light")
		assertTrue(DhenPalette.luminance(DhenPalette.SPLASH_INK) < 60, "the splash ink is not dark")
		assertTrue(
			DhenPalette.luminance(DhenPalette.SPLASH_TRACK) < DhenPalette.luminance(DhenPalette.SPLASH_CANVAS),
			"the bar track has to sit deeper than the ground it lies on"
		)
		assertTrue(
			DhenPalette.luminance(DhenPalette.SPLASH_INK) < DhenPalette.luminance(DhenPalette.SPLASH_TRACK),
			"the filled bar has to read against its own track"
		)
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
	fun `a mix lands on each end and blends every channel in between`() {
		val from = 0x00204060
		val to = 0xFF60A0E0u.toInt()

		assertEquals(from, DhenPalette.mix(from, to, 0f))
		assertEquals(to, DhenPalette.mix(from, to, 1f))
		assertEquals(0x804070A0u.toInt(), DhenPalette.mix(from, to, 0.5f))
	}

	@Test
	fun `a mix clamps a progress that overshoots and never leaves the endpoints`() {
		val from = DhenPalette.TEXT_SECONDARY
		val to = DhenPalette.TEXT_PRIMARY

		assertEquals(to, DhenPalette.mix(from, to, 4f))
		assertEquals(from, DhenPalette.mix(from, to, -1f))
		assertEquals(to, DhenPalette.mix(to, to, 0.5f))
	}
}
