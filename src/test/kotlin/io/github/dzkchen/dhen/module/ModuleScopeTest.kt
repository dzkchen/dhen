package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleScopeTest {
	@Test
	fun `scope jobs are cancelled when the module is disabled`() {
		val dispatcher = ClientThreadDispatcher()
		val manager = ModuleManager(clientDispatcher = dispatcher)
		val module = ScopedModule()
		manager.register(module)
		manager.enable(module)

		val started = CompletableDeferred<Unit>()
		val job = module.start(started)!!
		dispatcher.drainQueue()

		assertTrue(started.isCompleted)
		assertTrue(job.isActive)

		manager.disable(module)
		assertTrue(job.isCancelled)
	}

	@Test
	fun `launch is a no-op while the module is disabled`() {
		val module = ScopedModule()
		ModuleManager().register(module)

		assertNull(module.start(CompletableDeferred()))
	}

	@Test
	fun `re-enabling yields a fresh working scope`() {
		val dispatcher = ClientThreadDispatcher()
		val manager = ModuleManager(clientDispatcher = dispatcher)
		val module = ScopedModule()
		manager.register(module)

		manager.enable(module)
		val first = module.start(CompletableDeferred())!!
		manager.disable(module)
		assertTrue(first.isCancelled)

		manager.enable(module)
		val started = CompletableDeferred<Unit>()
		val second = module.start(started)!!
		dispatcher.drainQueue()

		assertFalse(first === second)
		assertTrue(started.isCompleted)
		assertTrue(second.isActive)

		manager.disable(module)
		assertTrue(second.isCancelled)
	}

	@Test
	fun `a queued launch never runs its body once the module is disabled`() {
		val dispatcher = ClientThreadDispatcher()
		val manager = ModuleManager(clientDispatcher = dispatcher)
		val module = ScopedModule()
		manager.register(module)
		manager.enable(module)

		val started = CompletableDeferred<Unit>()
		module.start(started)
		manager.disable(module)
		dispatcher.drainQueue()

		assertFalse(started.isCompleted)
	}

	@Test
	fun `repeated coroutine failures auto-disable the module`() {
		val dispatcher = ClientThreadDispatcher()
		val manager = ModuleManager(clientDispatcher = dispatcher)
		val module = ScopedModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) {
			module.fail()
			dispatcher.drainQueue()
		}

		assertFalse(module.enabled)
	}

	private class ScopedModule : Module(
		name = "Scoped Module",
		category = Category.DEV,
		description = "Launches a suspending job."
	) {
		fun start(started: CompletableDeferred<Unit>): Job? =
			launch {
				started.complete(Unit)
				awaitCancellation()
			}

		fun fail(): Job? =
			launch { throw IllegalStateException("boom") }
	}
}
