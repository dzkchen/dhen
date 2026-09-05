package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

internal class KeybindRuntime(
	eventBus: EventBus,
	private val currentScreen: () -> Screen?
) {
	private var bindings = emptyArray<Binding>()
	private val chordRegistry = ChordRegistry()
	private var buffer = IntArray(0)
	private var buffered = 0
	private var lastPressMillis = 0L

	var chordDelayMillis: () -> Long = { DEFAULT_CHORD_DELAY_MILLIS }

	init {
		eventBus.subscribe<KeyInputEvent> { event ->
			if (event.action == InputAction.PRESS && event.key > GLFW.GLFW_MOUSE_BUTTON_LAST) activate(event.key)
		}
		eventBus.subscribe<MouseInputEvent> { event ->
			if (event.action == InputAction.PRESS && event.button in MOUSE_BUTTONS) activate(event.button)
		}
		eventBus.subscribe<ClientTickEvent.End> { settle() }
	}

	fun register(module: Module): Handle {
		for (setting in module.settings) {
			if (setting is KeybindSetting) bindings += Binding(module, setting)
		}
		return Handle { bindings = bindings.filterNot { it.module === module }.toTypedArray() }
	}

	fun chord(binding: ChordBinding): Handle = chordRegistry.add(binding)

	fun count(module: Module): Int {
		var count = 0
		for (binding in bindings) {
			if (binding.module === module) count++
		}
		return count
	}

	private fun activate(code: Int) {
		val screenState = keybindScreenState(currentScreen())
		val bindings = bindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding.setting.code == code && allows(binding.setting.screenPolicy, screenState)) {
				binding.module.activateKeybind(binding.setting)
			}
			index++
		}
		if (screenState != KeybindScreenState.TEXT_INPUT) remember(code)
	}

	private fun remember(code: Int) {
		val tree = chordRegistry.tree
		if (tree.isEmpty) return
		val now = Util.getMillis()
		if (now - lastPressMillis > chordDelayMillis()) buffered = 0
		lastPressMillis = now
		if (buffer.size != tree.depth) {
			buffer = IntArray(tree.depth)
			buffered = 0
		}
		if (buffered == buffer.size) {
			System.arraycopy(buffer, 1, buffer, 0, buffered - 1)
			buffered--
		}
		buffer[buffered++] = code
		if (tree.growable(buffer, buffered)) return
		fireChord()
	}

	private fun settle() {
		if (buffered == 0) return
		if (Util.getMillis() - lastPressMillis <= chordDelayMillis()) return
		fireChord()
	}

	private fun fireChord() {
		val binding = chordRegistry.tree.match(buffer, buffered)
		buffered = 0
		binding?.fire?.invoke()
	}

	private fun allows(policy: KeybindScreenPolicy, state: KeybindScreenState): Boolean = when (policy) {
		KeybindScreenPolicy.NO_SCREEN -> state == KeybindScreenState.NONE
		KeybindScreenPolicy.NON_TEXT_SCREEN -> state != KeybindScreenState.TEXT_INPUT
		KeybindScreenPolicy.ALWAYS -> true
	}

	private companion object {
		private val MOUSE_BUTTONS = GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST
		private const val DEFAULT_CHORD_DELAY_MILLIS = 250L
	}

	private class Binding(
		val module: Module,
		val setting: KeybindSetting
	)
}
