package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.KeyInputEvent
import org.lwjgl.glfw.GLFW

class PlaceholderModule : Module(
	name = "Test Module",
	category = Category.DEV,
	description = "Placeholder module for verifying core controls."
) {
	private var inputEvents = 0

	init {
		on<KeyInputEvent> { inputEvents++ }
	}

	private val showAdvanced by BooleanSetting(
		name = "Show Advanced",
		default = false,
		description = "Reveals the advanced value control."
	)

	@Suppress("unused")
	private val strength by NumberSetting(
		name = "Strength",
		default = 5.0,
		min = 0.0,
		max = 10.0,
		step = 1.0,
		description = "Example slider value."
	)

	@Suppress("unused")
	private val advanced by NumberSetting(
		name = "Advanced",
		default = 50.0,
		min = 0.0,
		max = 100.0,
		step = 5.0,
		description = "Gated by Show Advanced."
	).withDependency { showAdvanced }

	@Suppress("unused")
	private val mode by SelectorSetting(
		name = "Mode",
		default = "One",
		options = listOf("One", "Two", "Three"),
		description = "Example dropdown."
	)

	@Suppress("unused")
	private val keybind by KeybindSetting(
		name = "Keybind",
		default = GLFW.GLFW_KEY_K,
		description = "Disables the test module while it is enabled."
	).onPress(::toggle)
}
