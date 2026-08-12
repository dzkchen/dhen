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
		val stack = accordion()
		stack.scrollBy(-90)

		for (slot in intArrayOf(0, OPENED_SLOT, CATEGORIES - 1)) {
			assertEquals(slot, stack.slotAt(stack.originOf(slot)))
		}
	}

	@Test
	fun `an unscrolled stack starts at the top of the field`() {
		assertEquals(FIELD_TOP, accordion().originOf(0))
	}

	@Test
	fun `a stack that fits its viewport refuses to scroll`() {
		val stack = accordion(viewport = 900)

		assertEquals(0, stack.max())
		assertFalse(stack.scrollBy(-90))
		assertEquals(0, stack.offset)
	}

	@Test
	fun `an entry already on screen is not scrolled to`() {
		val stack = accordion()
		stack.scrollBy(-90)

		stack.reveal(3)

		assertEquals(90, stack.offset)
	}

	@Test
	fun `revealing an entry above the window scrolls its top into view`() {
		val stack = accordion()
		stack.scrollBy(-90)

		stack.reveal(0)

		assertEquals(0, stack.offset)
	}

	@Test
	fun `revealing an entry below the window scrolls exactly far enough`() {
		val stack = accordion()

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
			IntUnaryOperator { index -> if (index == 1) 400 else HEADER_HEIGHT }
		)

		stack.reveal(1)

		assertEquals(HEADER_HEIGHT + GAP, stack.offset)
	}

	@Test
	fun `a reveal keeps the offset the search is holding for a widening query`() {
		val stack = accordion()
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
		val stack = accordion()
		stack.scrollBy(-90)
		stack.refilterAt(1)
		stack.refilterAt(6)

		stack.scrollBy(1000)
		assertEquals(0, stack.offset)

		stack.refilterAt(CATEGORIES)
		assertEquals(0, stack.offset)
	}

	@Test
	fun `rewinding parks the idle axis back at the top`() {
		val stack = accordion()
		stack.scrollBy(-90)

		stack.rewind()

		assertEquals(0, stack.offset)
	}

	private fun ScrollingStack.refilterAt(matches: Int) {
		stackCount = matches
		refilter()
	}

	private fun field(count: Int, viewport: Int = 384): ScrollingStack =
		ScrollingStack(MARGIN, GAP, MARGIN, { count }, { viewport }, IntUnaryOperator { COLUMN_WIDTH })

	private fun accordion(viewport: Int = VIEWPORT): ScrollingStack {
		stackCount = CATEGORIES
		return ScrollingStack(FIELD_TOP, GAP, MARGIN, { stackCount }, { viewport }, ACCORDION)
	}

	private companion object {
		const val COLUMN_WIDTH = 118
		const val GAP = 8
		const val MARGIN = 8
		const val CATEGORIES = 17
		const val HEADER_HEIGHT = 18
		const val FIELD_TOP = 70
		const val OPENED_SLOT = 2
		const val OPENED_HEIGHT = 100
		const val VIEWPORT = 240
		val ACCORDION = IntUnaryOperator { index -> if (index == OPENED_SLOT) OPENED_HEIGHT else HEADER_HEIGHT }
	}
}
