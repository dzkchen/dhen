package io.github.dzkchen.dhen.input

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen

internal enum class KeybindScreenState {
	NONE,
	PLAIN,
	TEXT_INPUT
}

internal interface TextInputTarget {
	val textInputFocused: Boolean
}

internal fun keybindScreenState(screen: Screen?): KeybindScreenState = when {
	screen == null -> KeybindScreenState.NONE
	screen is AbstractSignEditScreen -> KeybindScreenState.TEXT_INPUT
	textInputFocused(screen) -> KeybindScreenState.TEXT_INPUT
	else -> KeybindScreenState.PLAIN
}

private fun textInputFocused(listener: GuiEventListener): Boolean {
	var focused: GuiEventListener? = listener
	while (focused != null) {
		if (focused is TextInputTarget && focused.textInputFocused) return true
		if (focused is EditBox || focused is MultiLineEditBox) return true
		val child = (focused as? ContainerEventHandler)?.focused ?: return false
		if (child === focused) return false
		focused = child
	}
	return false
}
