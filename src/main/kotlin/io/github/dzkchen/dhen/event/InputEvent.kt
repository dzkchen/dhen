package io.github.dzkchen.dhen.event

import org.lwjgl.glfw.GLFW

enum class InputAction {
	PRESS,
	RELEASE,
	REPEAT;

	internal companion object {
		fun of(glfwAction: Int): InputAction = when (glfwAction) {
			GLFW.GLFW_PRESS -> PRESS
			GLFW.GLFW_RELEASE -> RELEASE
			else -> REPEAT
		}
	}
}

class KeyInputEvent(
	val key: Int,
	val action: InputAction,
	val scancode: Int,
	val modifiers: Int
) : Event, Cancellable {
	override var cancelled: Boolean = false
}

class MouseInputEvent(
	val button: Int,
	val action: InputAction,
	val modifiers: Int
) : Event, Cancellable {
	override var cancelled: Boolean = false
}
