package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import kotlin.math.abs

class FlatGuiTest {
	@Test
	fun `rounded inset follows a symmetric quarter circle`() {
		assertEquals(listOf(1), insets(1))
		assertEquals(listOf(2, 1), insets(2))
		assertEquals(listOf(3, 1, 1), insets(3))
		assertEquals(listOf(4, 2, 1, 1), insets(4))
	}

	@Test
	fun `rounded inset rejects rows outside the corner`() {
		assertEquals(0, FlatGui.roundedInset(0, 0))
		assertEquals(0, FlatGui.roundedInset(3, -1))
		assertEquals(0, FlatGui.roundedInset(3, 3))
	}

	@Test
	fun `flat palette colors are opaque and text tiers are ordered`() {
		val colors = intArrayOf(
			DhenPalette.CANVAS,
			DhenPalette.SURFACE,
			DhenPalette.SURFACE_RAISED,
			DhenPalette.SURFACE_INTERACTIVE,
			DhenPalette.BORDER,
			DhenPalette.ACCENT,
			DhenPalette.TEXT_PRIMARY,
			DhenPalette.TEXT_SECONDARY,
			DhenPalette.TEXT_DISABLED,
			DhenPalette.TEXT_ON_ACCENT
		)

		assertTrue(colors.all { it ushr 24 == 0xFF })
		assertEquals(colors.size, colors.distinct().size)
		assertTrue(luminance(DhenPalette.TEXT_PRIMARY) > luminance(DhenPalette.TEXT_SECONDARY))
		assertTrue(luminance(DhenPalette.TEXT_SECONDARY) > luminance(DhenPalette.TEXT_DISABLED))
		assertTrue(abs(luminance(DhenPalette.ACCENT) - luminance(DhenPalette.TEXT_ON_ACCENT)) >= 100)
	}

	private fun insets(radius: Int): List<Int> = List(radius) { FlatGui.roundedInset(radius, it) }

	private fun luminance(color: Int): Int {
		val red = color ushr 16 and 0xFF
		val green = color ushr 8 and 0xFF
		val blue = color and 0xFF
		return (red * 299 + green * 587 + blue * 114) / 1000
	}
}
