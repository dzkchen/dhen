package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.util.Color
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlaceholderModule(
	name: String = "Test Module",
	category: Category = Category.DEV,
	description: String = "Placeholder module for verifying core controls.",
	private val toggleKey: Int = GLFW.GLFW_KEY_K
) : Module(name, category, description) {
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
	private val label by StringSetting(
		name = "Label",
		default = "dhen",
		maxLength = 16,
		description = "Example text field."
	)

	@Suppress("unused")
	private val tint by ColorSetting(
		name = "Color",
		default = Color.rgba(255, 128, 0),
		allowAlpha = true,
		description = "Example color picker."
	)

	@Suppress("unused")
	private val keybind by KeybindSetting(
		name = "Keybind",
		default = toggleKey,
		description = "Disables the test module while it is enabled."
	).onPress(::toggle)

	@Suppress("unused")
	private val ping by ActionSetting(
		name = "Ping",
		default = { LOG.info("Test Module ping") },
		description = "Logs a line when clicked."
	)

	private companion object {
		val LOG: Logger = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
