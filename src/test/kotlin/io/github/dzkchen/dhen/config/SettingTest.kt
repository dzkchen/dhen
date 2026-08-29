package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.Setting.Companion.derived
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class SettingTest {
	@Test
	fun `boolean delegate reads and writes`() {
		val module = SampleModule()

		assertTrue(module.flag)
		module.flag = false
		assertFalse(module.flag)
		assertFalse(module.flagSetting.value)
	}

	@Test
	fun `string delegate truncates to max length`() {
		val module = SampleModule()

		module.label = "short"
		assertEquals("short", module.label)
		module.label = "way too long"
		assertEquals("way t", module.label)
	}

	@Test
	fun `number delegate reads and writes through the module`() {
		val module = SampleModule()

		assertEquals(3.0, module.speed)
		module.speed = 7.3
		assertEquals(7.5, module.speed)
		assertEquals(7.5, module.currentSpeed())
	}

	@Test
	fun `number setting shares storage between amount and value`() {
		val setting = NumberSetting("n", default = 3.0, min = 0.0, max = 10.0, step = 1.0)

		setting.value = 7.0
		assertEquals(7.0, setting.amount)
		setting.amount = 2.0
		assertEquals(2.0, setting.value)
	}

	@Test
	fun `number setting coerces a write that skips value`() {
		val setting = NumberSetting("n", default = 1.0, min = 1.0, max = 4.0, step = 0.5)

		setting.amount = 9.0
		assertEquals(4.0, setting.value)
		setting.amount = 2.2
		assertEquals(2.0, setting.value)
	}

	@Test
	fun `boolean setting shares storage between on and value`() {
		val setting = BooleanSetting("b", true)

		setting.value = false
		assertFalse(setting.on)
		setting.on = true
		assertTrue(setting.value)
	}

	@Test
	fun `keybind setting shares storage between code and value`() {
		val setting = KeybindSetting("k")

		setting.value = GLFW.GLFW_KEY_G
		assertEquals(GLFW.GLFW_KEY_G, setting.code)
		setting.code = GLFW.GLFW_KEY_H
		assertEquals(GLFW.GLFW_KEY_H, setting.value)
	}

	@Test
	fun `number setting coerces into range and rounds to step`() {
		val setting = NumberSetting("n", default = 3.0, min = 0.0, max = 10.0, step = 2.0)

		assertEquals(4.0, setting.default)
		assertEquals(4.0, setting.value)
		setting.value = 100.0
		assertEquals(10.0, setting.value)
		setting.value = -5.0
		assertEquals(0.0, setting.value)
		setting.value = 5.4
		assertEquals(6.0, setting.value)
	}

	@Test
	fun `selector delegate reads the selected option as string`() {
		val module = SampleModule()

		assertEquals("Hold", module.mode)
		module.mode = "Toggle"
		assertEquals("Toggle", module.mode)
		assertEquals(1, module.modeSetting.index)
	}

	@Test
	fun `sound setting exposes registry choices with deterministic short names`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value()
		val setting = SoundSetting("Sound", harp)

		assertEquals("NOTE_BLOCK_HARP", SoundSetting.prettyName(harp.location()))
		assertEquals(SoundSetting.options.sortedByDescending { it.name }, SoundSetting.options)
		assertTrue(SoundSetting.options.any { it.sound == setting.value && it.identifier == harp.location() })
	}

	@Test
	fun `sound setting selects a registered identifier and rejects an unknown one`() {
		val setting = SoundSetting("Sound", SoundEvents.NOTE_BLOCK_HARP.value())
		val arrow = SoundEvents.ARROW_HIT_PLAYER

		setting.select(arrow.location())
		assertEquals(arrow, setting.value)
		assertThrows(IllegalArgumentException::class.java) {
			setting.select(Identifier.fromNamespaceAndPath("dhen", "missing"))
		}
	}

	@Test
	fun `sound setting codec writes and restores the registered identifier`() {
		val setting = SoundSetting("Sound", SoundEvents.NOTE_BLOCK_HARP.value())
		val arrow = SoundEvents.ARROW_HIT_PLAYER

		setting.value = arrow
		assertEquals(arrow.location().toString(), (SettingCodec.serialize(setting) as JsonPrimitive).asString)

		setting.value = SoundEvents.NOTE_BLOCK_HARP.value()
		SettingCodec.deserialize(setting, JsonPrimitive(arrow.location().toString()))
		assertEquals(arrow, setting.value)
	}

	@Test
	fun `sound setting codec isolates an invalid persisted identifier`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value()
		val setting = SoundSetting("Sound", harp)
		val block = JsonObject().apply { addProperty("Sound", "dhen:missing") }

		SettingCodec.readInto(block, listOf(setting), "Sound Fixture")

		assertEquals(harp, setting.value)
	}

	@Test
	fun `selector resolves default and rejects unknown options`() {
		val unknownDefault = SelectorSetting("m", default = "Nope", options = listOf("A", "B"))
		assertEquals("A", unknownDefault.value)

		unknownDefault.value = "B"
		assertEquals("B", unknownDefault.value)
		unknownDefault.value = "missing"
		assertEquals("A", unknownDefault.value)
	}

	@Test
	fun `a selector re-resolves its index when the options change under it`() {
		val selector = SelectorSetting("Theme", default = "Default", options = listOf("Default"))

		selector.options = listOf("Amber", "Default", "Ocean")
		assertEquals("Default", selector.value)
		assertEquals(1, selector.index)

		selector.value = "Ocean"
		selector.options = listOf("Default", "Ocean")
		assertEquals("Ocean", selector.value)
		assertEquals(1, selector.index)
	}

	@Test
	fun `a selector keeps the option it was asked for after that option disappears`() {
		val selector = SelectorSetting("Theme", default = "Default", options = listOf("Default", "Ocean"))
		selector.value = "Ocean"

		selector.options = listOf("Default")

		assertEquals("Default", selector.value)
		assertEquals(0, selector.index)
		assertEquals("Ocean", selector.preferred)

		selector.options = emptyList()

		assertEquals("Default", selector.value)
		assertEquals(0, selector.index)
		assertEquals("Ocean", selector.preferred)

		selector.options = listOf("Default", "Ocean")

		assertEquals("Ocean", selector.value)
		assertEquals("Ocean", selector.preferred)
	}

	@Test
	fun `cycling a selector makes the option it lands on the one it asks for`() {
		val selector = SelectorSetting("Theme", default = "Default", options = listOf("Default", "Ocean"))
		selector.value = "Amber"

		selector.index += 1

		assertEquals("Ocean", selector.value)
		assertEquals("Ocean", selector.preferred)

		selector.index += 1

		assertEquals("Default", selector.value)
		assertEquals("Default", selector.preferred)
	}

	@Test
	fun `a selector with one option ignores a press instead of forgetting the choice under it`() {
		val selector = SelectorSetting("Theme", default = "Default", options = listOf("Default", "Ocean"))
		selector.value = "Ocean"
		selector.options = listOf("Default")
		var presses = 0
		selector.changed = { presses++ }

		selector.index += 1

		assertEquals(0, presses)
		assertEquals("Default", selector.value)
		assertEquals("Ocean", selector.preferred)

		selector.options = listOf("Default", "Ocean")
		selector.index += 1

		assertEquals(1, presses)
		assertEquals("Default", selector.value)
	}

	@Test
	fun `a derived setting keeps its control and never reaches disk`() {
		val view = SelectorSetting("Sodium", default = "Off", options = listOf("Off", "On")).derived()
		view.value = "On"

		assertTrue(view.isVisible)
		assertNull(SettingCodec.serialize(view))
		assertTrue(SettingCodec.writeInto(JsonObject(), listOf(view)).entrySet().isEmpty())
	}

	@Test
	fun `a selector persists the option it was asked for, not the one it fell back to`() {
		val selector = SelectorSetting("Theme", default = "Default", options = listOf("Default", "Ocean"))
		selector.value = "Ocean"
		selector.options = listOf("Default")

		assertEquals("Ocean", (SettingCodec.serialize(selector) as JsonPrimitive).asString)
	}

	@Test
	fun `dependency controls visibility`() {
		val module = SampleModule()

		assertTrue(module.flagSetting.isVisible)
		assertTrue(module.gatedSetting.isVisible)

		module.flag = false
		assertFalse(module.gatedSetting.isVisible)
	}

	@Test
	fun `hide overrides visibility and preserves the concrete type`() {
		val setting: BooleanSetting = BooleanSetting("b", true).hide()
		assertFalse(setting.isVisible)
	}

	@Test
	fun `reset restores the default`() {
		val setting = NumberSetting("n", default = 4.0, min = 0.0, max = 10.0, step = 1.0)
		setting.value = 9.0
		setting.reset()
		assertEquals(4.0, setting.value)
	}

	@Test
	fun `color delegate reads and writes`() {
		val module = SampleModule()

		assertEquals(Color.rgba(255, 0, 0), module.color)
		module.color = Color.rgba(0, 255, 0)
		assertEquals(Color.rgba(0, 255, 0), module.color)
	}

	@Test
	fun `color setting forces opaque when alpha disallowed`() {
		val setting = ColorSetting("c", Color.rgba(10, 20, 30, alpha = 64))
		assertEquals(255, setting.default.alpha)

		setting.value = Color.rgba(1, 2, 3, alpha = 0)
		assertEquals(255, setting.value.alpha)
	}

	@Test
	fun `color setting keeps alpha when allowed`() {
		val setting = ColorSetting("c", Color.rgba(10, 20, 30, alpha = 64), allowAlpha = true)
		assertEquals(64, setting.default.alpha)

		setting.value = Color.rgba(1, 2, 3, alpha = 128)
		assertEquals(128, setting.value.alpha)
	}

	@Test
	fun `keybind defaults to unbound and reads and writes through the module`() {
		val module = SampleModule()

		assertEquals(GLFW.GLFW_KEY_UNKNOWN, module.key)
		assertFalse(module.keySetting.isBound)

		module.key = GLFW.GLFW_KEY_G
		assertEquals(GLFW.GLFW_KEY_G, module.key)
		assertTrue(module.keySetting.isBound)
	}

	@Test
	fun `action delegate exposes the callback`() {
		val module = SampleModule()

		module.run()
		assertEquals(1, module.runs)
	}

	@Test
	fun `settings enumerate in declaration order`() {
		val module = SampleModule()

		assertEquals(
			listOf("Flag", "Label", "Speed", "Mode", "Gated", "Color", "Key", "Run"),
			module.settings.map { it.name }
		)
	}

	@Test
	fun `auto sprint example shape compiles and wires dependency`() {
		val settings = AutoSprintFixture.settings

		assertEquals(listOf("Only in SkyBlock", "Mode", "Gated"), settings.map { it.name })

		val gated = settings[2]
		assertTrue(gated.isVisible)

		(settings[1] as SelectorSetting).value = "Toggle"
		assertFalse(gated.isVisible)
	}

	private class SampleModule : Module(
		name = "Sample",
		category = Category.QOL,
		description = "Fixture for setting delegates."
	) {
		val flagSetting = BooleanSetting("Flag", true)
		var flag by flagSetting

		val labelSetting = StringSetting("Label", "", maxLength = 5)
		var label by labelSetting

		val speedSetting = NumberSetting("Speed", 3.0, min = 0.0, max = 10.0, step = 0.5)
		var speed by speedSetting

		val modeSetting = SelectorSetting("Mode", "Hold", listOf("Hold", "Toggle"))
		var mode by modeSetting

		val gatedSetting = StringSetting("Gated", "x").withDependency { flag }

		@Suppress("unused")
		var gated by gatedSetting

		val colorSetting = ColorSetting("Color", Color.rgba(255, 0, 0))
		var color by colorSetting

		val keySetting = KeybindSetting("Key")
		var key by keySetting

		var runs = 0
		val runSetting = ActionSetting("Run", default = { runs++ })
		val run by runSetting

		fun currentSpeed(): Double = speedSetting.amount
	}

	@Suppress("unused")
	private object AutoSprintFixture : Module(
		name = "Auto Sprint",
		category = Category.QOL,
		description = "Keeps sprint held while moving."
	) {
		private val inSkyblockOnly by BooleanSetting("Only in SkyBlock", true)
		private val mode by SelectorSetting("Mode", "Hold", listOf("Hold", "Toggle"))
		private val gated by StringSetting("Gated", "x").withDependency { mode == "Hold" }
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
