package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.module.Module
import org.lwjgl.glfw.GLFW

internal class KeybindRuntime(eventBus: EventBus) {
	private var bindings = emptyArray<Binding>()

	init {
		eventBus.subscribe<KeyInputEvent> { event ->
			if (event.action == InputAction.PRESS) activateKey(event.key)
		}
		eventBus.subscribe<MouseInputEvent> { event ->
			if (event.action == InputAction.PRESS) activateMouseButton(event.button)
		}
	}

	fun register(module: Module) {
		for (setting in module.settings) {
			if (setting is KeybindSetting) bindings += Binding(module, setting)
		}
	}

	private fun activateKey(key: Int) {
		val bindings = bindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding.setting.value == key && key > GLFW.GLFW_MOUSE_BUTTON_LAST) {
				binding.module.activateKeybind(binding.setting)
			}
			index++
		}
	}

	private fun activateMouseButton(button: Int) {
		val bindings = bindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding.setting.value == button && button in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST) {
				binding.module.activateKeybind(binding.setting)
			}
			index++
		}
	}

	private class Binding(
		val module: Module,
		val setting: KeybindSetting
	)
}
