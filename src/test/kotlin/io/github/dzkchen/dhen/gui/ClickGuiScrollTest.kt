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
}
