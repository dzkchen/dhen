package io.github.dzkchen.dhen.ui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HudLayoutTest {
	@Test
	fun `left anchored element keeps its distance to the left edge at any resolution`() {
		assertEquals(4, HudLayout.place(HudAnchor.TOP_LEFT.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, 4))
		assertEquals(4, HudLayout.place(HudAnchor.TOP_LEFT.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, 4))
	}

	@Test
	fun `right anchored element keeps its distance to the right edge at any resolution`() {
		val small = HudLayout.place(HudAnchor.TOP_RIGHT.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, -4)
		val large = HudLayout.place(HudAnchor.TOP_RIGHT.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, -4)

		assertEquals(SMALL_WIDTH - ELEMENT_WIDTH - 4, small)
		assertEquals(4, SMALL_WIDTH - (small + ELEMENT_WIDTH))
		assertEquals(4, LARGE_WIDTH - (large + ELEMENT_WIDTH))
	}

	@Test
	fun `bottom anchored element keeps its distance to the bottom edge at any resolution`() {
		val small = HudLayout.place(HudAnchor.BOTTOM_LEFT.vertical, SMALL_HEIGHT, ELEMENT_HEIGHT, -6)
		val large = HudLayout.place(HudAnchor.BOTTOM_LEFT.vertical, LARGE_HEIGHT, ELEMENT_HEIGHT, -6)

		assertEquals(6, SMALL_HEIGHT - (small + ELEMENT_HEIGHT))
		assertEquals(6, LARGE_HEIGHT - (large + ELEMENT_HEIGHT))
	}

	@Test
	fun `centered element stays centered at any resolution`() {
		val small = HudLayout.place(HudAnchor.MIDDLE_CENTER.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, 0)
		val large = HudLayout.place(HudAnchor.MIDDLE_CENTER.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, 0)

		assertEquals((SMALL_WIDTH - ELEMENT_WIDTH) / 2, small)
		assertEquals((LARGE_WIDTH - ELEMENT_WIDTH) / 2, large)
		assertEquals(SMALL_WIDTH - (small + ELEMENT_WIDTH), small)
		assertEquals(LARGE_WIDTH - (large + ELEMENT_WIDTH), large)
	}

	@Test
	fun `an offset moves an anchored element by the same pixels at any resolution`() {
		for (anchor in HudAnchor.entries) {
			val small = HudLayout.place(anchor.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, 0)
			val large = HudLayout.place(anchor.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, 0)

			assertEquals(small + 12, HudLayout.place(anchor.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, 12))
			assertEquals(large + 12, HudLayout.place(anchor.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, 12))
		}
	}

	@Test
	fun `scale grows the size anchoring uses`() {
		assertEquals(ELEMENT_WIDTH, HudLayout.scaled(ELEMENT_WIDTH, 1.0f))
		assertEquals(2 * ELEMENT_WIDTH, HudLayout.scaled(ELEMENT_WIDTH, 2.0f))
		assertEquals(14, HudLayout.scaled(9, 1.5f))

		val scaled = HudLayout.scaled(ELEMENT_WIDTH, 2.0f)
		assertEquals(SMALL_WIDTH - scaled, HudLayout.place(HudAnchor.TOP_RIGHT.horizontal, SMALL_WIDTH, scaled, 0))
	}

	@Test
	fun `an offset that would leave the screen is clamped back into it`() {
		assertEquals(
			SMALL_WIDTH - ELEMENT_WIDTH,
			HudLayout.clamp(HudLayout.place(HudAnchor.TOP_LEFT.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, 3000), ELEMENT_WIDTH, SMALL_WIDTH)
		)
		assertEquals(
			0,
			HudLayout.clamp(HudLayout.place(HudAnchor.TOP_LEFT.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, -3000), ELEMENT_WIDTH, SMALL_WIDTH)
		)
	}

	@Test
	fun `an on-screen position is left alone by the clamp`() {
		for (anchor in HudAnchor.entries) {
			val placed = HudLayout.place(anchor.horizontal, LARGE_WIDTH, ELEMENT_WIDTH, 0)
			assertEquals(placed, HudLayout.clamp(placed, ELEMENT_WIDTH, LARGE_WIDTH))
		}
	}

	@Test
	fun `the offset for a position reproduces that position at every anchor`() {
		for (anchor in HudAnchor.entries) {
			for (position in intArrayOf(0, 37, SMALL_WIDTH - ELEMENT_WIDTH)) {
				val offset = HudLayout.offsetFor(anchor.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, position)
				assertEquals(position, HudLayout.place(anchor.horizontal, SMALL_WIDTH, ELEMENT_WIDTH, offset))
			}
		}
	}

	@Test
	fun `an element wider than the screen clamps to the origin`() {
		assertEquals(0, HudLayout.clamp(40, 2 * SMALL_WIDTH, SMALL_WIDTH))
	}

	private companion object {
		const val SMALL_WIDTH = 854
		const val SMALL_HEIGHT = 480
		const val LARGE_WIDTH = 1920
		const val LARGE_HEIGHT = 1080
		const val ELEMENT_WIDTH = 50
		const val ELEMENT_HEIGHT = 9
	}
}
