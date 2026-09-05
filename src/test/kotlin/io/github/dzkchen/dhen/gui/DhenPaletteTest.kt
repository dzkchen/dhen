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
		DhenTheme.activate(DhenTheme.DEFAULT)
	}

	@Test
	fun `the star ramp answers every star count without running off either end`() {
		assertEquals(DhenTheme.SLOT_STAR_DUNGEON.first(), DhenPalette.slotStar(0, dungeon = true))
		assertEquals(DhenTheme.SLOT_STAR_DUNGEON.first(), DhenPalette.slotStar(1, dungeon = true))
		assertEquals(DhenTheme.SLOT_STAR_DUNGEON.last(), DhenPalette.slotStar(10, dungeon = true))
		assertEquals(DhenTheme.SLOT_STAR_DUNGEON.last(), DhenPalette.slotStar(99, dungeon = true))
		assertEquals(DhenTheme.SLOT_STAR_NORMAL[9], DhenPalette.slotStar(10, dungeon = false))
		assertEquals(DhenTheme.SLOT_STAR_NORMAL.last(), DhenPalette.slotStar(15, dungeon = false))
		assertEquals(DhenTheme.SLOT_STAR_NORMAL.last(), DhenPalette.slotStar(99, dungeon = false))
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
