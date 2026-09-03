package io.github.dzkchen.dhen.ui.hud

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.json
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HudPersistenceTest {
	@Test
	fun `a reset element leaves the file it was written into`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("modules.json")
		val manager = ModuleManager()
		val module = OverlayModule().also { manager.register(it) }
		manager.enable(module.name)
		val store = ConfigStore(
			path,
			CoroutineScope(Dispatchers.IO),
			migrations = ModulePersistence.migrations,
			authoritative = ModulePersistence.authoritative,
			debounce = {}
		)

		module.status.offsetX = 300
		store.save(ModulePersistence.snapshot(manager)).join()
		assertEquals(300, hudBlock(path).getAsJsonObject("Status").get("x").asInt)

		module.status.resetToDeclared()
		store.save(ModulePersistence.snapshot(manager)).join()

		assertEquals(0, hudBlock(path).size())
	}

	@Test
	fun `element layout survives a simulated restart`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("modules.json")

		val saved = ModuleManager()
		val before = OverlayModule().also { saved.register(it) }
		saved.enable(before.name)
		before.status.anchor = HudAnchor.BOTTOM_RIGHT
		before.status.offsetX = -12
		before.status.offsetY = -20
		before.status.scale = 1.5f
		before.hidden.visible = false

		ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations, debounce = {})
			.save(ModulePersistence.snapshot(saved)).join()

		val file = json(Files.readString(path))
		val entry = file.getAsJsonObject("modules").getAsJsonObject("Overlay").getAsJsonObject("hud")
		assertEquals("BOTTOM_RIGHT", entry.getAsJsonObject("Status").get("anchor").asString)

		val restored = ModuleManager()
		val after = OverlayModule().also { restored.register(it) }
		ModulePersistence.apply(
			restored,
			ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()
		)

		assertEquals(HudAnchor.BOTTOM_RIGHT, after.status.anchor)
		assertEquals(-12, after.status.offsetX)
		assertEquals(-20, after.status.offsetY)
		assertEquals(1.5f, after.status.scale)
		assertTrue(after.status.visible)
		assertFalse(after.hidden.visible)
	}

	@Test
	fun `a module without elements writes no hud entry`() {
		val manager = ModuleManager()
		manager.register(PlainModule())

		val entry = ModulePersistence.snapshot(manager).getAsJsonObject("modules").getAsJsonObject("Plain")

		assertFalse(entry.has("hud"))
	}

	@Test
	fun `unknown elements in the document are ignored`() {
		val element = FixedHudElement("Status", offsetX = 3)
		val doc = JsonObject().apply { add("Gone", JsonObject().apply { addProperty("x", 40) }) }

		HudPersistence.apply(listOf(element), doc)

		assertEquals(3, element.offsetX)
	}

	@Test
	fun `malformed values keep the current layout`() {
		val element = FixedHudElement("Status", anchor = HudAnchor.TOP_RIGHT, offsetX = 3, offsetY = 7)
		val doc = JsonObject().apply {
			add("Status", JsonObject().apply {
				addProperty("anchor", "SOMEWHERE_ELSE")
				addProperty("x", "left")
				addProperty("visible", "yes")
			})
		}

		HudPersistence.apply(listOf(element), doc)

		assertEquals(HudAnchor.TOP_RIGHT, element.anchor)
		assertEquals(3, element.offsetX)
		assertEquals(7, element.offsetY)
		assertTrue(element.visible)
	}

	@Test
	fun `an unreadable number keeps the layout instead of failing the load`() {
		val manager = ModuleManager()
		val module = OverlayModule().also { manager.register(it) }
		val doc = json("""{"modules":{"Overlay":{"enabled":true,"hud":{"Status":{"x":1e2147483648,"y":9}}}}}""")

		ModulePersistence.apply(manager, doc)

		assertEquals(0, module.status.offsetX)
		assertEquals(9, module.status.offsetY)
		assertTrue(module.enabled)
	}

	@Test
	fun `the plate flag defaults off and is switched by the file`() {
		val element = FixedHudElement("Status")
		val doc = JsonObject().apply {
			add("Status", JsonObject().apply { addProperty("background", true) })
		}

		assertFalse(element.background)
		HudPersistence.apply(listOf(element), doc)

		assertTrue(element.background)
		assertTrue(HudPersistence.snapshot(listOf(element)).getAsJsonObject("Status").get("background").asBoolean)
	}

	@Test
	fun `a reset restores the plate the module declared`() {
		val plain = FixedHudElement("Status")
		val plated = FixedHudElement("Plated", background = true)
		plain.background = true
		plated.background = false

		assertTrue(plain.resetToDeclared())
		assertTrue(plated.resetToDeclared())
		assertFalse(plain.background)
		assertTrue(plated.background)
	}

	@Test
	fun `an out of range scale is clamped on load`() {
		val element = FixedHudElement("Status")
		val doc = JsonObject().apply {
			add("Status", JsonObject().apply { addProperty("scale", 40.0f) })
		}

		HudPersistence.apply(listOf(element), doc)

		assertEquals(HudElement.MAX_SCALE, element.scale)
	}

	private fun hudBlock(path: Path): JsonObject =
		json(Files.readString(path)).getAsJsonObject("modules").getAsJsonObject("Overlay").getAsJsonObject("hud")

	private class OverlayModule : Module(
		name = "Overlay",
		category = Category.VISUAL,
		description = "Fixture for HUD persistence."
	) {
		val status = hud(FixedHudElement("Status"))
		val hidden = hud(FixedHudElement("Hidden"))
	}

	private class PlainModule : Module(
		name = "Plain",
		category = Category.QOL,
		description = "Fixture without HUD elements."
	)
}
