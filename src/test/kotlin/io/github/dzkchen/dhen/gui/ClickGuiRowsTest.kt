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
	fun `row top offsets each row past the settings areas above it`() {
		val heights = intArrayOf(30, 0, 42)
		assertEquals(0, ClickGuiRows.rowTop(0, rowHeight = 13) { heights[it] })
		assertEquals(43, ClickGuiRows.rowTop(1, rowHeight = 13) { heights[it] })
		assertEquals(56, ClickGuiRows.rowTop(2, rowHeight = 13) { heights[it] })
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
	fun `a settings area resolves to the row that owns it, and a row does not`() {
		val heights = intArrayOf(30, 0, 42)

		assertNull(ClickGuiRows.settingsRowAt(-1, 3, 13) { heights[it] })
		assertNull(ClickGuiRows.settingsRowAt(12, 3, 13) { heights[it] })
		assertEquals(0, ClickGuiRows.settingsRowAt(13, 3, 13) { heights[it] })
		assertEquals(0, ClickGuiRows.settingsRowAt(42, 3, 13) { heights[it] })
		assertNull(ClickGuiRows.settingsRowAt(43, 3, 13) { heights[it] })
		assertNull(ClickGuiRows.settingsRowAt(56, 3, 13) { heights[it] })
		assertEquals(2, ClickGuiRows.settingsRowAt(69, 3, 13) { heights[it] })
		assertNull(ClickGuiRows.settingsRowAt(111, 3, 13) { heights[it] })
	}

	@Test
	fun `a settings area starts directly under the row it belongs to`() {
		val heights = intArrayOf(30, 0, 42)

		assertEquals(13, ClickGuiRows.settingsTop(0, rowHeight = 13) { heights[it] })
		assertEquals(69, ClickGuiRows.settingsTop(2, rowHeight = 13) { heights[it] })
	}

	@Test
	fun `out of range returns no row`() {
		assertNull(ClickGuiRows.rowAt(-1, 2, 13) { 0 })
		assertNull(ClickGuiRows.rowAt(26, 2, 13) { 0 })
	}
}
