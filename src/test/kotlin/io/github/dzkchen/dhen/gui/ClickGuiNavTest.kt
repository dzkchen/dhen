package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ClickGuiNavTest {
	@Test
	fun `every navigable row across every column is reachable`() {
		assertEquals(9, ClickGuiNav.total(COLUMNS, ROWS))
		assertEquals(0, ClickGuiNav.total(0, ROWS))
	}

	@Test
	fun `a column and row pair maps to one flat position and back`() {
		for (column in 0 until COLUMNS) {
			for (row in 0 until ROWS.applyAsInt(column)) {
				val flat = ClickGuiNav.flatOf(column, row, ROWS)

				assertEquals(column, ClickGuiNav.columnOf(flat, COLUMNS, ROWS))
				assertEquals(row, flat - ClickGuiNav.flatOf(column, 0, ROWS))
			}
		}
	}

	@Test
	fun `a column with no navigable rows is skipped by the flat order`() {
		assertEquals(5, ClickGuiNav.flatOf(EMPTY_COLUMN, 0, ROWS))
		assertEquals(5, ClickGuiNav.flatOf(EMPTY_COLUMN + 1, 0, ROWS))
		assertEquals(EMPTY_COLUMN + 1, ClickGuiNav.columnOf(5, COLUMNS, ROWS))
	}

	@Test
	fun `a position outside the rows resolves to no column`() {
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.columnOf(-1, COLUMNS, ROWS))
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.columnOf(9, COLUMNS, ROWS))
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.columnOf(0, 0, ROWS))
	}

	@Test
	fun `stepping walks the rows and wraps column to column in category order`() {
		assertEquals(1, ClickGuiNav.step(0, 1, 9))
		assertEquals(3, ClickGuiNav.step(2, 1, 9))
		assertEquals(0, ClickGuiNav.step(8, 1, 9))
		assertEquals(8, ClickGuiNav.step(0, -1, 9))
	}

	@Test
	fun `the first arrow press with nothing focused lands on an end row`() {
		assertEquals(0, ClickGuiNav.step(ClickGuiShell.NONE, 1, 9))
		assertEquals(8, ClickGuiNav.step(ClickGuiShell.NONE, -1, 9))
	}

	@Test
	fun `stepping through nothing focuses nothing`() {
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.step(0, 1, 0))
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.step(ClickGuiShell.NONE, 1, 0))
	}

	@Test
	fun `a column jump wraps and never lands on an empty column`() {
		assertEquals(1, ClickGuiNav.columnStep(0, 1, COLUMNS, ROWS))
		assertEquals(3, ClickGuiNav.columnStep(1, 1, COLUMNS, ROWS))
		assertEquals(0, ClickGuiNav.columnStep(3, 1, COLUMNS, ROWS))
		assertEquals(1, ClickGuiNav.columnStep(3, -1, COLUMNS, ROWS))
		assertEquals(3, ClickGuiNav.columnStep(0, -1, COLUMNS, ROWS))
	}

	@Test
	fun `a column jump with only one populated column stays where it is`() {
		val single = IntUnaryOperator { index -> if (index == 1) 4 else 0 }

		assertEquals(1, ClickGuiNav.columnStep(1, 1, COLUMNS, single))
	}

	@Test
	fun `a column jump with nowhere to go reports no column`() {
		val none = IntUnaryOperator { 0 }

		assertEquals(ClickGuiShell.NONE, ClickGuiNav.columnStep(0, 1, COLUMNS, none))
		assertEquals(ClickGuiShell.NONE, ClickGuiNav.columnStep(0, 1, 0, ROWS))
	}

	private companion object {
		const val COLUMNS = 4
		const val EMPTY_COLUMN = 2
		val ROW_COUNTS = intArrayOf(3, 2, 0, 4)
		val ROWS = IntUnaryOperator { index -> ROW_COUNTS[index] }
	}
}
