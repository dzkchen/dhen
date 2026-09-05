package io.github.dzkchen.dhen.input

import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import java.util.Locale

internal fun keyDisplayName(code: Int): String =
	if (code <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
		InputConstants.Type.MOUSE.getOrCreate(code).displayName.string
	} else {
		InputConstants.Type.KEYSYM.getOrCreate(code).displayName.string
	}

internal fun keyCode(token: String): Int {
	val tail = token.trim().lowercase(Locale.ROOT).replace(' ', '.').replace('-', '.')
	if (tail.isEmpty()) return GLFW.GLFW_KEY_UNKNOWN
	if (tail.startsWith(MOUSE_PREFIX)) return mouseCode(tail.removePrefix(MOUSE_PREFIX))
	if (tail.length == 1) return singleKeyCode(tail[0])
	if (tail.all(Char::isDigit)) return GLFW.GLFW_KEY_UNKNOWN
	return named("key.keyboard.$tail")
}

private fun singleKeyCode(symbol: Char): Int = when (symbol) {
	in '0'..'9' -> GLFW.GLFW_KEY_0 + (symbol - '0')
	in 'a'..'z' -> GLFW.GLFW_KEY_A + (symbol - 'a')
	else -> named("key.keyboard.$symbol")
}

private fun mouseCode(tail: String): Int = when (tail) {
	"left" -> GLFW.GLFW_MOUSE_BUTTON_LEFT
	"right" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT
	"middle" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE
	else -> {
		val button = (tail.toIntOrNull() ?: 0) - 1
		if (button in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST) button else GLFW.GLFW_KEY_UNKNOWN
	}
}

private fun named(name: String): Int = try {
	InputConstants.getKey(name).value
} catch (ignored: IllegalArgumentException) {
	GLFW.GLFW_KEY_UNKNOWN
}

private const val MOUSE_PREFIX = "mouse."
