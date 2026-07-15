package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.event.KeyInputEvent
import org.lwjgl.glfw.GLFW

// Temporary verification aid: gives runClient a toggleable module until native
// modules exist. Safe to delete once real modules are registered.
class PlaceholderModule : Module(
	name = "Test Module",
	category = Category.DEV,
	description = "Placeholder module for verifying core controls."
) {
	private var inputEvents = 0

	init {
		on<KeyInputEvent> { inputEvents++ }
	}

	@Suppress("unused")
	private val keybind by KeybindSetting(
		name = "Keybind",
		default = GLFW.GLFW_KEY_K,
		description = "Disables the test module while it is enabled."
	).onPress(::toggle)
}
