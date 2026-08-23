package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe

internal object InputHooks : Hooks {
	override val feed = "Input"

	private val failsafe = Failsafe("Dhen {} failed, its input events are off until restart")

	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	override fun uninstall() {
		channels = null
	}

	override fun active(): Boolean = channels != null

	@JvmStatic
	fun beforeKey(key: Int, scancode: Int, modifiers: Int, glfwAction: Int): Boolean =
		guarded("key input") { it.key(key, scancode, modifiers, glfwAction) }

	@JvmStatic
	fun beforeMouseButton(button: Int, modifiers: Int, glfwAction: Int): Boolean =
		guarded("mouse input") { it.mouse(button, modifiers, glfwAction) }

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			this.channels = null
			failsafe.fail(label, throwable)
			false
		}
	}

	private class Channels(bus: EventBus) {
		private val keys = bus.type<KeyInputEvent>()
		private val buttons = bus.type<MouseInputEvent>()

		fun key(key: Int, scancode: Int, modifiers: Int, glfwAction: Int): Boolean {
			val event = KeyInputEvent(key, InputAction.of(glfwAction), scancode, modifiers)
			keys.dispatch(event)
			return event.cancelled
		}

		fun mouse(button: Int, modifiers: Int, glfwAction: Int): Boolean {
			val event = MouseInputEvent(button, InputAction.of(glfwAction), modifiers)
			buttons.dispatch(event)
			return event.cancelled
		}
	}
}
