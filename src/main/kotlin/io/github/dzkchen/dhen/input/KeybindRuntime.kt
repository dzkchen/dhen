package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.module.Module
import org.lwjgl.glfw.GLFW

internal class KeybindRuntime(
	eventBus: EventBus,
	private val anyScreenOpen: () -> Boolean
) {
	private var bindings = emptyArray<Binding>()

	init {
		eventBus.subscribe<KeyInputEvent> { event ->
			if (event.action == InputAction.PRESS && event.key > GLFW.GLFW_MOUSE_BUTTON_LAST) activate(event.key)
		}
		eventBus.subscribe<MouseInputEvent> { event ->
			if (event.action == InputAction.PRESS && event.button in MOUSE_BUTTONS) activate(event.button)
		}
	}

	fun register(module: Module): Handle {
		for (setting in module.settings) {
			if (setting is KeybindSetting) bindings += Binding(module, setting)
		}
		return Handle { bindings = bindings.filterNot { it.module === module }.toTypedArray() }
	}

	fun count(module: Module): Int {
		var count = 0
		for (binding in bindings) {
			if (binding.module === module) count++
		}
		return count
	}

	private fun activate(code: Int) {
		if (anyScreenOpen()) return
		val bindings = bindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding.setting.code == code) binding.module.activateKeybind(binding.setting)
			index++
		}
	}

	private companion object {
		private val MOUSE_BUTTONS = GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST
	}

	private class Binding(
		val module: Module,
		val setting: KeybindSetting
	)
}
