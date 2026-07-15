package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.config.KeybindSetting
import org.lwjgl.glfw.GLFW

// Temporary verification aid: gives runClient a toggleable module until native
// modules exist. Safe to delete once real modules are registered.
class PlaceholderModule : Module(
	name = "Test Module",
	category = Category.DEV,
	description = "Placeholder module for verifying core controls."
) {
	@Suppress("unused")
	private val keybind by KeybindSetting(
		name = "Keybind",
		default = GLFW.GLFW_KEY_K,
		description = "Disables the test module while it is enabled."
	).onPress(::toggle)
}
