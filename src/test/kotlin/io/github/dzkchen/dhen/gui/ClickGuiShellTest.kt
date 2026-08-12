package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ClickGuiShellTest {
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
	fun `a column taller than the viewport is cut to what the viewport can show`() {
		assertEquals(200, ClickGuiShell.clampedColumnHeight(naturalHeight = 200, headerHeight = 18, available = 260))
		assertEquals(260, ClickGuiShell.clampedColumnHeight(naturalHeight = 400, headerHeight = 18, available = 260))
	}

	@Test
	fun `a viewport too short for even the header keeps the header`() {
		assertEquals(18, ClickGuiShell.clampedColumnHeight(naturalHeight = 400, headerHeight = 18, available = 4))
		assertEquals(18, ClickGuiShell.clampedColumnHeight(naturalHeight = 400, headerHeight = 18, available = -20))
	}

	@Test
	fun `stacked sections sit under one another with one gap between them`() {
		assertEquals(0, ClickGuiShell.spanStart(0, SECTIONS, SECTION_GAP))
		assertEquals(52, ClickGuiShell.spanStart(1, SECTIONS, SECTION_GAP))
		assertEquals(104, ClickGuiShell.spanStart(2, SECTIONS, SECTION_GAP))
	}

	@Test
	fun `the stack is as tall as its sections plus the gaps between them`() {
		assertEquals(0, ClickGuiShell.spanTotal(0, SECTIONS, SECTION_GAP))
		assertEquals(46, ClickGuiShell.spanTotal(1, SECTIONS, SECTION_GAP))
		assertEquals(143, ClickGuiShell.spanTotal(3, SECTIONS, SECTION_GAP))
	}

	@Test
	fun `a hit lands on the section under it and never in a gap`() {
		assertEquals(0, ClickGuiShell.spanAt(0, 3, SECTIONS, SECTION_GAP))
		assertEquals(0, ClickGuiShell.spanAt(45, 3, SECTIONS, SECTION_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(46, 3, SECTIONS, SECTION_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(51, 3, SECTIONS, SECTION_GAP))
		assertEquals(1, ClickGuiShell.spanAt(52, 3, SECTIONS, SECTION_GAP))
		assertEquals(2, ClickGuiShell.spanAt(142, 3, SECTIONS, SECTION_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(143, 3, SECTIONS, SECTION_GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(-1, 3, SECTIONS, SECTION_GAP))
	}

	@Test
	fun `a section hit resolves to the control row it lands on`() {
		assertEquals(0, ClickGuiShell.sectionRowAt(localY = 22, bodyTop = 22, rowCount = 2, rowHeight = 20))
		assertEquals(0, ClickGuiShell.sectionRowAt(localY = 41, bodyTop = 22, rowCount = 2, rowHeight = 20))
		assertEquals(1, ClickGuiShell.sectionRowAt(localY = 42, bodyTop = 22, rowCount = 2, rowHeight = 20))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.sectionRowAt(localY = 62, bodyTop = 22, rowCount = 2, rowHeight = 20))
	}

	@Test
	fun `a hit on a section header or an empty section reaches no control`() {
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.sectionRowAt(localY = 0, bodyTop = 22, rowCount = 2, rowHeight = 20))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.sectionRowAt(localY = 21, bodyTop = 22, rowCount = 2, rowHeight = 20))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.sectionRowAt(localY = 30, bodyTop = 22, rowCount = 0, rowHeight = 20))
	}

	@Test
	fun `an accordion stacks closed categories as bare headers and gives the opened one its rows`() {
		assertEquals(0, ClickGuiShell.spanStart(0, ACCORDION, GAP))
		assertEquals(52, ClickGuiShell.spanStart(2, ACCORDION, GAP))
		assertEquals(160, ClickGuiShell.spanStart(3, ACCORDION, GAP))
		assertEquals(516, ClickGuiShell.spanTotal(CATEGORIES, ACCORDION, GAP))
	}

	@Test
	fun `a hit in the accordion lands on the category under it and never between two`() {
		assertEquals(0, ClickGuiShell.spanAt(17, CATEGORIES, ACCORDION, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(18, CATEGORIES, ACCORDION, GAP))
		assertEquals(2, ClickGuiShell.spanAt(52, CATEGORIES, ACCORDION, GAP))
		assertEquals(2, ClickGuiShell.spanAt(151, CATEGORIES, ACCORDION, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(152, CATEGORIES, ACCORDION, GAP))
		assertEquals(3, ClickGuiShell.spanAt(160, CATEGORIES, ACCORDION, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(516, CATEGORIES, ACCORDION, GAP))
	}

	@Test
	fun `a tooltip sits beside its column while there is room to the right`() {
		assertEquals(132, tooltipLeft(columnLeft = 8, tooltipWidth = 120))
		assertEquals(346, tooltipLeft(columnLeft = 222, tooltipWidth = 120))
	}

	@Test
	fun `a tooltip with no room to the right flips to the other side of its column`() {
		assertEquals(230, tooltipLeft(columnLeft = 356, tooltipWidth = 120))
	}

	@Test
	fun `a tooltip that fits on neither side is pinned inside the viewport`() {
		assertEquals(72, tooltipLeft(columnLeft = 8, tooltipWidth = 400))
		assertEquals(8, tooltipLeft(columnLeft = 8, tooltipWidth = 600))
	}

	@Test
	fun `a tooltip sits level with its row until the bottom edge pushes it up`() {
		assertEquals(120, ClickGuiShell.tooltipTop(rowTop = 120, tooltipHeight = 20, viewportHeight = 260, margin = MARGIN))
		assertEquals(232, ClickGuiShell.tooltipTop(rowTop = 250, tooltipHeight = 20, viewportHeight = 260, margin = MARGIN))
		assertEquals(8, ClickGuiShell.tooltipTop(rowTop = -40, tooltipHeight = 20, viewportHeight = 260, margin = MARGIN))
	}

	@Test
	fun `a tooltip taller than the viewport still starts inside it`() {
		assertEquals(8, ClickGuiShell.tooltipTop(rowTop = 120, tooltipHeight = 400, viewportHeight = 260, margin = MARGIN))
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

	private fun tooltipLeft(columnLeft: Int, tooltipWidth: Int): Int =
		ClickGuiShell.tooltipLeft(columnLeft, COLUMN_WIDTH, tooltipWidth, VIEWPORT_WIDTH, TOOLTIP_GAP, MARGIN)

	private fun columnHeight(collapsed: Boolean, rowCount: Int, settingsHeightAt: IntUnaryOperator): Int =
		ClickGuiShell.columnHeight(collapsed, headerHeight = 18, bodyPad = 4, rowCount = rowCount, rowHeight = 13, settingsHeightAt = settingsHeightAt)

	private companion object {
		const val COLUMN_WIDTH = 118
		const val GAP = 8
		const val MARGIN = 8
		const val CATEGORIES = 17
		const val HEADER_HEIGHT = 18
		const val OPENED_SLOT = 2
		const val OPENED_HEIGHT = 100
		const val SEGMENT_GAP = 2
		const val SECTION_GAP = 6
		const val TOOLTIP_GAP = 6
		const val VIEWPORT_WIDTH = 480
		val TABS = intArrayOf(60, 40)
		val SECTION_HEIGHTS = intArrayOf(46, 46, 39)
		val SECTIONS = IntUnaryOperator { index -> SECTION_HEIGHTS[index] }
		val ACCORDION = IntUnaryOperator { index -> if (index == OPENED_SLOT) OPENED_HEIGHT else HEADER_HEIGHT }
	}
}
