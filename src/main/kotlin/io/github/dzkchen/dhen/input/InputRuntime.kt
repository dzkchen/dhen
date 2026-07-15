package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import org.lwjgl.glfw.GLFW

class InputRuntime(eventBus: EventBus) {
	private val keyEvents = eventBus.type<KeyInputEvent>()
	private val mouseEvents = eventBus.type<MouseInputEvent>()
	private val keyStates = BooleanArray(GLFW.GLFW_KEY_LAST + 1)
	private val mouseStates = BooleanArray(GLFW.GLFW_MOUSE_BUTTON_LAST + 1)
	private val keyPressEvents = Array(keyStates.size) { KeyInputEvent(it, InputAction.PRESS) }
	private val keyReleaseEvents = Array(keyStates.size) { KeyInputEvent(it, InputAction.RELEASE) }
	private val mousePressEvents = Array(mouseStates.size) { MouseInputEvent(it, InputAction.PRESS) }
	private val mouseReleaseEvents = Array(mouseStates.size) { MouseInputEvent(it, InputAction.RELEASE) }

	fun poll(source: InputSource, window: Long) {
		var key = GLFW.GLFW_KEY_SPACE
		while (key <= GLFW.GLFW_KEY_LAST) {
			val pressed = source.isKeyPressed(window, key)
			if (pressed != keyStates[key]) {
				keyStates[key] = pressed
				keyEvents.dispatch(if (pressed) keyPressEvents[key] else keyReleaseEvents[key])
			}
			key++
		}

		var button = GLFW.GLFW_MOUSE_BUTTON_1
		while (button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
			val pressed = source.isMouseButtonPressed(window, button)
			if (pressed != mouseStates[button]) {
				mouseStates[button] = pressed
				mouseEvents.dispatch(if (pressed) mousePressEvents[button] else mouseReleaseEvents[button])
			}
			button++
		}
	}

	interface InputSource {
		fun isKeyPressed(window: Long, key: Int): Boolean

		fun isMouseButtonPressed(window: Long, button: Int): Boolean
	}

	object Glfw : InputSource {
		override fun isKeyPressed(window: Long, key: Int): Boolean =
			GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS

		override fun isMouseButtonPressed(window: Long, button: Int): Boolean =
			GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS
	}
}
