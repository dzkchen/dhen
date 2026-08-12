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
	fun `focusing a row after a restore keeps the row on screen`() {
		val field = scrolledTo(120)

		field.refilter(maxScroll = 0)
		// The order ClickGuiScreen.applySearch runs in: refilter, then focus the matched row.
		field.refilter(maxScroll = 148)
		field.settle(target = 12, maxScroll = 148)

		assertEquals(12, field.offset)
		// The offset alone would pass for any stash/restore pair, since settling overwrites it.
		// The consumed stash is what proves the restore ran and did not re-stash the 120.
		assertEquals(ClickGuiScroll.TOP, field.stashed)
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

	/** A state already parked at [offset], as a user scroll through that much content would leave it. */
	private fun scrolledTo(offset: Int) = ScrollState().apply { scrollTo(offset, maxScroll = offset) }
}
