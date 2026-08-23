package io.github.dzkchen.dhen.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RequirementPumpTest {
	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val pump = RequirementPump()

	private var alive = true
	private var starts = 0
	private var job: Job? = null

	@Test
	fun `a requirement taken after its owner went away is refused rather than counted`() {
		alive = false

		val handle = pump.require({ alive }, ::start)
		handle.unsubscribe()

		assertEquals(0, pump.count)
		assertEquals(0, starts)
		assertFalse(pump.polling)
	}

	@Test
	fun `one pump serves every requirement and stops with the last release`() {
		val first = pump.require({ alive }, ::start)
		val second = pump.require({ alive }, ::start)

		assertEquals(2, pump.count)
		assertEquals(1, starts)
		assertTrue(pump.polling)

		first.unsubscribe()
		assertTrue(pump.polling)

		second.unsubscribe()
		assertEquals(0, pump.count)
		assertFalse(pump.polling)

		pump.require({ alive }, ::start)
		assertEquals(2, starts)
		assertTrue(pump.polling)
	}

	@Test
	fun `a pump that died on its own is started again by the next change to the count`() {
		val first = pump.require({ alive }, ::start)
		pump.require({ alive }, ::start)
		assertEquals(1, starts)

		job?.cancel()
		assertFalse(pump.polling)

		first.unsubscribe()

		assertEquals(1, pump.count)
		assertEquals(2, starts)
		assertTrue(pump.polling)
	}

	@Test
	fun `resetting stops the pump and forgets every requirement`() {
		pump.require({ alive }, ::start)

		pump.reset()

		assertEquals(0, pump.count)
		assertFalse(pump.polling)
	}

	private fun start(): Job {
		starts++
		return scope.launch { awaitCancellation() }.also { job = it }
	}
}
