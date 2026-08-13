package io.github.dzkchen.dhen.config

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path

class ModulePersistenceTest {
	@Test
	fun `enabled state and settings survive a simulated restart`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("modules.json")

		val saved = ModuleManager()
		val before = SampleModule().also { saved.register(it) }
		saved.enable(before.name)
		before.boolSetting.value = false
		before.numberSetting.value = 5.0
		before.stringSetting.value = "hello"
		before.selectorSetting.value = "Toggle"
		before.colorSetting.value = Color.rgba(10, 20, 30)
		before.keySetting.value = GLFW.GLFW_KEY_G
		before.run()

		ConfigStore(
			path,
			CoroutineScope(Dispatchers.IO),
			migrations = ModulePersistence.migrations,
			debounce = {}
		)
			.save(ModulePersistence.snapshot(saved)).join()

		val file = JsonParser.parseString(Files.readString(path)).asJsonObject
		assertEquals(ModulePersistence.version, file.get("version").asInt)
		val fileSettings = file.getAsJsonObject("modules").getAsJsonObject("Sample").getAsJsonObject("settings")
		assertFalse(fileSettings.has("Run"))

		val restored = ModuleManager()
		val after = SampleModule().also { restored.register(it) }
		ModulePersistence.apply(
			restored,
			ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()
		)

		assertTrue(after.enabled)
		assertFalse(after.boolSetting.value)
		assertEquals(5.0, after.numberSetting.value)
		assertEquals("hello", after.stringSetting.value)
		assertEquals("Toggle", after.selectorSetting.value)
		assertEquals(Color.rgba(10, 20, 30), after.colorSetting.value)
		assertEquals(GLFW.GLFW_KEY_G, after.keySetting.value)
		assertEquals(0, after.ran)
	}

	@Test
	fun `wrong-typed values are skipped and their neighbours still load`() {
		val manager = ModuleManager()
		val module = SampleModule().also { manager.register(it) }
		manager.enable(module.name)
		val doc = JsonParser.parseString(
			"""{"modules":{"Sample":{"enabled":"yes","settings":{"Speed":"fast","Label":"kept"}}}}"""
		).asJsonObject

		ModulePersistence.apply(manager, doc)

		assertTrue(module.enabled)
		assertEquals(3.0, module.numberSetting.value)
		assertEquals("kept", module.stringSetting.value)
	}

	@Test
	fun `a stored value outside the current range is clamped on load`() {
		val manager = ModuleManager()
		val module = SampleModule().also { manager.register(it) }
		val doc = JsonParser.parseString(
			"""{"modules":{"Sample":{"enabled":false,"settings":{"Speed":99.0}}}}"""
		).asJsonObject
		manager.enable(module.name)

		ModulePersistence.apply(manager, doc)

		assertFalse(module.enabled)
		assertEquals(10.0, module.numberSetting.value)
	}

	@Test
	fun `an unknown module in the file is ignored`() {
		val manager = ModuleManager()
		val module = SampleModule().also { manager.register(it) }
		val doc = JsonParser.parseString(
			"""{"modules":{"Gone":{"enabled":true},"Sample":{"enabled":true}}}"""
		).asJsonObject

		ModulePersistence.apply(manager, doc)

		assertTrue(module.enabled)
	}

	@Suppress("unused")
	private class SampleModule : Module(
		name = "Sample",
		category = Category.QOL,
		description = "Fixture for persistence."
	) {
		val boolSetting = BooleanSetting("Flag", true)
		private val flag by boolSetting

		val numberSetting = NumberSetting("Speed", 3.0, min = 0.0, max = 10.0, step = 1.0)
		private val speed by numberSetting

		val stringSetting = StringSetting("Label", "")
		private val label by stringSetting

		val selectorSetting = SelectorSetting("Mode", "Hold", listOf("Hold", "Toggle"))
		private val mode by selectorSetting

		val colorSetting = ColorSetting("Color", Color.rgba(255, 0, 0))
		private val color by colorSetting

		val keySetting = KeybindSetting("Key")
		private val key by keySetting

		var ran = 0
			private set
		val runSetting = ActionSetting("Run", default = { ran++ })
		private val run by runSetting

		fun run() = runSetting.value()
	}
}
