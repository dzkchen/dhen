package io.github.dzkchen.dhen.event

enum class InputAction {
	PRESS,
	RELEASE
}

class KeyInputEvent(
	val key: Int,
	val action: InputAction
) : Event

class MouseInputEvent(
	val button: Int,
	val action: InputAction
) : Event
