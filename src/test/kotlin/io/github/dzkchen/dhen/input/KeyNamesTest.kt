package io.github.dzkchen.dhen.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class KeyNamesTest {
	@Test
	fun `a digit reads as the keyboard digit and never as a mouse button`() {
		for (digit in 0..9) {
			assertEquals(GLFW.GLFW_KEY_0 + digit, keyCode(digit.toString()), digit.toString())
		}
	}

	@Test
	fun `letters and multi word key names resolve`() {
		assertEquals(GLFW.GLFW_KEY_G, keyCode("G"))
		assertEquals(GLFW.GLFW_KEY_G, keyCode("g"))
		assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, keyCode("left shift"))
		assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, keyCode("left-shift"))
		assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, keyCode("left.shift"))
	}

	@Test
	fun `a mouse button needs the mouse prefix`() {
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_LEFT, keyCode("mouse.left"))
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_RIGHT, keyCode("mouse.right"))
		assertEquals(3, keyCode("mouse.4"))
	}

	@Test
	fun `a numeric name is never taken for a raw key code`() {
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("42"))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("mouse.0"))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("mouse.99"))
	}

	@Test
	fun `displaying a low numbered key does not rebind that name`() {
		keyDisplayName(3)
		keyDisplayName(5)

		assertEquals(GLFW.GLFW_KEY_3, keyCode("3"))
		assertEquals(GLFW.GLFW_KEY_5, keyCode("5"))
	}

	@Test
	fun `rubbish is refused rather than guessed at`() {
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode(""))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("  "))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("banana"))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("mouse.banana"))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, keyCode("unknown"))
	}
}
