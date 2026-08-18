package io.github.dzkchen.dhen.event

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class InputHooksTest {
	private val bus = EventBus()

	@AfterEach
	fun tearDown() = InputHooks.uninstall()

	@Test
	fun `a key carries its code, modifiers and action`() {
		InputHooks.install(bus)
		val seen = mutableListOf<KeyInputEvent>()
		bus.subscribe<KeyInputEvent> { seen += it }

		InputHooks.beforeKey(GLFW.GLFW_KEY_K, 42, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_PRESS)
		InputHooks.beforeKey(GLFW.GLFW_KEY_K, 42, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_RELEASE)

		assertEquals(listOf(InputAction.PRESS, InputAction.RELEASE), seen.map { it.action })
		assertEquals(GLFW.GLFW_KEY_K, seen[0].key)
		assertEquals(42, seen[0].scancode)
		assertEquals(GLFW.GLFW_MOD_SHIFT, seen[0].modifiers)
	}

	@Test
	fun `a repeat is published as a repeat, never as a press`() {
		InputHooks.install(bus)
		val actions = mutableListOf<InputAction>()
		bus.subscribe<KeyInputEvent> { actions += it.action }

		InputHooks.beforeKey(GLFW.GLFW_KEY_K, 0, 0, GLFW.GLFW_REPEAT)

		assertEquals(listOf(InputAction.REPEAT), actions)
	}

	@Test
	fun `a mouse button carries its code, modifiers and action`() {
		InputHooks.install(bus)
		val seen = mutableListOf<MouseInputEvent>()
		bus.subscribe<MouseInputEvent> { seen += it }

		InputHooks.beforeMouseButton(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_PRESS)

		assertEquals(GLFW.GLFW_MOUSE_BUTTON_4, seen.single().button)
		assertEquals(GLFW.GLFW_MOD_CONTROL, seen.single().modifiers)
		assertEquals(InputAction.PRESS, seen.single().action)
	}

	@Test
	fun `cancelling either event reports the input as handled`() {
		InputHooks.install(bus)
		bus.subscribe<KeyInputEvent> { it.cancel() }
		bus.subscribe<MouseInputEvent> { it.cancel() }

		assertTrue(InputHooks.beforeKey(GLFW.GLFW_KEY_K, 0, 0, GLFW.GLFW_PRESS))
		assertTrue(InputHooks.beforeMouseButton(GLFW.GLFW_MOUSE_BUTTON_1, 0, GLFW.GLFW_PRESS))
	}

	@Test
	fun `an uninstalled hook publishes nothing and cancels nothing`() {
		InputHooks.install(bus)
		var seen = 0
		bus.subscribe<KeyInputEvent> {
			seen++
			it.cancel()
		}
		bus.subscribe<MouseInputEvent> { seen++ }

		InputHooks.uninstall()

		assertFalse(InputHooks.active())
		assertFalse(InputHooks.beforeKey(GLFW.GLFW_KEY_K, 0, 0, GLFW.GLFW_PRESS))
		assertFalse(InputHooks.beforeMouseButton(GLFW.GLFW_MOUSE_BUTTON_1, 0, GLFW.GLFW_PRESS))
		assertEquals(0, seen)
	}

	@Test
	fun `a throwing handler latches input events off`() {
		InputHooks.install(bus)
		bus.subscribe<KeyInputEvent> { error("boom") }

		assertFalse(InputHooks.beforeKey(GLFW.GLFW_KEY_K, 0, 0, GLFW.GLFW_PRESS))
		assertFalse(InputHooks.active())
	}
}
