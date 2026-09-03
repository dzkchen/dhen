package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import io.github.dzkchen.dhen.util.TickClock
import kotlinx.coroutines.Job
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleTickTest {
	private val dispatcher = ClientThreadDispatcher()
	private val manager = ModuleManager(clientDispatcher = dispatcher)
	private val module = TickedModule()

	init {
		manager.register(module)
		manager.enable(module)
	}

	@AfterEach
	fun stop() {
		manager.disable(module)
		dispatcher.drainQueue()
	}

	@Test
	fun `a twenty-tick delay fires on the twentieth client tick`() {
		var fired = false
		module.after(20) { fired = true }
		dispatcher.drainQueue()

		repeat(19) { clientTick() }
		assertFalse(fired)

		clientTick()
		assertTrue(fired)
	}

	@Test
	fun `disabling the module leaves nothing parked in the shared tick queues`() {
		module.after(500) { }
		module.after(600) { }
		dispatcher.drainQueue()
		assertEquals(2, TickClock.pending)

		manager.disable(module)

		assertEquals(0, TickClock.pending)
	}

	private fun clientTick() {
		TickClock.clientTicked()
		dispatcher.drainQueue()
	}

	private class TickedModule : Module(
		name = "Ticked Module",
		category = Category.DEV,
		description = "Schedules tick-counted work."
	) {
		fun after(ticks: Int, block: () -> Unit): Job? = inTicks(ticks, block)
	}
}
