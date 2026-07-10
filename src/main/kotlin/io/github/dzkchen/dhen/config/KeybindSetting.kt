package io.github.dzkchen.dhen.config

import org.lwjgl.glfw.GLFW

class KeybindSetting(
	name: String,
	override val default: Int = GLFW.GLFW_KEY_UNKNOWN,
	description: String = ""
) : Setting<Int>(name, description) {

	override var value: Int = default

	val isBound: Boolean
		get() = value != GLFW.GLFW_KEY_UNKNOWN
}
