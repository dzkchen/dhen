package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ClickGuiRowsTest {
	@Test
	fun `row hit test resolves each collapsed row`() {
		assertEquals(0, ClickGuiRows.rowAt(0, 3, UNIFORM, NO_SETTINGS))
		assertEquals(0, ClickGuiRows.rowAt(12, 3, UNIFORM, NO_SETTINGS))
		assertEquals(1, ClickGuiRows.rowAt(13, 3, UNIFORM, NO_SETTINGS))
		assertEquals(2, ClickGuiRows.rowAt(26, 3, UNIFORM, NO_SETTINGS))
	}

	@Test
	fun `settings area shifts later rows and is itself not a row`() {
		val areas = IntUnaryOperator { index -> if (index == 0) 22 else 0 }
		assertEquals(0, ClickGuiRows.rowAt(12, 2, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(20, 2, UNIFORM, areas))
		assertEquals(1, ClickGuiRows.rowAt(35, 2, UNIFORM, areas))
	}

	@Test
	fun `variable settings heights place each area independently`() {
		val heights = intArrayOf(30, 0, 42)
		val areas = IntUnaryOperator { heights[it] }
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(20, 3, UNIFORM, areas))
		assertEquals(1, ClickGuiRows.rowAt(43, 3, UNIFORM, areas))
		assertEquals(2, ClickGuiRows.rowAt(56, 3, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(70, 3, UNIFORM, areas))
	}

	@Test
	fun `a settings area resolves to the row that owns it, and a row does not`() {
		val heights = intArrayOf(30, 0, 42)
		val areas = IntUnaryOperator { heights[it] }

		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(-1, 3, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(12, 3, UNIFORM, areas))
		assertEquals(0, ClickGuiRows.settingsRowAt(13, 3, UNIFORM, areas))
		assertEquals(0, ClickGuiRows.settingsRowAt(42, 3, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(43, 3, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(56, 3, UNIFORM, areas))
		assertEquals(2, ClickGuiRows.settingsRowAt(69, 3, UNIFORM, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(111, 3, UNIFORM, areas))
	}

	@Test
	fun `a wrapped row keeps the rows under it in step with the taller band`() {
		val rows = intArrayOf(13, 22, 13)
		val heights = IntUnaryOperator { rows[it] }

		assertEquals(0, ClickGuiRows.rowAt(12, 3, heights, NO_SETTINGS))
		assertEquals(1, ClickGuiRows.rowAt(13, 3, heights, NO_SETTINGS))
		assertEquals(1, ClickGuiRows.rowAt(34, 3, heights, NO_SETTINGS))
		assertEquals(2, ClickGuiRows.rowAt(35, 3, heights, NO_SETTINGS))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(48, 3, heights, NO_SETTINGS))
	}

	@Test
	fun `a wrapped row moves the settings band it owns and every band after it`() {
		val rows = intArrayOf(22, 13)
		val heights = IntUnaryOperator { rows[it] }
		val areas = IntUnaryOperator { index -> if (index == 0) 30 else 0 }

		assertEquals(ClickGuiShell.NONE, ClickGuiRows.settingsRowAt(21, 2, heights, areas))
		assertEquals(0, ClickGuiRows.settingsRowAt(22, 2, heights, areas))
		assertEquals(0, ClickGuiRows.settingsRowAt(51, 2, heights, areas))
		assertEquals(1, ClickGuiRows.rowAt(52, 2, heights, areas))
		assertEquals(1, ClickGuiRows.rowAt(64, 2, heights, areas))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(65, 2, heights, areas))
	}

	@Test
	fun `out of range returns no row`() {
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(-1, 2, UNIFORM, NO_SETTINGS))
		assertEquals(ClickGuiShell.NONE, ClickGuiRows.rowAt(26, 2, UNIFORM, NO_SETTINGS))
	}

	private companion object {
		val UNIFORM = IntUnaryOperator { 13 }
		val NO_SETTINGS = IntUnaryOperator { 0 }
	}
}
