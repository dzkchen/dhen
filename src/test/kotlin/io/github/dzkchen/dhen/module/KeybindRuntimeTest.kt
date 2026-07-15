package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class KeybindRuntimeTest {
	@Test
	fun `keybind activates only on press while owner is enabled`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		val keys = bus.type<KeyInputEvent>()

		keys.dispatch(KeyInputEvent(GLFW.GLFW_KEY_K, InputAction.PRESS))
		manager.enable(module)
		keys.dispatch(KeyInputEvent(GLFW.GLFW_KEY_K, InputAction.RELEASE))
		keys.dispatch(KeyInputEvent(GLFW.GLFW_KEY_K, InputAction.PRESS))

		assertEquals(1, module.activations)
	}

	@Test
	fun `live rebind routes keyboard and mouse ranges independently`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		manager.enable(module)

		module.rebind(GLFW.GLFW_MOUSE_BUTTON_4)
		bus.type<KeyInputEvent>().dispatch(KeyInputEvent(GLFW.GLFW_MOUSE_BUTTON_4, InputAction.PRESS))
		bus.type<MouseInputEvent>().dispatch(MouseInputEvent(GLFW.GLFW_MOUSE_BUTTON_4, InputAction.PRESS))
		module.rebind(GLFW.GLFW_KEY_L)
		bus.type<MouseInputEvent>().dispatch(MouseInputEvent(GLFW.GLFW_KEY_L, InputAction.PRESS))
		bus.type<KeyInputEvent>().dispatch(KeyInputEvent(GLFW.GLFW_KEY_L, InputAction.PRESS))

		assertEquals(2, module.activations)
	}

	@Test
	fun `keybind callback can toggle its owner`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = ToggleModule()
		manager.register(module)
		manager.enable(module)

		bus.type<KeyInputEvent>().dispatch(KeyInputEvent(GLFW.GLFW_KEY_F8, InputAction.PRESS))

		assertFalse(module.enabled)
		bus.type<KeyInputEvent>().dispatch(KeyInputEvent(GLFW.GLFW_KEY_F8, InputAction.PRESS))
		assertFalse(module.enabled)
	}

	@Test
	fun `keybind callback failures use module error isolation`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = ThrowingKeybindModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) {
			bus.type<KeyInputEvent>().dispatch(KeyInputEvent(GLFW.GLFW_KEY_K, InputAction.PRESS))
		}

		assertFalse(module.enabled)
		assertEquals(Module.ERROR_THRESHOLD, module.errorCount)
	}

	private class KeybindModule : Module(
		name = "Keybind Module",
		category = Category.QOL,
		description = "Tests keybind callbacks."
	) {
		private val setting = KeybindSetting("Action", GLFW.GLFW_KEY_K).onPress { activations++ }

		@Suppress("unused")
		private val keybind by setting

		var activations = 0
			private set

		fun rebind(key: Int) {
			setting.value = key
		}
	}

	private class ToggleModule : Module(
		name = "Toggle Module",
		category = Category.QOL,
		description = "Tests module toggling."
	) {
		@Suppress("unused")
		private val keybind by KeybindSetting("Toggle", GLFW.GLFW_KEY_F8).onPress(::toggle)
	}

	private class ThrowingKeybindModule : Module(
		name = "Throwing Keybind Module",
		category = Category.QOL,
		description = "Tests error isolation."
	) {
		@Suppress("unused")
		private val keybind by KeybindSetting("Throw", GLFW.GLFW_KEY_K).onPress {
			error("boom")
		}
	}
}
