package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

	private fun insets(radius: Int): List<Int> = List(radius) { FlatGui.roundedInset(radius, it) }
}
