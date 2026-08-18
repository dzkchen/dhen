package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.ui.hud.FixedHudElement
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.forEachHudElement
import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class ModuleUnregisterTest {
	@Test
	fun `register unregister cycles leave no subscriptions and no keybind bindings`() {
		val manager = ModuleManager()
		val module = SampleModule()

		repeat(25) {
			manager.register(module)
			manager.unregister(module)
		}
		module.setEnabled(true)
		manager.eventBus.type<TestEvent>().dispatch(TestEvent())
		manager.eventBus.type<KeyInputEvent>().dispatch(KeyInputEvent(TOGGLE_KEY, InputAction.PRESS, 0, 0))

		assertEquals(0, module.calls)
		assertEquals(0, module.activations)
		assertEquals(0, manager.keybindCount(module))
	}

	@Test
	fun `an unregistered module receives no events and its keybind no longer fires`() {
		val manager = ModuleManager()
		val module = SampleModule()
		manager.register(module)
		manager.enable(module)
		manager.eventBus.type<TestEvent>().dispatch(TestEvent())
		manager.eventBus.type<KeyInputEvent>().dispatch(KeyInputEvent(TOGGLE_KEY, InputAction.PRESS, 0, 0))

		manager.unregister(module)
		module.setEnabled(true)
		manager.eventBus.type<TestEvent>().dispatch(TestEvent())
		manager.eventBus.type<KeyInputEvent>().dispatch(KeyInputEvent(TOGGLE_KEY, InputAction.PRESS, 0, 0))

		assertEquals(1, module.calls)
		assertEquals(1, module.activations)
		assertNull(manager["Sample"])
		assertTrue(manager.modules.isEmpty())
		assertFalse(manager.categories.containsKey(Category.QOL))
	}

	@Test
	fun `unregistering an enabled module cancels its coroutine scope`() {
		val dispatcher = ClientThreadDispatcher()
		val manager = ModuleManager(clientDispatcher = dispatcher)
		val module = SampleModule()
		manager.register(module)
		manager.enable(module)
		val job = module.start()!!
		dispatcher.drainQueue()

		manager.unregister(module)

		assertFalse(module.enabled)
		assertTrue(job.isCancelled)
	}

	@Test
	fun `an unregistered module leaves the HUD element list`() {
		val manager = ModuleManager()
		val module = SampleModule()
		manager.register(module)

		manager.unregister(module)

		assertEquals(emptyList<HudElement>(), hudElements(manager))
	}

	@Test
	fun `a module can be registered again after unregistering`() {
		val manager = ModuleManager(nanoClock = { 0L })
		val module = SampleModule()
		manager.register(module)
		manager.enable(module)
		manager.eventBus.type<TestEvent>().dispatch(TestEvent())
		manager.unregister(module)

		assertSame(module, manager.register(module))
		manager.enable(module)
		manager.eventBus.type<TestEvent>().dispatch(TestEvent())
		manager.eventBus.type<KeyInputEvent>().dispatch(KeyInputEvent(TOGGLE_KEY, InputAction.PRESS, 0, 0))

		assertEquals(2, module.calls)
		assertEquals(1, module.activations)
		assertEquals(1, manager.keybindCount(module))
		assertEquals(listOf(module.element), hudElements(manager))
		assertEquals(1, module.handlerTimings.single().snapshot().totalInvocations)
	}

	@Test
	fun `unregistering a module this manager does not own is rejected`() {
		val manager = ModuleManager()
		val module = SampleModule()
		val impostor = SampleModule()
		manager.register(module)

		assertThrows(NoSuchElementException::class.java) { manager.unregister(SampleModule(name = "Absent")) }
		assertThrows(IllegalArgumentException::class.java) { manager.unregister(impostor) }
		assertSame(module, manager["Sample"])
	}

	private fun hudElements(manager: ModuleManager): List<HudElement> =
		buildList { manager.forEachHudElement { add(it) } }

	private class TestEvent : Event

	private class SampleModule(name: String = "Sample") : Module(name, Category.QOL, "Counts test events.") {
		var calls = 0
			private set
		var activations = 0
			private set

		val element = hud(FixedHudElement("Sample Readout"))

		@Suppress("unused")
		private val keybind by KeybindSetting("Action", TOGGLE_KEY).onPress { activations++ }

		init {
			on<TestEvent> { calls++ }
		}

		fun start(): Job? =
			launch { awaitCancellation() }
	}

	private companion object {
		const val TOGGLE_KEY = GLFW.GLFW_KEY_J
	}
}
