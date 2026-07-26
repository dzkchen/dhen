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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingControlTest {
	@Test
	fun `controlFor maps the three supported types`() {
		assertInstanceOf(CheckboxControl::class.java, controlFor(BooleanSetting("b")))
		assertInstanceOf(SliderControl::class.java, controlFor(NumberSetting("n", 0.0, 0.0, 10.0)))
		assertInstanceOf(DropdownControl::class.java, controlFor(SelectorSetting("s", "A", listOf("A", "B"))))
	}

	@Test
	fun `controlFor returns null for types owned by later sub-tasks`() {
		assertNull(controlFor(StringSetting("s")))
		assertNull(controlFor(ColorSetting("c", Color.rgba(0, 0, 0))))
		assertNull(controlFor(KeybindSetting("k")))
		assertNull(controlFor(ActionSetting("a")))
	}

	@Test
	fun `checkbox press toggles the boolean`() {
		val setting = BooleanSetting("b", default = false)
		val control = CheckboxControl(setting)
		assertFalse(control.press(0, 100))
		assertTrue(setting.value)
		control.press(0, 100)
		assertFalse(setting.value)
	}

	@Test
	fun `dropdown press cycles forward and wraps`() {
		val setting = SelectorSetting("s", default = "A", options = listOf("A", "B", "C"))
		val control = DropdownControl(setting)
		control.press(0, 100)
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
		assertTrue(control.press(50, 100))
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
}
