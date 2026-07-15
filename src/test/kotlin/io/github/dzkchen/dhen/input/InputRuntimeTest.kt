package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class InputRuntimeTest {
	@Test
	fun `poll emits only key and mouse edges`() {
		val bus = EventBus()
		val runtime = InputRuntime(bus)
		val source = FakeInputSource()
		val keys = mutableListOf<Pair<Int, InputAction>>()
		val buttons = mutableListOf<Pair<Int, InputAction>>()
		bus.subscribe<KeyInputEvent> { keys += it.key to it.action }
		bus.subscribe<MouseInputEvent> { buttons += it.button to it.action }

		runtime.poll(source, 0L)
		source.key = GLFW.GLFW_KEY_K
		source.button = GLFW.GLFW_MOUSE_BUTTON_4
		runtime.poll(source, 0L)
		runtime.poll(source, 0L)
		source.key = null
		source.button = null
		runtime.poll(source, 0L)

		assertEquals(
			listOf(GLFW.GLFW_KEY_K to InputAction.PRESS, GLFW.GLFW_KEY_K to InputAction.RELEASE),
			keys
		)
		assertEquals(
			listOf(GLFW.GLFW_MOUSE_BUTTON_4 to InputAction.PRESS, GLFW.GLFW_MOUSE_BUTTON_4 to InputAction.RELEASE),
			buttons
		)
	}

	private class FakeInputSource(
		var key: Int? = null,
		var button: Int? = null
	) : InputRuntime.InputSource {
		override fun isKeyPressed(window: Long, key: Int): Boolean = this.key == key

		override fun isMouseButtonPressed(window: Long, button: Int): Boolean = this.button == button
	}
}
