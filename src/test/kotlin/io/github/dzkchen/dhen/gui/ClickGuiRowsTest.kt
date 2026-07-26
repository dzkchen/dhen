package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClickGuiRowsTest {
	@Test
	fun `body height sums rows and per-module settings areas`() {
		assertEquals(0, ClickGuiRows.bodyHeight(rowCount = 0, rowHeight = 13) { 0 })
		assertEquals(39, ClickGuiRows.bodyHeight(rowCount = 3, rowHeight = 13) { 0 })
		val heights = intArrayOf(30, 0, 42)
		assertEquals(3 * 13 + 30 + 42, ClickGuiRows.bodyHeight(rowCount = 3, rowHeight = 13) { heights[it] })
	}

	@Test
	fun `row hit test resolves each collapsed row`() {
		assertEquals(0, ClickGuiRows.rowAt(0, 3, 13) { 0 })
		assertEquals(0, ClickGuiRows.rowAt(12, 3, 13) { 0 })
		assertEquals(1, ClickGuiRows.rowAt(13, 3, 13) { 0 })
		assertEquals(2, ClickGuiRows.rowAt(26, 3, 13) { 0 })
	}

	@Test
	fun `settings area shifts later rows and is itself not a row`() {
		assertEquals(0, ClickGuiRows.rowAt(12, 2, 13) { index -> if (index == 0) 22 else 0 })
		assertNull(ClickGuiRows.rowAt(20, 2, 13) { index -> if (index == 0) 22 else 0 })
		assertEquals(1, ClickGuiRows.rowAt(35, 2, 13) { index -> if (index == 0) 22 else 0 })
	}

	@Test
	fun `variable settings heights place each area independently`() {
		val heights = intArrayOf(30, 0, 42)
		assertNull(ClickGuiRows.rowAt(20, 3, 13) { heights[it] })
		assertEquals(1, ClickGuiRows.rowAt(43, 3, 13) { heights[it] })
		assertEquals(2, ClickGuiRows.rowAt(56, 3, 13) { heights[it] })
		assertNull(ClickGuiRows.rowAt(70, 3, 13) { heights[it] })
	}

	@Test
	fun `out of range returns no row`() {
		assertNull(ClickGuiRows.rowAt(-1, 2, 13) { 0 })
		assertNull(ClickGuiRows.rowAt(26, 2, 13) { 0 })
	}
}
