package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
	fun `selector resolves default and rejects unknown options`() {
		val unknownDefault = SelectorSetting("m", default = "Nope", options = listOf("A", "B"))
		assertEquals("A", unknownDefault.value)

		unknownDefault.value = "B"
		assertEquals("B", unknownDefault.value)
		unknownDefault.value = "missing"
		assertEquals("A", unknownDefault.value)
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
	fun `settings enumerate in declaration order`() {
		val module = SampleModule()

		assertEquals(listOf("Flag", "Label", "Speed", "Mode", "Gated"), module.settings.map { it.name })
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
		var gated by gatedSetting
	}

	private object AutoSprintFixture : Module(
		name = "Auto Sprint",
		category = Category.QOL,
		description = "Keeps sprint held while moving."
	) {
		private val inSkyblockOnly by BooleanSetting("Only in SkyBlock", true)
		private val mode by SelectorSetting("Mode", "Hold", listOf("Hold", "Toggle"))
		private val gated by StringSetting("Gated", "x").withDependency { mode == "Hold" }
	}
}
