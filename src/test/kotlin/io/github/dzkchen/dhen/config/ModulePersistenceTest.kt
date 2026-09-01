package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.json
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
	fun `the retired Sound Manager and Arrow Hit Sound blocks are migrated out and the rest is left alone`(@TempDir dir: Path) {
		val path = dir.resolve("modules.json")
		Files.writeString(
			path,
			"""{"modules":{"Sound Manager":{"enabled":true,"settings":{"Open Sound Manager":1}},"Arrow Hit Sound":{"enabled":true,"settings":{"Sound":"minecraft:block.note_block.harp"}},"Sample":{"enabled":true}}}"""
		)

		val loaded = ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()

		val modules = loaded.getAsJsonObject("modules")
		assertFalse(modules.has("Sound Manager"))
		assertFalse(modules.has("Arrow Hit Sound"))
		assertTrue(modules.has("Sample"))
		assertEquals(ModulePersistence.version, loaded.get("version").asInt)
	}

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

		val file = json(Files.readString(path))
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
		val doc = json("""{"modules":{"Sample":{"enabled":"yes","settings":{"Speed":"fast","Label":"kept"}}}}""")

		ModulePersistence.apply(manager, doc)

		assertTrue(module.enabled)
		assertEquals(3.0, module.numberSetting.value)
		assertEquals("kept", module.stringSetting.value)
	}

	@Test
	fun `a stored value outside the current range is clamped on load`() {
		val manager = ModuleManager()
		val module = SampleModule().also { manager.register(it) }
		val doc = json("""{"modules":{"Sample":{"enabled":false,"settings":{"Speed":99.0}}}}""")
		manager.enable(module.name)

		ModulePersistence.apply(manager, doc)

		assertFalse(module.enabled)
		assertEquals(10.0, module.numberSetting.value)
	}

	@Test
	fun `a scoreboard line list saved before the long tail gains the new lines in their own places`(@TempDir dir: Path) {
		val path = dir.resolve("modules.json")
		Files.writeString(
			path,
			"""{"modules":{"Custom Scoreboard":{"enabled":true,"settings":{"Lines":""" +
				"""["Lobby Code","Separator 1","Date","Time","Island","Location","Separator 2","Purse","Bits",""" +
				""""Separator 3","Quiver","Separator 4","Slayer","Party","Footer","Extra"]}}}}"""
		)

		val loaded = ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()

		val lines = loaded.getAsJsonObject("modules")
			.getAsJsonObject("Custom Scoreboard")
			.getAsJsonObject("settings")
			.getAsJsonArray("Lines")
			.map { it.asString }

		assertEquals(
			listOf(
				"Lobby Code", "Separator 1", "Date", "Time", "Island", "Player Count", "Location", "Visiting",
				"Profile", "Separator 2", "Purse", "Motes", "Bank", "Bits", "Copper", "Sowdust", "Gems", "Heat",
				"Cold", "North Stars", "Soulflow", "Separator 3", "Cookie Buff", "Quiver", "Power", "Tuning",
				"Separator 4", "Objective", "Slayer", "Powder", "Mayor", "Party", "Footer", "Extra"
			),
			lines
		)
	}

	@Test
	fun `a scoreboard line the player removed is not put back, and its followers still land`(@TempDir dir: Path) {
		val path = dir.resolve("modules.json")
		Files.writeString(
			path,
			"""{"modules":{"Custom Scoreboard":{"enabled":true,"settings":{"Lines":["Island","Purse","Extra"]}}}}"""
		)

		val loaded = ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()

		val lines = loaded.getAsJsonObject("modules")
			.getAsJsonObject("Custom Scoreboard")
			.getAsJsonObject("settings")
			.getAsJsonArray("Lines")
			.map { it.asString }

		assertEquals(listOf("Island", "Player Count"), lines.take(2))
		assertFalse(lines.contains("Bits"))
		assertTrue(lines.containsAll(listOf("Motes", "Bank", "Copper", "Cookie Buff", "Mayor", "Powder")))
	}

	@Test
	fun `an unknown module in the file is ignored`() {
		val manager = ModuleManager()
		val module = SampleModule().also { manager.register(it) }
		val doc = json("""{"modules":{"Gone":{"enabled":true},"Sample":{"enabled":true}}}""")

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
