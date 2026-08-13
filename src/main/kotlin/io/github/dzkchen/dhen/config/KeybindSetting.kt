package io.github.dzkchen.dhen.config

import org.lwjgl.glfw.GLFW

class KeybindSetting(
	name: String,
	override val default: Int = GLFW.GLFW_KEY_UNKNOWN,
	description: String = ""
) : Setting<Int>(name, description) {
	private var onPress: (() -> Unit)? = null

	var code: Int = default

	override var value: Int
		get() = code
		set(value) {
			code = value
		}

	var firesWhileDisabled: Boolean = false
		private set

	val isBound: Boolean
		get() = code != GLFW.GLFW_KEY_UNKNOWN

	fun onPress(callback: () -> Unit): KeybindSetting {
		onPress = callback
		return this
	}

	fun evenWhileDisabled(): KeybindSetting {
		firesWhileDisabled = true
		return this
	}

	internal fun activate() {
		onPress?.invoke()
	}
}
