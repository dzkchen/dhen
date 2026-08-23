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
	fun `a client wait resumes on the nth tick after it was scheduled`() {
		var fired = false
		start { delayTicks(2); fired = true }

		clientTick()
		assertFalse(fired)

		clientTick()
		assertTrue(fired)
	}

	@Test
	fun `a zero-tick wait and a bare tick await both resume on the next tick rather than inline`() {
		var fired = false
		var awaited = false
		start { delayTicks(0); fired = true }
		start { awaitTick(); awaited = true }

		assertFalse(fired)
		assertFalse(awaited)

		clientTick()
		assertTrue(fired)
		assertTrue(awaited)
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
	fun `a repeating wait fires once per period`() {
		var fired = 0
		scope.launch { repeatTicks(2) { fired++ } }
		dispatcher.drainQueue()

		repeat(4) { clientTick() }

		assertEquals(2, fired)
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
	fun `server waits count server ticks and ignore client ticks`() {
		var fired = false
		start { delayServerTicks(2); fired = true }

		repeat(5) { clientTick() }
		assertFalse(fired)

		serverTick()
		assertFalse(fired)

		serverTick()
		assertTrue(fired)
	}

	@Test
	fun `a world change cancels the world-scoped waits and keeps the ones that survive it`() {
		var oneShot = false
		var repeated = 0
		val job = start { delayTicks(1); oneShot = true }
		scope.launch { repeatTicks(1) { repeated++ } }
		dispatcher.drainQueue()

		TickClock.cancelWorldScopedWaits()
		repeat(2) { clientTick() }

		assertFalse(oneShot)
		assertTrue(job.isCancelled)
		assertEquals(2, repeated)
	}

	@Test
	fun `a shutdown cancels every parked wait including the surviving ones`() {
		val oneShot = start { delayTicks(5) }
		val repeating = scope.launch { repeatTicks(5) { } }
		dispatcher.drainQueue()
		assertEquals(2, TickClock.pending)

		TickClock.shutdown()
		assertEquals(0, TickClock.pending)

		dispatcher.drainQueue()
		assertTrue(oneShot.isCancelled)
		assertTrue(repeating.isCancelled)
	}

	private fun start(block: suspend () -> Unit): Job =
		scope.launch { block() }.also { dispatcher.drainQueue() }

	private fun clientTick() {
		TickClock.clientTicked()
		dispatcher.drainQueue()
	}

	private fun serverTick() {
		TickClock.serverTicked()
		dispatcher.drainQueue()
	}
}
