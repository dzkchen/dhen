package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ClickGuiShellTest {
	@Test
	fun `only the features tab routes typed characters into search`() {
		val chrome = ClickGuiChrome({ VIEWPORT_WIDTH }, { 260 }) { _, _, _ -> }

		assertTrue(chrome.acceptsTextInput)
		chrome.switchTab(1)
		assertFalse(chrome.acceptsTextInput)
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
	fun `a stack of varying extents starts each entry after the ones above it`() {
		assertEquals(0, ClickGuiShell.spanStart(0, VARYING, GAP))
		assertEquals(52, ClickGuiShell.spanStart(2, VARYING, GAP))
		assertEquals(160, ClickGuiShell.spanStart(3, VARYING, GAP))
		assertEquals(516, ClickGuiShell.spanTotal(CATEGORIES, VARYING, GAP))
	}

	@Test
	fun `a hit in a stack of varying extents lands on the entry under it and never between two`() {
		assertEquals(0, ClickGuiShell.spanAt(17, CATEGORIES, VARYING, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(18, CATEGORIES, VARYING, GAP))
		assertEquals(2, ClickGuiShell.spanAt(52, CATEGORIES, VARYING, GAP))
		assertEquals(2, ClickGuiShell.spanAt(151, CATEGORIES, VARYING, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(152, CATEGORIES, VARYING, GAP))
		assertEquals(3, ClickGuiShell.spanAt(160, CATEGORIES, VARYING, GAP))
		assertEquals(ClickGuiShell.NONE, ClickGuiShell.spanAt(516, CATEGORIES, VARYING, GAP))
	}

	@Test
	fun `segments that do not fit shrink in proportion and land exactly in the room left`() {
		val widths = intArrayOf(100, 60)

		ClickGuiShell.shrinkSegments(widths, TAB_GAP, 82)

		assertEquals(82, ClickGuiShell.segmentsWidth(widths, TAB_GAP))
		assertTrue(widths[0] > widths[1])
		assertTrue(widths.all { it > 0 })
	}

	@Test
	fun `segments that already fit are left alone`() {
		val widths = intArrayOf(100, 60)

		ClickGuiShell.shrinkSegments(widths, TAB_GAP, 400)

		assertArrayEquals(intArrayOf(100, 60), widths)
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
	fun `a segment starts where a click on its first pixel resolves back to it`() {
		assertEquals(0, ClickGuiShell.segmentStart(0, TABS, SEGMENT_GAP))
		assertEquals(62, ClickGuiShell.segmentStart(1, TABS, SEGMENT_GAP))
		assertEquals(ClickGuiShell.segmentsWidth(TABS, SEGMENT_GAP) + SEGMENT_GAP, ClickGuiShell.segmentStart(TABS.size, TABS, SEGMENT_GAP))

		for (segment in TABS.indices) {
			assertEquals(segment, ClickGuiShell.segmentAt(ClickGuiShell.segmentStart(segment, TABS, SEGMENT_GAP), TABS, SEGMENT_GAP))
		}
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

	private companion object {
		const val COLUMN_WIDTH = 118
		const val GAP = 8
		const val MARGIN = 8
		const val CATEGORIES = 17
		const val HEADER_HEIGHT = 18
		const val TALL_SLOT = 2
		const val TALL_HEIGHT = 100
		const val SEGMENT_GAP = 2
		const val SECTION_GAP = 6
		const val TOOLTIP_GAP = 6
		const val VIEWPORT_WIDTH = 480
		val TABS = intArrayOf(60, 40)
		val SECTION_HEIGHTS = intArrayOf(46, 46, 39)
		val SECTIONS = IntUnaryOperator { index -> SECTION_HEIGHTS[index] }
		val VARYING = IntUnaryOperator { index -> if (index == TALL_SLOT) TALL_HEIGHT else HEADER_HEIGHT }
	}
}
