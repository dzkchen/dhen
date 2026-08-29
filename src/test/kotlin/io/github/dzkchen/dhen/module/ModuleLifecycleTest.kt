package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.EventBus
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Test

class ModuleLifecycleTest {
	@Test
	fun `enable and disable each call their hook once`() {
		val manager = ModuleManager(EventBus())
		val module = LifecycleModule()
		manager.register(module)

		manager.enable(module)
		manager.disable(module)

		assertEquals(listOf("enabled", "disabled"), module.calls)
	}

	@Test
	fun `hooks see the module already in its new state`() {
		val manager = ModuleManager(EventBus())
		val module = LifecycleModule()
		manager.register(module)

		manager.enable(module)

		assertEquals(listOf(true), module.observed)

		manager.disable(module)

		assertEquals(listOf(true, false), module.observed)
	}

	@Test
	fun `a repeated request of the same state calls no hook`() {
		val manager = ModuleManager(EventBus())
		val module = LifecycleModule()
		manager.register(module)

		manager.disable(module)
		manager.enable(module)
		manager.enable(module)

		assertEquals(listOf("enabled"), module.calls)
	}

	@Test
	fun `unregistering an enabled module runs its disable hook before unbinding`() {
		val manager = ModuleManager(EventBus())
		val module = LifecycleModule()
		manager.register(module)
		manager.enable(module)

		manager.unregister(module)

		assertEquals(listOf("enabled", "disabled"), module.calls)
		assertFalse(module.enabled)
	}

	@Test
	fun `a throwing enable hook is isolated and counted like a handler error`() {
		val notices = mutableListOf<String>()
		val manager = ModuleManager(EventBus(), { _, message, _ -> notices += message }, { 0L })
		val module = ThrowingLifecycleModule()
		manager.register(module)

		assertDoesNotThrow { manager.enable(module) }

		assertTrue(module.enabled)
		assertEquals(1, module.errorCount)
		assertEquals(1, notices.size)
	}

	@Test
	fun `a hook that toggles its own module settles without recursing forever`() {
		val manager = ModuleManager(EventBus())
		val module = SelfDisablingModule()
		manager.register(module)

		assertDoesNotThrow { manager.enable(module) }

		assertFalse(module.enabled)
		assertEquals(listOf("enabled", "disabled"), module.calls)
	}

	@Test
	fun `a disable hook cannot launch, because the module scope is already cancelled`() {
		val manager = ModuleManager(EventBus())
		val module = LaunchingModule()
		manager.register(module)

		manager.enable(module)

		assertNotNull(module.enableJob)

		manager.disable(module)

		assertNull(module.disableJob)
		assertEquals(0, module.errorCount)
	}

	private class LaunchingModule : Module(
		name = "Launching Module",
		category = Category.DEV,
		description = "Launches from both lifecycle hooks."
	) {
		var enableJob: Job? = null
			private set
		var disableJob: Job? = null
			private set

		override fun onEnabled() {
			enableJob = launch { }
		}

		override fun onDisabled() {
			disableJob = launch { }
		}
	}

	private class LifecycleModule : Module(
		name = "Lifecycle Module",
		category = Category.DEV,
		description = "Records its lifecycle hooks."
	) {
		val calls = mutableListOf<String>()
		val observed = mutableListOf<Boolean>()

		override fun onEnabled() {
			calls += "enabled"
			observed += enabled
		}

		override fun onDisabled() {
			calls += "disabled"
			observed += enabled
		}
	}

	private class ThrowingLifecycleModule : Module(
		name = "Throwing Lifecycle Module",
		category = Category.DEV,
		description = "Throws from its enable hook."
	) {
		override fun onEnabled() {
			throw RuntimeException("boom")
		}
	}

	private class SelfDisablingModule : Module(
		name = "Self Disabling Module",
		category = Category.DEV,
		description = "Turns itself off from its own enable hook."
	) {
		val calls = mutableListOf<String>()

		override fun onEnabled() {
			calls += "enabled"
			setEnabled(false)
		}

		override fun onDisabled() {
			calls += "disabled"
		}
	}
}
