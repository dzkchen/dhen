package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class SettingControlTest {
	@Test
	fun `controlFor maps every setting type to its control`() {
		assertInstanceOf(ToggleControl::class.java, controlFor(BooleanSetting("b")))
		assertInstanceOf(SliderControl::class.java, controlFor(NumberSetting("n", 0.0, 0.0, 10.0)))
		assertInstanceOf(DropdownControl::class.java, controlFor(SelectorSetting("s", "A", listOf("A", "B"))))
		assertInstanceOf(TextControl::class.java, controlFor(StringSetting("s")))
		assertInstanceOf(ColorControl::class.java, controlFor(ColorSetting("c", Color.rgba(0, 0, 0))))
		assertInstanceOf(KeybindControl::class.java, controlFor(KeybindSetting("k")))
		assertInstanceOf(ActionControl::class.java, controlFor(ActionSetting("a")))
	}

	@Test
	fun `toggle press flips the boolean and reports a change`() {
		val setting = BooleanSetting("b", default = false)
		val control = ToggleControl(setting)
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
	fun `color control rejects a digit that does not fit the character it would store`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(0, 100)
		repeat(6) { control.charTyped(MATHEMATICAL_BOLD_DIGIT_ZERO) }
		"FF8000".forEach { control.charTyped(it.code) }

		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(Color.rgba(255, 128, 0).argb, setting.value.argb)
	}

	private companion object {
		const val MATHEMATICAL_BOLD_DIGIT_ZERO = 0x1D7CE
		const val GLYPH_WIDTH = 5
		const val SWATCH_TRAILING = 12
		const val GLYPH_TRAILING = 9
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
	fun `a throwing dependency quarantines the control and reports it to the owning module once`() {
		var invocations = 0
		val setting = BooleanSetting("b").withDependency {
			invocations++
			throw RuntimeException("boom")
		}
		val module = owning(setting)
		val control = controlFor(setting)!!

		assertFalse(control.renderable())
		assertTrue(control.failed)
		assertEquals(1, invocations)
		assertEquals(1, module.errorCount)

		assertFalse(control.renderable())
		assertEquals(1, invocations)
		assertEquals(1, module.errorCount)
	}

	@Test
	fun `a quarantined control takes no input and changes no value`() {
		val setting = BooleanSetting("b", default = false).withDependency { throw RuntimeException("boom") }
		val module = owning(setting)
		val control = controlFor(setting)!!
		control.renderable()

		assertNull(control.press(0, 100))
		assertFalse(setting.value)
		assertEquals(ControlKey.IGNORED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(ControlKey.IGNORED, control.captureMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT))
		assertFalse(control.charTyped('a'.code))
		assertFalse(control.blur())
		assertEquals(1, module.errorCount)
	}

	@Test
	fun `a throwing action quarantines the control and reports it instead of swallowing it`() {
		val setting = ActionSetting("a", default = { throw RuntimeException("boom") })
		val module = owning(setting)
		val control = controlFor(setting)!!

		assertNull(control.press(0, 100))
		assertTrue(control.failed)
		assertEquals(1, module.errorCount)
	}

	@Test
	fun `a control whose setting no module registered quarantines itself with nothing to report to`() {
		val setting = BooleanSetting("b").withDependency { throw RuntimeException("boom") }
		val control = controlFor(setting)!!

		assertTrue(control.clientOwned)
		assertFalse(control.renderable())
		assertTrue(control.failed)
	}

	private fun <T> owning(setting: Setting<T>): Module = object : Module(
		name = "Owner",
		category = Category.DEV,
		description = "Fixture owning the setting under test."
	) {
		init {
			registerSetting(setting)
		}
	}

	@Test
	fun `a value pill hugs its content, keeps a minimum width, and stops at the setting name`() {
		assertEquals(100 - 50 - 2 * PILL_PAD, pillLeft(x = 0, width = 100, contentWidth = 50, labelWidth = 0))
		assertEquals(100 - PILL_MIN_WIDTH, pillLeft(x = 0, width = 100, contentWidth = 4, labelWidth = 0))
		assertEquals(CONTROL_TEXT_INSET + 30 + LABEL_GAP, pillLeft(x = 0, width = 100, contentWidth = 120, labelWidth = 30))
		assertEquals(100 - PILL_MIN_WIDTH, pillLeft(x = 0, width = 100, contentWidth = 120, labelWidth = 90))
		assertEquals(20, pillLeft(x = 20, width = 4, contentWidth = 0, labelWidth = 0))
	}

	@Test
	fun `the widest value each setting type allows still clears its own name`() {
		for (width in intArrayOf(ClickGuiShellScreen.CONTROLS_WIDTH, ClickGuiShellScreen.PANEL_CONTROLS_WIDTH)) {
			assertRoomForName(width, "Label", "WWWWWWWWWWWWWWWW", trailing = 0, editing = true)
			assertRoomForName(width, "Color", "FFFFFFFF", trailing = SWATCH_TRAILING, editing = true)
			assertRoomForName(width, "Theme", "high-contrast-midnight", trailing = GLYPH_TRAILING, editing = false)
			assertRoomForName(width, "Keybind", "Right Control", trailing = 0, editing = false)
			assertRoomForName(width, "A setting with a very long name", "WWWWWWWWWWWWWWWW", trailing = SWATCH_TRAILING, editing = true)
		}
	}

	private fun assertRoomForName(width: Int, name: String, value: String, trailing: Int, editing: Boolean) {
		val label = elide(name, labelRoom(width, PILL_MIN_WIDTH + trailing), false, ::glyphs)
		val labelWidth = glyphs(label)
		val reserve = if (editing) CARET_WIDTH else 0
		val shown = elide(value, pillContent(width, labelWidth, trailing) - trailing - reserve, editing, ::glyphs)
		val left = pillLeft(0, width, glyphs(shown) + reserve + trailing, labelWidth)

		assertTrue(left - CONTROL_TEXT_INSET - labelWidth >= LABEL_GAP) {
			"'$shown' starts at $left, over '$label' which ends at ${CONTROL_TEXT_INSET + labelWidth}"
		}
		assertTrue(glyphs(shown) + reserve + trailing <= width - left - 2 * PILL_PAD) {
			"'$shown' is wider than the pill it sits in"
		}
	}

	private fun glyphs(text: String): Int = text.length * GLYPH_WIDTH

	@Test
	@Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
	fun `pill padding clears the rounded cap so text cannot touch the curve`() {
		assertTrue(PILL_PAD > PILL_CAP)
		assertTrue(PILL_MIN_WIDTH >= 2 * PILL_PAD)
	}
}
