package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClickGuiRowsTest {
	@Test
	fun `body height sums rows and expanded settings`() {
		assertEquals(0, ClickGuiRows.bodyHeight(rowCount = 0, expandedCount = 0, rowHeight = 13, settingsHeight = 22))
		assertEquals(39, ClickGuiRows.bodyHeight(rowCount = 3, expandedCount = 0, rowHeight = 13, settingsHeight = 22))
		assertEquals(61, ClickGuiRows.bodyHeight(rowCount = 3, expandedCount = 1, rowHeight = 13, settingsHeight = 22))
	}

	@Test
	fun `row hit test resolves each collapsed row`() {
		val flags = listOf(false, false, false)
		assertEquals(0, ClickGuiRows.rowAt(0, flags.size, 13, 22, flags::get))
		assertEquals(0, ClickGuiRows.rowAt(12, flags.size, 13, 22, flags::get))
		assertEquals(1, ClickGuiRows.rowAt(13, flags.size, 13, 22, flags::get))
		assertEquals(2, ClickGuiRows.rowAt(26, flags.size, 13, 22, flags::get))
	}

	@Test
	fun `expanded settings area shifts later rows and is itself not a row`() {
		val flags = listOf(true, false)
		assertEquals(0, ClickGuiRows.rowAt(12, flags.size, 13, 22, flags::get))
		assertNull(ClickGuiRows.rowAt(20, flags.size, 13, 22, flags::get))
		assertEquals(1, ClickGuiRows.rowAt(35, flags.size, 13, 22, flags::get))
	}

	@Test
	fun `out of range returns no row`() {
		val flags = listOf(false, false)
		assertNull(ClickGuiRows.rowAt(-1, flags.size, 13, 22, flags::get))
		assertNull(ClickGuiRows.rowAt(26, flags.size, 13, 22, flags::get))
	}
}
