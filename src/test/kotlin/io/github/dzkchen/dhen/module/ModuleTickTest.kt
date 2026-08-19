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
	fun `a three-server-tick delay fires on the third server tick`() {
		var fired = false
		module.afterServer(3) { fired = true }
		dispatcher.drainQueue()

		repeat(2) { serverTick() }
		assertFalse(fired)

		serverTick()
		assertTrue(fired)
	}

	@Test
	fun `disabling the module cancels a repeating task`() {
		var fired = 0
		val job = module.repeatedly(1) { fired++ }!!
		dispatcher.drainQueue()

		clientTick()
		assertEquals(1, fired)

		manager.disable(module)
		assertTrue(job.isCancelled)

		repeat(3) { clientTick() }
		assertEquals(1, fired)
	}

	@Test
	fun `disabling the module leaves nothing parked in the shared tick queues`() {
		module.repeatedly(1) { }
		module.after(500) { }
		module.afterServer(500) { }
		dispatcher.drainQueue()
		assertEquals(3, TickClock.pending)

		manager.disable(module)

		assertEquals(0, TickClock.pending)
	}

	@Test
	fun `a repeating task survives a throwing run and keeps counting errors`() {
		var runs = 0
		module.repeatedly(1) { runs++; error("boom") }
		dispatcher.drainQueue()

		repeat(3) { clientTick() }

		assertEquals(3, runs)
		assertEquals(3, module.errorCount)
	}

	private fun clientTick() {
		TickClock.clientTicked()
		dispatcher.drainQueue()
	}

	private fun serverTick() {
		TickClock.serverTicked()
		dispatcher.drainQueue()
	}

	private class TickedModule : Module(
		name = "Ticked Module",
		category = Category.DEV,
		description = "Schedules tick-counted work."
	) {
		fun after(ticks: Int, block: () -> Unit): Job? = inTicks(ticks, block)

		fun afterServer(ticks: Int, block: () -> Unit): Job? = inServerTicks(ticks, block)

		fun repeatedly(ticks: Int, block: () -> Unit): Job? = everyTicks(ticks, block)
	}
}
