package io.github.dzkchen.dhen.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TickClockTest {
	private val dispatcher = ClientThreadDispatcher()
	private val scope = CoroutineScope(SupervisorJob() + dispatcher)

	@AfterEach
	fun stop() {
		scope.cancel()
		dispatcher.drainQueue()
	}

	@Test
	fun `a zero-tick wait resumes on the next tick rather than inline`() {
		var fired = false
		start { delayTicks(0); fired = true }

		assertFalse(fired)

		clientTick()
		assertTrue(fired)
	}

	@Test
	fun `waits resume in due order regardless of the order they were scheduled`() {
		val order = mutableListOf<String>()
		start { delayTicks(3); order += "late" }
		start { delayTicks(1); order += "early" }

		repeat(3) { clientTick() }

		assertEquals(listOf("early", "late"), order)
	}

	@Test
	fun `cancelling the job stops a pending wait`() {
		var fired = false
		val job = start { delayTicks(1); fired = true }
		job.cancel()

		clientTick()

		assertFalse(fired)
	}

	@Test
	fun `a world change cancels every parked wait`() {
		var fired = false
		val soon = start { delayTicks(1); fired = true }
		val later = start { delayTicks(5) }
		assertEquals(2, TickClock.pending)

		TickClock.cancelWaits()
		assertEquals(0, TickClock.pending)
		repeat(2) { clientTick() }

		assertFalse(fired)
		assertTrue(soon.isCancelled)
		assertTrue(later.isCancelled)
	}

	private fun start(block: suspend () -> Unit): Job =
		scope.launch { block() }.also { dispatcher.drainQueue() }

	private fun clientTick() {
		TickClock.clientTicked()
		dispatcher.drainQueue()
	}

}
