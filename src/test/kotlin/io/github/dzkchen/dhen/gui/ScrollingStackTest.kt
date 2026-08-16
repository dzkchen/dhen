package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class ScrollingStackTest {
	private var stackCount = 0

	@Test
	fun `columns sit side by side in slot order`() {
		val field = field(CATEGORIES)

		assertEquals(0, field.startOf(0))
		assertEquals(126, field.startOf(1))
		assertEquals(2016, field.startOf(16))
	}

	@Test
	fun `the field scrolls by whatever the viewport cannot show`() {
		assertEquals(1766, field(CATEGORIES, viewport = 384).max())
		assertEquals(0, field(CATEGORIES, viewport = 2150).max())
		assertEquals(0, field(CATEGORIES, viewport = 4000).max())
		assertEquals(0, field(0, viewport = 384).max())
	}

	@Test
	fun `a hit lands on the column under it and never in a gap`() {
		val field = field(CATEGORIES)

		assertEquals(0, field.slotAt(MARGIN))
		assertEquals(0, field.slotAt(MARGIN + 117))
		assertEquals(ClickGuiShell.NONE, field.slotAt(MARGIN + 118))
		assertEquals(ClickGuiShell.NONE, field.slotAt(MARGIN + 125))
		assertEquals(1, field.slotAt(MARGIN + 126))
	}

	@Test
	fun `a hit outside the field resolves to no column`() {
		val field = field(CATEGORIES)

		assertEquals(ClickGuiShell.NONE, field.slotAt(MARGIN - 1))
		assertEquals(ClickGuiShell.NONE, field(0).slotAt(MARGIN))
		assertEquals(ClickGuiShell.NONE, field.slotAt(MARGIN + field.startOf(CATEGORIES)))
		assertEquals(16, field.slotAt(MARGIN + field.startOf(CATEGORIES) - GAP - 1))
	}

	@Test
	fun `a scrolled stack draws each entry where a click on it resolves back`() {
		val stack = varyingStack()
		stack.scrollBy(-90)

		for (slot in intArrayOf(0, TALL_SLOT, CATEGORIES - 1)) {
			assertEquals(slot, stack.slotAt(stack.originOf(slot)))
		}
	}

	@Test
	fun `an unscrolled stack starts at the top of the field`() {
		assertEquals(FIELD_TOP, varyingStack().originOf(0))
	}

	@Test
	fun `a stack that fits its viewport refuses to scroll`() {
		val stack = varyingStack(viewport = 900)

		assertEquals(0, stack.max())
		assertFalse(stack.scrollBy(-90))
		assertEquals(0, stack.offset)
	}

	@Test
	fun `an entry already on screen is not scrolled to`() {
		val stack = varyingStack()
		stack.scrollBy(-90)

		stack.reveal(3)

		assertEquals(90, stack.offset)
	}

	@Test
	fun `revealing an entry above the window scrolls its top into view`() {
		val stack = varyingStack()
		stack.scrollBy(-90)

		stack.reveal(0)

		assertEquals(0, stack.offset)
	}

	@Test
	fun `revealing an entry below the window scrolls exactly far enough`() {
		val stack = varyingStack()

		stack.reveal(CATEGORIES - 1)

		assertEquals(stack.max(), stack.offset)
		assertTrue(stack.originOf(CATEGORIES - 1) + HEADER_HEIGHT <= VIEWPORT - MARGIN)
	}

	@Test
	fun `an entry taller than the window is revealed from its top`() {
		val stack = ScrollingStack(
			FIELD_TOP,
			GAP,
			MARGIN,
			{ 3 },
			{ VIEWPORT },
			{ index -> if (index == 1) 400 else HEADER_HEIGHT }
		)

		stack.reveal(1)

		assertEquals(HEADER_HEIGHT + GAP, stack.offset)
	}

	@Test
	fun `a reveal keeps the offset the search is holding for a widening query`() {
		val stack = varyingStack()
		stack.scrollBy(-90)
		stack.refilterAt(1)
		stack.refilterAt(6)

		stack.reveal(0)
		assertEquals(0, stack.offset)

		stack.refilterAt(CATEGORIES)
		assertEquals(90, stack.offset)
	}

	@Test
	fun `a wheel scroll takes the field over from the search`() {
		val stack = varyingStack()
		stack.scrollBy(-90)
		stack.refilterAt(1)
		stack.refilterAt(6)

		stack.scrollBy(1000)
		assertEquals(0, stack.offset)

		stack.refilterAt(CATEGORIES)
		assertEquals(0, stack.offset)
	}

	@Test
	fun `a column's rows stack under one another with no gap`() {
		val column = column()

		assertEquals(0, column.startOf(0))
		assertEquals(43, column.startOf(1))
		assertEquals(56, column.startOf(2))
		assertEquals(3 * ROW_HEIGHT + 30 + 42, column.total())
	}

	@Test
	fun `an empty column measures nothing and takes no room`() {
		val column = ScrollingStack(BODY_TOP, NO_GAP, BODY_PAD, { 0 }, { FIELD_BOTTOM }, { error("no rows to measure") })

		assertEquals(0, column.total())
		assertEquals(0, column.max())
	}

	@Test
	fun `a column scrolls by whatever its height overruns the field by`() {
		val column = column()
		val naturalHeight = HEADER_HEIGHT + BODY_PAD + column.total()

		assertEquals(naturalHeight - (FIELD_BOTTOM - FIELD_TOP), column.max())
		assertEquals(0, column(fieldBottom = 400).max())
	}

	@Test
	fun `a settings area starts directly under the row it belongs to`() {
		val column = column()
		column.scrollBy(-20)

		assertEquals(BODY_TOP - 20 + ROW_HEIGHT, column.originOf(0) + ROW_HEIGHT)
		assertEquals(BODY_TOP - 20 + 69, column.originOf(2) + ROW_HEIGHT)
	}

	@Test
	fun `revealing the last row leaves the column's bottom pad below it`() {
		val column = column()

		column.reveal(2)

		assertEquals(BODY_PAD, FIELD_BOTTOM - (column.originOf(2) + ROW_EXTENTS.applyAsInt(2)))
	}

	private fun column(fieldBottom: Int = FIELD_BOTTOM): ScrollingStack =
		ScrollingStack(BODY_TOP, NO_GAP, BODY_PAD, { ROW_AREAS.size }, { fieldBottom }, ROW_EXTENTS)

	private fun ScrollingStack.refilterAt(matches: Int) {
		stackCount = matches
		refilter()
	}

	private fun field(count: Int, viewport: Int = 384): ScrollingStack =
		ScrollingStack(MARGIN, GAP, MARGIN, { count }, { viewport }, { COLUMN_WIDTH })

	private fun varyingStack(viewport: Int = VIEWPORT): ScrollingStack {
		stackCount = CATEGORIES
		return ScrollingStack(FIELD_TOP, GAP, MARGIN, { stackCount }, { viewport }, VARYING)
	}

	private companion object {
		const val COLUMN_WIDTH = 118
		const val GAP = 8
		const val MARGIN = 8
		const val CATEGORIES = 17
		const val HEADER_HEIGHT = 18
		const val FIELD_TOP = 70
		const val TALL_SLOT = 2
		const val TALL_HEIGHT = 100
		const val VIEWPORT = 240
		const val NO_GAP = 0
		const val ROW_HEIGHT = 13
		const val BODY_PAD = 6
		const val BODY_TOP = FIELD_TOP + HEADER_HEIGHT
		const val FIELD_BOTTOM = 160
		val VARYING = IntUnaryOperator { index -> if (index == TALL_SLOT) TALL_HEIGHT else HEADER_HEIGHT }
		val ROW_AREAS = intArrayOf(30, 0, 42)
		val ROW_EXTENTS = IntUnaryOperator { index -> ROW_HEIGHT + ROW_AREAS[index] }
	}
}
