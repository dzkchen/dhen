package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class SettingControlTest {
	@Test
	fun `controlFor maps every setting type to its control`() {
		assertInstanceOf(CheckboxControl::class.java, controlFor(BooleanSetting("b")))
		assertInstanceOf(SliderControl::class.java, controlFor(NumberSetting("n", 0.0, 0.0, 10.0)))
		assertInstanceOf(DropdownControl::class.java, controlFor(SelectorSetting("s", "A", listOf("A", "B"))))
		assertInstanceOf(TextControl::class.java, controlFor(StringSetting("s")))
		assertInstanceOf(ColorControl::class.java, controlFor(ColorSetting("c", Color.rgba(0, 0, 0))))
		assertInstanceOf(KeybindControl::class.java, controlFor(KeybindSetting("k")))
		assertInstanceOf(ActionControl::class.java, controlFor(ActionSetting("a")))
	}

	@Test
	fun `checkbox press toggles the boolean and reports a change`() {
		val setting = BooleanSetting("b", default = false)
		val control = CheckboxControl(setting)
		assertEquals(ControlPress.CHANGED, control.press(0, 100))
		assertTrue(setting.value)
		control.press(0, 100)
		assertFalse(setting.value)
	}

	@Test
	fun `dropdown press cycles forward and wraps`() {
		val setting = SelectorSetting("s", default = "A", options = listOf("A", "B", "C"))
		val control = DropdownControl(setting)
		assertEquals(ControlPress.CHANGED, control.press(0, 100))
		assertEquals("B", setting.value)
		control.press(0, 100)
		assertEquals("C", setting.value)
		control.press(0, 100)
		assertEquals("A", setting.value)
	}

	@Test
	fun `slider press and drag set the value from mouse position and track drag`() {
		val setting = NumberSetting("n", default = 0.0, min = 0.0, max = 10.0, step = 1.0)
		val control = SliderControl(setting)
		assertEquals(ControlPress.TRACK, control.press(50, 100))
		assertEquals(5.0, setting.value)
		control.drag(100, 100)
		assertEquals(10.0, setting.value)
		control.drag(-20, 100)
		assertEquals(0.0, setting.value)
	}

	@Test
	fun `slider maps mouse position onto the setting step`() {
		val setting = NumberSetting("n", default = 0.0, min = 0.0, max = 100.0, step = 5.0)
		val control = SliderControl(setting)
		control.press(23, 100)
		assertEquals(25.0, setting.value)
	}

	@Test
	fun `slider value formatting is compact`() {
		assertEquals("5", formatSliderValue(5.0))
		assertEquals("-3", formatSliderValue(-3.0))
		assertEquals("7.5", formatSliderValue(7.5))
		assertEquals("0.5", formatSliderValue(0.5))
		assertEquals("-0.5", formatSliderValue(-0.5))
		assertEquals("0.25", formatSliderValue(0.25))
	}

	@Test
	fun `text control focuses, edits, and commits on enter`() {
		val setting = StringSetting("s", default = "ab", maxLength = 5)
		val control = TextControl(setting)
		assertEquals(ControlPress.FOCUS, control.press(0, 100))
		control.charTyped('c'.code)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals("abc", setting.value)
	}

	@Test
	fun `text control respects maxLength and backspace`() {
		val setting = StringSetting("s", default = "", maxLength = 3)
		val control = TextControl(setting)
		control.press(0, 100)
		"abcd".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals("ab", setting.value)
	}

	@Test
	fun `text control escape cancels without committing`() {
		val setting = StringSetting("s", default = "keep", maxLength = 10)
		val control = TextControl(setting)
		control.press(0, 100)
		control.charTyped('x'.code)
		assertEquals(ControlKey.CANCELLED, control.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0))
		assertEquals("keep", setting.value)
	}

	@Test
	fun `text control commits pending edit on blur`() {
		val setting = StringSetting("s", default = "a", maxLength = 10)
		val control = TextControl(setting)
		control.press(0, 100)
		control.charTyped('b'.code)
		assertTrue(control.blur())
		assertEquals("ab", setting.value)
	}

	@Test
	fun `color control parses six-digit hex on commit`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		assertEquals(ControlPress.FOCUS, control.press(0, 100))
		"FF8000".forEach { control.charTyped(it.code) }
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(Color.rgba(255, 128, 0).argb, setting.value.argb)
	}

	@Test
	fun `color control honors allowAlpha with eight digits`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0, 255), allowAlpha = true)
		val control = ColorControl(setting)
		control.press(0, 100)
		"112233AA".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_ENTER, 0)
		assertEquals(Color.rgba(0x11, 0x22, 0x33, 0xAA).argb, setting.value.argb)
	}

	@Test
	fun `color control forces opaque when alpha is disallowed`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(0, 100)
		"abcdef".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_ENTER, 0)
		assertEquals(0xFF, setting.value.alpha)
	}

	@Test
	fun `color control ignores non-hex and short input`() {
		val setting = ColorSetting("c", Color.rgba(10, 20, 30), allowAlpha = false)
		val control = ColorControl(setting)
		val before = setting.value.argb
		control.press(0, 100)
		"GG".forEach { control.charTyped(it.code) }
		"12".forEach { control.charTyped(it.code) }
		assertEquals(ControlKey.CANCELLED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(before, setting.value.argb)
	}

	@Test
	fun `keybind control arms and binds a key`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_KEY_UNKNOWN)
		val control = KeybindControl(setting)
		assertEquals(ControlPress.FOCUS, control.press(0, 100))
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_J, 0))
		assertEquals(GLFW.GLFW_KEY_J, setting.value)
	}

	@Test
	fun `keybind control unbinds on escape`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_KEY_J)
		val control = KeybindControl(setting)
		control.press(0, 100)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, setting.value)
	}

	@Test
	fun `keybind control captures any mouse button`() {
		val setting = KeybindSetting("k")
		val control = KeybindControl(setting)
		control.press(0, 100)
		assertEquals(ControlKey.COMMITTED, control.captureMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, setting.value)
	}

	@Test
	fun `keybind control reports no change when the mouse button is unchanged`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_MOUSE_BUTTON_1)
		val control = KeybindControl(setting)
		control.press(0, 100)
		assertEquals(ControlKey.CANCELLED, control.captureMouse(GLFW.GLFW_MOUSE_BUTTON_1))
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_1, setting.value)
	}

	@Test
	fun `keybind control ignores input while unarmed`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_KEY_J)
		val control = KeybindControl(setting)
		assertEquals(ControlKey.IGNORED, control.keyPressed(GLFW.GLFW_KEY_L, 0))
		assertEquals(ControlKey.IGNORED, control.captureMouse(GLFW.GLFW_MOUSE_BUTTON_1))
		assertEquals(GLFW.GLFW_KEY_J, setting.value)
	}

	@Test
	fun `action control invokes the callback`() {
		var fired = false
		val setting = ActionSetting("a", default = { fired = true })
		val control = ActionControl(setting)
		assertEquals(ControlPress.INVOKED, control.press(0, 100))
		assertTrue(fired)
	}

	@Test
	fun `action control swallows callback failures`() {
		val setting = ActionSetting("a", default = { throw RuntimeException("boom") })
		val control = ActionControl(setting)
		assertEquals(ControlPress.INVOKED, control.press(0, 100))
	}
}
