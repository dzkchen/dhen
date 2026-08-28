package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.uninitialized
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.input.TextInputTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.lwjgl.glfw.GLFW

class KeybindRuntimeTest {
	@Test
	fun `keybind activates only on press while owner is enabled`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		val keys = bus.type<KeyInputEvent>()

		keys.dispatch(key(GLFW.GLFW_KEY_K))
		manager.enable(module)
		keys.dispatch(key(GLFW.GLFW_KEY_K, InputAction.RELEASE))
		keys.dispatch(key(GLFW.GLFW_KEY_K))

		assertEquals(1, module.activations)
	}

	@Test
	fun `a held key repeating does not activate its binding again`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		manager.enable(module)
		val keys = bus.type<KeyInputEvent>()

		keys.dispatch(key(GLFW.GLFW_KEY_K))
		repeat(5) { keys.dispatch(key(GLFW.GLFW_KEY_K, InputAction.REPEAT)) }
		keys.dispatch(key(GLFW.GLFW_KEY_K, InputAction.RELEASE))

		assertEquals(1, module.activations)
	}

	@TestFactory
	fun `every screen policy decides each screen state`() = screenCases.flatMap { screen ->
		KeybindScreenPolicy.entries.map { policy ->
			dynamicTest("$policy with ${screen.name}") {
				val bus = EventBus()
				val manager = ModuleManager(bus, currentScreen = screen.screen)
				val module = KeybindModule(policy)
				manager.register(module)
				manager.enable(module)

				bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_K))

				assertEquals(if (policy in screen.allowed) 1 else 0, module.activations)
			}
		}
	}

	@Test
	fun `live rebind routes keyboard and mouse ranges independently`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		manager.enable(module)

		module.rebind(GLFW.GLFW_MOUSE_BUTTON_4)
		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_MOUSE_BUTTON_4))
		bus.type<MouseInputEvent>().dispatch(MouseInputEvent(GLFW.GLFW_MOUSE_BUTTON_4, InputAction.PRESS, 0))
		module.rebind(GLFW.GLFW_KEY_L)
		bus.type<MouseInputEvent>().dispatch(MouseInputEvent(GLFW.GLFW_KEY_L, InputAction.PRESS, 0))
		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_L))

		assertEquals(2, module.activations)
	}

	@Test
	fun `a toggle keybind turns its owner back on after turning it off`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = ToggleModule()
		manager.register(module)
		manager.enable(module)

		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_F8))

		assertFalse(module.enabled)

		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_F8))

		assertTrue(module.enabled)
	}

	@Test
	fun `an ordinary keybind still stays silent while its owner is disabled`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = KeybindModule()
		manager.register(module)
		manager.enable(module)
		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_K))
		manager.disable(module)

		bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_K))

		assertEquals(1, module.activations)
	}

	@Test
	fun `keybind callback failures use module error isolation`() {
		val bus = EventBus()
		val manager = ModuleManager(bus)
		val module = ThrowingKeybindModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) {
			bus.type<KeyInputEvent>().dispatch(key(GLFW.GLFW_KEY_K))
		}

		assertFalse(module.enabled)
		assertEquals(Module.ERROR_THRESHOLD, module.errorCount)
	}

	private fun key(code: Int, action: InputAction = InputAction.PRESS) = KeyInputEvent(code, action, 0, 0)

	private class KeybindModule(policy: KeybindScreenPolicy = KeybindScreenPolicy.NO_SCREEN) : Module(
		name = "Keybind Module",
		category = Category.QOL,
		description = "Tests keybind callbacks."
	) {
		private val setting = KeybindSetting("Action", GLFW.GLFW_KEY_K, screenPolicy = policy).onPress { activations++ }

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
		private val keybind by KeybindSetting("Toggle", GLFW.GLFW_KEY_F8).onPress(::toggle).evenWhileDisabled()
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

	private class TestScreen(
		override val textInputFocused: Boolean
	) : Screen(uninitialized<Minecraft>(), uninitialized<Font>(), Component.empty()), TextInputTarget

	private data class ScreenCase(
		val name: String,
		val allowed: Set<KeybindScreenPolicy>,
		val screen: () -> Screen?
	)

	private companion object {
		val screenCases = listOf(
			ScreenCase("no screen", KeybindScreenPolicy.entries.toSet()) { null },
			ScreenCase(
				"plain screen",
				setOf(KeybindScreenPolicy.NON_TEXT_SCREEN, KeybindScreenPolicy.ALWAYS)
			) { TestScreen(false) },
			ScreenCase("text-input screen", setOf(KeybindScreenPolicy.ALWAYS)) { TestScreen(true) }
		)
	}
}
