package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ClickGuiShellTest {
	@Test
	fun `columns sit side by side in slot order`() {
		assertEquals(0, ClickGuiShell.columnLeft(0, COLUMN_WIDTH, GAP))
		assertEquals(126, ClickGuiShell.columnLeft(1, COLUMN_WIDTH, GAP))
		assertEquals(2016, ClickGuiShell.columnLeft(16, COLUMN_WIDTH, GAP))
	}

	@Test
	fun `field width counts the gaps between columns only`() {
		assertEquals(0, ClickGuiShell.fieldWidth(0, COLUMN_WIDTH, GAP))
		assertEquals(118, ClickGuiShell.fieldWidth(1, COLUMN_WIDTH, GAP))
		assertEquals(2134, ClickGuiShell.fieldWidth(CATEGORIES, COLUMN_WIDTH, GAP))
	}

	@Test
	fun `the field scrolls by whatever the viewport cannot show`() {
		val right = MARGIN + ClickGuiShell.fieldWidth(CATEGORIES, COLUMN_WIDTH, GAP)
		assertEquals(1766, ClickGuiScroll.maxScroll(right, viewportHeight = 384, margin = MARGIN))
		assertEquals(0, ClickGuiScroll.maxScroll(right, viewportHeight = 2150, margin = MARGIN))
		assertEquals(0, ClickGuiScroll.maxScroll(right, viewportHeight = 4000, margin = MARGIN))
	}

	@Test
	fun `a hit lands on the column under it and never in a gap`() {
		assertEquals(0, ClickGuiShell.slotAt(0, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(0, ClickGuiShell.slotAt(117, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.slotAt(118, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.slotAt(125, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(1, ClickGuiShell.slotAt(126, CATEGORIES, COLUMN_WIDTH, GAP))
	}

	@Test
	fun `a hit outside the field resolves to no column`() {
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.slotAt(-1, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.slotAt(0, 0, COLUMN_WIDTH, GAP))
		val past = ClickGuiShell.columnLeft(CATEGORIES, COLUMN_WIDTH, GAP)
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.slotAt(past, CATEGORIES, COLUMN_WIDTH, GAP))
		assertEquals(16, ClickGuiShell.slotAt(past - GAP - 1, CATEGORIES, COLUMN_WIDTH, GAP))
	}

	@Test
	fun `an expanded category is its header plus every row and settings area`() {
		val heights = intArrayOf(30, 0, 42)

		assertEquals(18 + 4 + 3 * 13 + 72, columnHeight(collapsed = false, rowCount = 3) { heights[it] })
		assertEquals(18 + 4, columnHeight(collapsed = false, rowCount = 0) { 0 })
	}

	@Test
	fun `a collapsed category is its header alone and never measures its rows`() {
		assertEquals(18, columnHeight(collapsed = true, rowCount = 3) { error("collapsed columns must not measure rows") })
	}

	@Test
	fun `segments lay out left to right with one gap between them`() {
		assertEquals(0, ClickGuiShell.segmentsWidth(IntArray(0), SEGMENT_GAP))
		assertEquals(60, ClickGuiShell.segmentsWidth(intArrayOf(60), SEGMENT_GAP))
		assertEquals(102, ClickGuiShell.segmentsWidth(TABS, SEGMENT_GAP))
	}

	@Test
	fun `a click resolves to the segment it lands on`() {
		assertEquals(0, ClickGuiShell.segmentAt(0, TABS, SEGMENT_GAP))
		assertEquals(0, ClickGuiShell.segmentAt(59, TABS, SEGMENT_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.segmentAt(60, TABS, SEGMENT_GAP))
		assertEquals(1, ClickGuiShell.segmentAt(62, TABS, SEGMENT_GAP))
		assertEquals(1, ClickGuiShell.segmentAt(101, TABS, SEGMENT_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.segmentAt(102, TABS, SEGMENT_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.segmentAt(-1, TABS, SEGMENT_GAP))
	}

	@Test
	fun `centered chrome keeps equal margins on both sides`() {
		assertEquals(120, ClickGuiShell.centeredLeft(viewportWidth = 480, width = 240))
		assertEquals(0, ClickGuiShell.centeredLeft(viewportWidth = 240, width = 240))
		assertEquals(-20, ClickGuiShell.centeredLeft(viewportWidth = 200, width = 240))
	}

	@Test
	fun `right aligned chrome keeps its margin off the right edge`() {
		assertEquals(392, ClickGuiShell.rightAlignedLeft(viewportWidth = 480, width = 80, margin = MARGIN))
		assertEquals(-8, ClickGuiShell.rightAlignedLeft(viewportWidth = 80, width = 80, margin = MARGIN))
	}

	private fun columnHeight(collapsed: Boolean, rowCount: Int, settingsHeightAt: IntUnaryOperator): Int =
		ClickGuiShell.columnHeight(collapsed, headerHeight = 18, bodyPad = 4, rowCount = rowCount, rowHeight = 13, settingsHeightAt = settingsHeightAt)

	private companion object {
		const val COLUMN_WIDTH = 118
		const val GAP = 8
		const val MARGIN = 8
		const val CATEGORIES = 17
		const val SEGMENT_GAP = 2
		val TABS = intArrayOf(60, 40)
	}
}
