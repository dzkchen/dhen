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
		assertInstanceOf(CycleControl::class.java, controlFor(SelectorSetting("s", "A", listOf("A", "B"))))
		assertInstanceOf(TextControl::class.java, controlFor(StringSetting("s")))
		assertInstanceOf(ColorControl::class.java, controlFor(ColorSetting("c", Color.rgba(0, 0, 0))))
		assertInstanceOf(KeybindControl::class.java, controlFor(KeybindSetting("k")))
		assertInstanceOf(ActionControl::class.java, controlFor(ActionSetting("a")))
	}

	@Test
	fun `toggle press flips the boolean and reports a change`() {
		val setting = BooleanSetting("b", default = false)
		val control = ToggleControl(setting)
		assertEquals(ControlPress.CHANGED, control.press(0, 0, 100))
		assertTrue(setting.value)
		control.press(0, 0, 100)
		assertFalse(setting.value)
	}

	@Test
	fun `cycle press moves forward and wraps`() {
		val setting = SelectorSetting("s", default = "A", options = listOf("A", "B", "C"))
		val control = CycleControl(setting)
		assertEquals(ControlPress.CHANGED, control.press(0, 0, 100))
		assertEquals("B", setting.value)
		control.press(0, 0, 100)
		assertEquals("C", setting.value)
		control.press(0, 0, 100)
		assertEquals("A", setting.value)
	}

	@Test
	fun `option count alone decides between cycling and a list`() {
		for (count in 1..4) {
			val setting = SelectorSetting("s", "A", OPTIONS.take(count))
			val control = controlFor(setting)
			if (count < 3) assertInstanceOf(CycleControl::class.java, control)
			else assertInstanceOf(DropdownControl::class.java, control)
		}
	}

	@Test
	fun `a dropdown opens on the pill, grows, picks the row clicked, and closes`() {
		val setting = SelectorSetting("s", default = "A", options = OPTIONS)
		val control = DropdownControl(setting)
		assertEquals(CONTROL_ROW_HEIGHT, control.height)

		assertEquals(ControlPress.RESIZED, control.press(0, 0, 100))
		assertTrue(control.expanded)
		val listed = control.height
		assertTrue(listed > CONTROL_ROW_HEIGHT) { "an open list must make its control taller" }

		assertEquals(ControlPress.CHANGED, control.press(0, (CONTROL_ROW_HEIGHT + listed) / 2, 100))
		assertEquals("B", setting.value)
		assertFalse(control.expanded)
		assertEquals(CONTROL_ROW_HEIGHT, control.height)
	}

	@Test
	fun `a dropdown closes on a second press of its pill without changing the value`() {
		val setting = SelectorSetting("s", default = "A", options = OPTIONS)
		val control = DropdownControl(setting)
		control.press(0, 0, 100)

		assertEquals(ControlPress.RESIZED, control.press(0, CONTROL_ROW_HEIGHT - 1, 100))
		assertFalse(control.expanded)
		assertEquals("A", setting.value)
	}

	@Test
	fun `collapsing an open dropdown reports the change once`() {
		val control = DropdownControl(SelectorSetting("s", "A", OPTIONS))
		assertFalse(control.collapse())

		control.press(0, 0, 100)
		assertTrue(control.collapse())
		assertFalse(control.collapse())
		assertEquals(CONTROL_ROW_HEIGHT, control.height)
	}

	@Test
	fun `a control body stacks its controls by their own heights`() {
		val listed = DropdownControl(SelectorSetting("s", "A", OPTIONS))
		val body = ControlBody(listOf(ToggleControl(BooleanSetting("b")), listed, ToggleControl(BooleanSetting("c"))))
		assertEquals(3 * CONTROL_ROW_HEIGHT, body.height)

		listed.press(0, 0, 100)
		val grown = listed.height
		assertEquals(2 * CONTROL_ROW_HEIGHT + grown, body.height)
		assertEquals(CONTROL_ROW_HEIGHT, body.topOf(1))
		assertEquals(CONTROL_ROW_HEIGHT + grown, body.topOf(2))
	}

	@Test
	fun `a click under an open list still lands on the control it looks like it hits`() {
		val listed = DropdownControl(SelectorSetting("s", "A", OPTIONS))
		val below = ToggleControl(BooleanSetting("b"))
		val body = ControlBody(listOf(listed, below))
		listed.press(0, 0, 100)
		val grown = listed.height

		assertEquals(0, body.indexAt(grown - 1))
		assertEquals(1, body.indexAt(grown))
		assertEquals(below, body.at(body.indexAt(grown + CONTROL_ROW_HEIGHT - 1)))
		assertEquals(ClickGuiShell.NONE, body.indexAt(grown + CONTROL_ROW_HEIGHT))
		assertEquals(ClickGuiShell.NONE, body.indexAt(-1))
	}

	@Test
	fun `a hidden control takes no room and no clicks`() {
		val gate = BooleanSetting("gate", default = false)
		val body = ControlBody(
			listOf(
				controlFor(BooleanSetting("shown"))!!,
				controlFor(BooleanSetting("gated").withDependency { gate.on })!!
			)
		)

		assertEquals(CONTROL_ROW_HEIGHT, body.height)
		assertEquals(ClickGuiShell.NONE, body.indexAt(CONTROL_ROW_HEIGHT))

		gate.value = true
		assertEquals(2 * CONTROL_ROW_HEIGHT, body.height)
		assertEquals(1, body.indexAt(CONTROL_ROW_HEIGHT))
	}

	@Test
	fun `slider press and drag set the value from mouse position and track drag`() {
		val setting = NumberSetting("n", default = 0.0, min = 0.0, max = 10.0, step = 1.0)
		val control = SliderControl(setting)
		assertEquals(ControlPress.TRACK, control.press(50, 0, 100))
		assertEquals(5.0, setting.value)
		control.drag(100, 0, 100)
		assertEquals(10.0, setting.value)
		control.drag(-20, 0, 100)
		assertEquals(0.0, setting.value)
	}

	@Test
	fun `slider maps mouse position onto the setting step`() {
		val setting = NumberSetting("n", default = 0.0, min = 0.0, max = 100.0, step = 5.0)
		val control = SliderControl(setting)
		control.press(23, 0, 100)
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
		assertEquals(ControlPress.FOCUS, control.press(0, 0, 100))
		control.charTyped('c'.code)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals("abc", setting.value)
	}

	@Test
	fun `text control respects maxLength and backspace`() {
		val setting = StringSetting("s", default = "", maxLength = 3)
		val control = TextControl(setting)
		control.press(0, 0, 100)
		"abcd".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals("ab", setting.value)
	}

	@Test
	fun `text control escape cancels without committing`() {
		val setting = StringSetting("s", default = "keep", maxLength = 10)
		val control = TextControl(setting)
		control.press(0, 0, 100)
		control.charTyped('x'.code)
		assertEquals(ControlKey.CANCELLED, control.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0))
		assertEquals("keep", setting.value)
	}

	@Test
	fun `text control commits pending edit on blur`() {
		val setting = StringSetting("s", default = "a", maxLength = 10)
		val control = TextControl(setting)
		control.press(0, 0, 100)
		control.charTyped('b'.code)
		assertTrue(control.blur())
		assertEquals("ab", setting.value)
	}

	@Test
	fun `color control parses six-digit hex on commit`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		assertEquals(ControlPress.FOCUS, control.press(0, 0, 100))
		"FF8000".forEach { control.charTyped(it.code) }
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(Color.rgba(255, 128, 0).argb, setting.value.argb)
	}

	@Test
	fun `color control honors allowAlpha with eight digits`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0, 255), allowAlpha = true)
		val control = ColorControl(setting)
		control.press(0, 0, 100)
		"112233AA".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_ENTER, 0)
		assertEquals(Color.rgba(0x11, 0x22, 0x33, 0xAA).argb, setting.value.argb)
	}

	@Test
	fun `color control forces opaque when alpha is disallowed`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(0, 0, 100)
		"abcdef".forEach { control.charTyped(it.code) }
		control.keyPressed(GLFW.GLFW_KEY_ENTER, 0)
		assertEquals(0xFF, setting.value.alpha)
	}

	@Test
	fun `color control ignores non-hex and short input`() {
		val setting = ColorSetting("c", Color.rgba(10, 20, 30), allowAlpha = false)
		val control = ColorControl(setting)
		val before = setting.value.argb
		control.press(0, 0, 100)
		"GG".forEach { control.charTyped(it.code) }
		"12".forEach { control.charTyped(it.code) }
		assertEquals(ControlKey.CANCELLED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(before, setting.value.argb)
	}

	@Test
	fun `color control rejects a digit that does not fit the character it would store`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(0, 0, 100)
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
		const val WIDTH = 100
		val SQUARE_RIGHT = pickerStripLeft(WIDTH) - 1
		val OPTIONS = listOf("A", "B", "C")
	}

	@Test
	fun `color control opens its picker from the swatch and closes it again`() {
		val setting = ColorSetting("c", Color.rgba(255, 128, 0), allowAlpha = false)
		val control = ColorControl(setting)
		assertEquals(CONTROL_ROW_HEIGHT, control.height)

		assertEquals(ControlPress.RESIZED, control.press(WIDTH - 1, 0, WIDTH))
		assertTrue(control.expanded)
		assertTrue(control.height > CONTROL_ROW_HEIGHT) { "an open picker must make its control taller" }

		assertEquals(ControlPress.RESIZED, control.press(WIDTH - 1, 0, WIDTH))
		assertFalse(control.expanded)
		assertEquals(CONTROL_ROW_HEIGHT, control.height)
		assertEquals(Color.rgba(255, 128, 0).argb, setting.value.argb)
	}

	@Test
	fun `the rest of the color row still opens the hex field`() {
		val control = ColorControl(ColorSetting("c", Color.rgba(0, 0, 0)))

		assertEquals(ControlPress.FOCUS, control.press(0, 0, WIDTH))
		assertFalse(control.expanded)
	}

	@Test
	fun `collapsing an open picker reports the change once and gives the room back`() {
		val control = ColorControl(ColorSetting("c", Color.rgba(0, 0, 0)))
		assertFalse(control.collapse())

		control.press(WIDTH - 1, 0, WIDTH)
		assertTrue(control.collapse())
		assertFalse(control.collapse())
		assertEquals(CONTROL_ROW_HEIGHT, control.height)
	}

	@Test
	fun `an open picker takes no room while its setting is hidden`() {
		val gate = BooleanSetting("gate", default = true)
		val control = ColorControl(ColorSetting("c", Color.rgba(0, 0, 0)).withDependency { gate.on })
		control.press(WIDTH - 1, 0, WIDTH)
		assertEquals(control.height, control.extent)

		gate.value = false
		assertEquals(0, control.extent)
	}

	@Test
	fun `dragging the square sets saturation and brightness and pins at its edges`() {
		val setting = ColorSetting("c", Color.rgba(255, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(WIDTH - 1, 0, WIDTH)

		assertEquals(ControlPress.TRACK, control.press(SQUARE_RIGHT, PICKER_SQUARE_TOP, WIDTH))
		assertEquals(Color.rgba(255, 0, 0).argb, setting.value.argb)

		control.drag(PICKER_PAD, PICKER_SQUARE_TOP, WIDTH)
		assertEquals(Color.rgba(255, 255, 255).argb, setting.value.argb)

		control.drag(-40, PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT + 40, WIDTH)
		assertEquals(Color.rgba(0, 0, 0).argb, setting.value.argb)
	}

	@Test
	fun `the picker keeps the hue the user chose through white and black`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 255), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(WIDTH - 1, 0, WIDTH)

		control.press(PICKER_PAD, PICKER_SQUARE_TOP, WIDTH)
		assertEquals(Color.rgba(255, 255, 255).argb, setting.value.argb)

		control.drag(SQUARE_RIGHT, PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT, WIDTH)
		assertEquals(Color.rgba(0, 0, 0).argb, setting.value.argb)

		control.drag(SQUARE_RIGHT, PICKER_SQUARE_TOP, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb)
	}

	@Test
	fun `dragging the hue strip walks the spectrum and pins at its ends`() {
		val setting = ColorSetting("c", Color.rgba(255, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(WIDTH - 1, 0, WIDTH)

		assertEquals(ControlPress.TRACK, control.press(pickerStripLeft(WIDTH), PICKER_SQUARE_TOP, WIDTH))
		assertEquals(Color.rgba(255, 0, 0).argb, setting.value.argb)

		control.drag(pickerStripLeft(WIDTH), PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT / 2, WIDTH)
		assertEquals(Color.rgba(0, 255, 255).argb, setting.value.argb)

		control.drag(pickerStripLeft(WIDTH), -50, WIDTH)
		assertEquals(Color.rgba(255, 0, 0).argb, setting.value.argb)
	}

	@Test
	fun `a press on the picker's padding leaves the colour alone`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 255), allowAlpha = true)
		val control = ColorControl(setting)
		control.press(WIDTH - 1, 0, WIDTH)

		control.press(pickerStripLeft(WIDTH), PICKER_SQUARE_TOP - 1, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb) { "padding above the hue strip must not reset the hue" }

		control.press(PICKER_PAD, PICKER_SQUARE_TOP - 1, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb)

		control.press(PICKER_PAD, PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb)

		control.press(PICKER_PAD, control.height - 1, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb) { "the panel's bottom padding must not set alpha" }
	}

	@Test
	fun `a drag that begins on the padding and crosses the square still picks nothing`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 255), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(WIDTH - 1, 0, WIDTH)

		control.press(PICKER_PAD, PICKER_SQUARE_TOP - 1, WIDTH)
		control.drag(PICKER_PAD, PICKER_SQUARE_TOP, WIDTH)

		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb)
	}

	@Test
	fun `the alpha strip exists only where the setting allows alpha`() {
		val opaque = ColorControl(ColorSetting("c", Color.rgba(10, 20, 30), allowAlpha = false))
		val faded = ColorSetting("c", Color.rgba(10, 20, 30, 255), allowAlpha = true)
		val fadedControl = ColorControl(faded)
		opaque.press(WIDTH - 1, 0, WIDTH)
		fadedControl.press(WIDTH - 1, 0, WIDTH)

		assertTrue(opaque.height <= PICKER_ALPHA_TOP) { "a picker without alpha must not reach the alpha strip" }
		assertTrue(fadedControl.height > opaque.height)

		fadedControl.press(PICKER_PAD, PICKER_ALPHA_TOP, WIDTH)
		assertEquals(Color.rgba(10, 20, 30, 0).argb, faded.value.argb)

		fadedControl.drag(WIDTH, PICKER_ALPHA_TOP, WIDTH)
		assertEquals(Color.rgba(10, 20, 30, 255).argb, faded.value.argb)
	}

	@Test
	fun `the hex field and the picker agree in both directions`() {
		val setting = ColorSetting("c", Color.rgba(0, 0, 0), allowAlpha = false)
		val control = ColorControl(setting)
		control.press(0, 0, WIDTH)
		"0000FF".forEach { control.charTyped(it.code) }
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))

		control.press(WIDTH - 1, 0, WIDTH)
		control.press(SQUARE_RIGHT, PICKER_SQUARE_TOP, WIDTH)
		assertEquals(Color.rgba(0, 0, 255).argb, setting.value.argb)

		control.drag(PICKER_PAD + 30, PICKER_SQUARE_TOP + 17, WIDTH)
		val picked = setting.value
		control.press(0, 0, WIDTH)
		"%02X%02X%02X".format(picked.red, picked.green, picked.blue).forEach { control.charTyped(it.code) }

		assertEquals(ControlKey.CANCELLED, control.keyPressed(GLFW.GLFW_KEY_ENTER, 0))
		assertEquals(picked.argb, setting.value.argb)
	}

	@Test
	fun `keybind control arms and binds a key`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_KEY_UNKNOWN)
		val control = KeybindControl(setting)
		assertEquals(ControlPress.FOCUS, control.press(0, 0, 100))
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_J, 0))
		assertEquals(GLFW.GLFW_KEY_J, setting.value)
	}

	@Test
	fun `keybind control unbinds on escape`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_KEY_J)
		val control = KeybindControl(setting)
		control.press(0, 0, 100)
		assertEquals(ControlKey.COMMITTED, control.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0))
		assertEquals(GLFW.GLFW_KEY_UNKNOWN, setting.value)
	}

	@Test
	fun `keybind control captures any mouse button`() {
		val setting = KeybindSetting("k")
		val control = KeybindControl(setting)
		control.press(0, 0, 100)
		assertEquals(ControlKey.COMMITTED, control.captureMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, setting.value)
	}

	@Test
	fun `keybind control reports no change when the mouse button is unchanged`() {
		val setting = KeybindSetting("k", default = GLFW.GLFW_MOUSE_BUTTON_1)
		val control = KeybindControl(setting)
		control.press(0, 0, 100)
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
		assertEquals(ControlPress.INVOKED, control.press(0, 0, 100))
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

		assertNull(control.press(0, 0, 100))
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

		assertNull(control.press(0, 0, 100))
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
