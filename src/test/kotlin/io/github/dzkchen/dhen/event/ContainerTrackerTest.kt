package io.github.dzkchen.dhen.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContainerTrackerTest {
	private val tracker = ContainerTracker()

	@Test
	fun `a chest is ready once the last container slot arrives`() {
		tracker.opened(WINDOW, CHEST)

		assertEquals(ContainerSignal.NONE, tracker.contentSet(WINDOW, 0))
		for (slot in 0 until CHEST - 1) assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW, slot))

		assertEquals(ContainerSignal.READY, tracker.slotSet(WINDOW, CHEST - 1))
		assertTrue(tracker.ready)
	}

	@Test
	fun `a set-slot in the player inventory means the container part is done`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, 0)
		repeat(9) { tracker.slotSet(WINDOW, it) }

		assertEquals(ContainerSignal.READY, tracker.slotSet(WINDOW, CHEST))
	}

	@Test
	fun `a content packet spanning the whole menu is ready on its own`() {
		tracker.opened(WINDOW, CHEST)

		assertEquals(ContainerSignal.READY, tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS))
	}

	@Test
	fun `a content packet shorter than the container is not ready`() {
		tracker.opened(WINDOW, CHEST)

		assertEquals(ContainerSignal.NONE, tracker.contentSet(WINDOW, CHEST - 1))
		assertFalse(tracker.ready)
	}

	@Test
	fun `a non-generic menu is ready when its last slot arrives`() {
		tracker.opened(WINDOW, HOPPER)

		repeat(HOPPER - 1) { assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW, it)) }

		assertEquals(ContainerSignal.READY, tracker.slotSet(WINDOW, HOPPER - 1))
	}

	@Test
	fun `packets for another window are ignored`() {
		tracker.opened(WINDOW, CHEST)

		assertEquals(ContainerSignal.NONE, tracker.contentSet(WINDOW + 1, CHEST + PLAYER_SLOTS))
		assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW + 1, CHEST - 1))
		assertFalse(tracker.closed(WINDOW + 1))
		assertFalse(tracker.ready)
	}

	@Test
	fun `packets arriving before any menu opened raise nothing`() {
		assertEquals(ContainerSignal.NONE, tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS))
		assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW, 0))
		assertFalse(tracker.closed(WINDOW))
	}

	@Test
	fun `re-sent slots after ready coalesce into one update per tick`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS)

		assertEquals(ContainerSignal.UPDATED, tracker.slotSet(WINDOW, 4))
		assertEquals(ContainerSignal.UPDATED, tracker.slotSet(WINDOW, 5))
		assertEquals(ContainerSignal.UPDATED, tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS))

		assertTrue(tracker.flushUpdate())
		assertFalse(tracker.flushUpdate())
	}

	@Test
	fun `a tick with no re-sent slot raises no update`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS)

		assertFalse(tracker.flushUpdate())
	}

	@Test
	fun `closing the tracked window stops the tracking`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS)
		tracker.slotSet(WINDOW, 4)

		assertTrue(tracker.closed(WINDOW))
		assertFalse(tracker.tracking)
		assertFalse(tracker.ready)
		assertFalse(tracker.flushUpdate())
		assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW, 4))
	}

	@Test
	fun `a menu opening over another starts from nothing`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS)
		tracker.slotSet(WINDOW, 4)

		tracker.opened(WINDOW + 1, HOPPER)

		assertFalse(tracker.ready)
		assertFalse(tracker.flushUpdate())
		assertEquals(HOPPER, tracker.slotCount)
		assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW + 1, 0))
		assertEquals(ContainerSignal.READY, tracker.slotSet(WINDOW + 1, HOPPER - 1))
	}

	@Test
	fun `the carried cursor slot is not a container slot`() {
		tracker.opened(WINDOW, CHEST)

		assertEquals(ContainerSignal.NONE, tracker.slotSet(WINDOW, -1))
		assertFalse(tracker.ready)
	}

	@Test
	fun `a reset drops everything the tracker knew`() {
		tracker.opened(WINDOW, CHEST)
		tracker.contentSet(WINDOW, CHEST + PLAYER_SLOTS)
		tracker.slotSet(WINDOW, 4)

		tracker.reset()

		assertFalse(tracker.tracking)
		assertEquals(ContainerTracker.NO_WINDOW, tracker.windowId)
		assertEquals(0, tracker.slotCount)
		assertFalse(tracker.flushUpdate())
	}

	private companion object {
		private const val WINDOW = 7
		private const val CHEST = 54
		private const val HOPPER = 5
		private const val PLAYER_SLOTS = 36
	}
}
