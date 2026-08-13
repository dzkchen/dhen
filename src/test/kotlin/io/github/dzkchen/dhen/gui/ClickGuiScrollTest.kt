package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClickGuiScrollTest {
	@Test
	fun `no scroll when content fits the viewport`() {
		assertEquals(0, ClickGuiScroll.maxScroll(contentBottom = 200, viewportHeight = 260, margin = 8))
	}

	@Test
	fun `no scroll when content bottom exactly meets the viewport`() {
		assertEquals(0, ClickGuiScroll.maxScroll(contentBottom = 252, viewportHeight = 260, margin = 8))
	}

	@Test
	fun `overflow becomes scrollable with a trailing margin`() {
		assertEquals(148, ClickGuiScroll.maxScroll(contentBottom = 400, viewportHeight = 260, margin = 8))
	}

	@Test
	fun `offset is clamped into the scrollable range`() {
		assertEquals(0, ClickGuiScroll.clampOffset(-30, maxScroll = 148))
		assertEquals(148, ClickGuiScroll.clampOffset(500, maxScroll = 148))
		assertEquals(70, ClickGuiScroll.clampOffset(70, maxScroll = 148))
	}

	@Test
	fun `a thumb covers as much of its track as the viewport covers the content`() {
		assertEquals(50, ClickGuiScroll.thumbHeight(trackHeight = 100, viewportHeight = 100, maxScroll = 100, minimum = 12))
		assertEquals(25, ClickGuiScroll.thumbHeight(trackHeight = 100, viewportHeight = 100, maxScroll = 300, minimum = 12))
	}

	@Test
	fun `a thumb never shrinks past its minimum, nor past the track itself`() {
		assertEquals(12, ClickGuiScroll.thumbHeight(trackHeight = 100, viewportHeight = 100, maxScroll = 3900, minimum = 12))
		assertEquals(8, ClickGuiScroll.thumbHeight(trackHeight = 8, viewportHeight = 100, maxScroll = 3900, minimum = 12))
	}

	@Test
	fun `a thumb travels the leftover track in step with the offset`() {
		assertEquals(20, ClickGuiScroll.thumbTop(trackTop = 20, trackHeight = 100, thumbHeight = 40, offset = 0, maxScroll = 120))
		assertEquals(50, ClickGuiScroll.thumbTop(trackTop = 20, trackHeight = 100, thumbHeight = 40, offset = 60, maxScroll = 120))
		assertEquals(80, ClickGuiScroll.thumbTop(trackTop = 20, trackHeight = 100, thumbHeight = 40, offset = 120, maxScroll = 120))
	}

	@Test
	fun `a thumb with nowhere to travel stays at the top of its track`() {
		assertEquals(20, ClickGuiScroll.thumbTop(trackTop = 20, trackHeight = 100, thumbHeight = 40, offset = 60, maxScroll = 0))
		assertEquals(20, ClickGuiScroll.thumbTop(trackTop = 20, trackHeight = 40, thumbHeight = 40, offset = 60, maxScroll = 120))
	}

	@Test
	fun `offset collapses to zero when nothing scrolls`() {
		assertEquals(0, ClickGuiScroll.clampOffset(70, maxScroll = 0))
	}

	@Test
	fun `a field that collapses stashes the offset and gives it back`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		assertEquals(ClickGuiScroll.TOP, field.offset)
		assertEquals(120, field.stashed)

		field.refilter(maxScroll = 148)
		assertEquals(120, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	@Test
	fun `the stash survives further edits while the field stays collapsed`() {
		val field = scrolledTo(120)

		repeat(3) { field.refilter(maxScroll = 0) }
		assertEquals(ClickGuiScroll.TOP, field.offset)
		assertEquals(120, field.stashed)

		field.refilter(maxScroll = 148)
		assertEquals(120, field.offset)
	}

	@Test
	fun `a partial match on the way back does not truncate the stash`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 20)
		assertEquals(20, field.offset)
		assertEquals(120, field.stashed)

		field.refilter(maxScroll = 148)
		assertEquals(120, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	@Test
	fun `a restored offset is clamped to the range it comes back into`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 40)

		assertEquals(40, field.offset)
	}

	@Test
	fun `an explicit scroll takes over from the stash`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 20)
		field.scrollTo(5, maxScroll = 20)
		field.refilter(maxScroll = 148)

		assertEquals(5, field.offset)
	}

	@Test
	fun `nothing is stashed or restored while the field still scrolls`() {
		val field = scrolledTo(70)

		field.refilter(maxScroll = 148)
		assertEquals(70, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)

		field.refilter(maxScroll = 40)
		assertEquals(40, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	@Test
	fun `a field collapsing from the top has nothing to stash`() {
		val field = ScrollState()

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 148)

		assertEquals(ClickGuiScroll.TOP, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	@Test
	fun `collapsing again keeps the offset stashed before the first collapse`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 20)
		assertEquals(20, field.offset)

		field.refilter(maxScroll = 0)
		assertEquals(120, field.stashed)

		field.refilter(maxScroll = 148)
		assertEquals(120, field.offset)
	}

	@Test
	fun `a row already inside the window is not scrolled to`() {
		assertEquals(PARKED, reveal(spanStart = 60, extent = 13))
		assertEquals(PARKED, reveal(spanStart = 40, extent = 13))
		assertEquals(PARKED, reveal(spanStart = 127, extent = 13))
	}

	@Test
	fun `a row above the window scrolls its top flush with the window`() {
		assertEquals(20, reveal(spanStart = 20, extent = 13))
		assertEquals(0, reveal(spanStart = 0, extent = 13))
	}

	@Test
	fun `a row below the window scrolls just far enough to show its bottom`() {
		assertEquals(41, reveal(spanStart = 128, extent = 13))
		assertEquals(148, reveal(spanStart = 400, extent = 13))
	}

	@Test
	fun `a row taller than the window is shown from its top`() {
		assertEquals(60, reveal(spanStart = 60, extent = 200))
	}

	@Test
	fun `focusing a row after a restore keeps the row on screen`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 148)
		field.reveal(spanStart = 12, extent = 13, window = 100, maxScroll = 148)

		assertEquals(12, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	@Test
	fun `a reveal leaves the stash for the search to give back`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.refilter(maxScroll = 20)
		field.reveal(spanStart = 0, extent = 13, window = 100, maxScroll = 20)

		assertEquals(120, field.stashed)

		field.refilter(maxScroll = 148)
		assertEquals(120, field.offset)
	}

	@Test
	fun `a reclamp from a direct user action drops the stash`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		field.reclamp(maxScroll = 148)
		field.refilter(maxScroll = 148)

		assertEquals(ClickGuiScroll.TOP, field.offset)
		assertEquals(ClickGuiScroll.TOP, field.stashed)
	}

	private fun scrolledTo(offset: Int) = ScrollState().apply { scrollTo(offset, maxScroll = offset) }

	private fun reveal(spanStart: Int, extent: Int): Int =
		ClickGuiScroll.reveal(PARKED, spanStart, extent, window = 100, maxScroll = 148)

	private companion object {
		const val PARKED = 40
	}
}
