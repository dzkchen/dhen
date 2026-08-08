package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClickGuiNavTest {
	private val counts = intArrayOf(2, 0, 3)

	@Test
	fun `flatten walks panels in order and rejects out of range rows`() {
		assertEquals(0, ClickGuiNav.flatten(counts, 0, 0))
		assertEquals(1, ClickGuiNav.flatten(counts, 0, 1))
		assertEquals(2, ClickGuiNav.flatten(counts, 2, 0))
		assertEquals(4, ClickGuiNav.flatten(counts, 2, 2))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.flatten(counts, 1, 0))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.flatten(counts, 0, 2))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.flatten(counts, 3, 0))
	}

	@Test
	fun `locating a flat position skips empty panels`() {
		assertEquals(0, ClickGuiNav.panelOf(counts, 1))
		assertEquals(1, ClickGuiNav.rowOf(counts, 1))
		assertEquals(2, ClickGuiNav.panelOf(counts, 2))
		assertEquals(0, ClickGuiNav.rowOf(counts, 2))
		assertEquals(2, ClickGuiNav.panelOf(counts, 4))
		assertEquals(2, ClickGuiNav.rowOf(counts, 4))
	}

	@Test
	fun `locating a position past the last row resolves to nothing`() {
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.panelOf(counts, 5))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.rowOf(counts, 5))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.panelOf(counts, -1))
	}

	@Test
	fun `stepping wraps in both directions`() {
		assertEquals(1, ClickGuiNav.step(total = 5, flat = 0, delta = 1))
		assertEquals(0, ClickGuiNav.step(total = 5, flat = 4, delta = 1))
		assertEquals(4, ClickGuiNav.step(total = 5, flat = 0, delta = -1))
		assertEquals(3, ClickGuiNav.step(total = 5, flat = 4, delta = -1))
	}

	@Test
	fun `stepping without a focus takes the first or last row`() {
		assertEquals(0, ClickGuiNav.step(total = 5, flat = ClickGuiNav.NONE, delta = 1))
		assertEquals(4, ClickGuiNav.step(total = 5, flat = ClickGuiNav.NONE, delta = -1))
	}

	@Test
	fun `stepping with nothing navigable resolves to nothing`() {
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.step(total = 0, flat = ClickGuiNav.NONE, delta = 1))
		assertEquals(ClickGuiNav.NONE, ClickGuiNav.step(total = 0, flat = 0, delta = -1))
	}
}
