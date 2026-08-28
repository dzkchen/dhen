package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.gui.ClickGuiState
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenFont
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.ui.hud.FixedHudElement
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.json
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CorePersistenceTest {
	@Test
	fun `a document written by the draggable shell loads without its panel block`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(
			path,
			"""{"panels":{"DEV":{"x":12,"y":34,"collapsed":true}},"effects":{"reduced":true}}"""
		)
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations)

		val doc = store.load()

		assertFalse(doc.has("panels"))
		assertTrue(doc.getAsJsonObject("client").get(Effects.REDUCED).asBoolean)
	}

	@Test
	fun `the effects flag moves into the client block and its old block goes`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":1,"effects":{"reduced":true},"clickgui":{"collapsed":["DEV"]}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations)

		val view = CorePersistence.apply(store.load()).clickGui

		assertTrue(Effects.reduced)
		assertEquals(setOf("DEV"), view.collapsed)
	}

	@Test
	fun `the accordion and arrow-key keys are stripped from a file that still carries them`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(
			path,
			"""{"clickgui":{"collapsed":["DEV"],"opened":["MISC"]},""" +
				""""client":{"Layout":"Accordion","Arrow keys":"Jump columns","Accent color":-1}}"""
		)
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations)

		val doc = store.load()

		assertFalse(doc.getAsJsonObject("clickgui").has("opened"))
		assertFalse(doc.getAsJsonObject("client").has("Layout"))
		assertFalse(doc.getAsJsonObject("client").has("Arrow keys"))
		assertTrue(doc.getAsJsonObject("client").has("Accent color"))
		assertEquals(setOf("DEV"), CorePersistence.apply(doc).clickGui.collapsed)
	}

	@Test
	fun `a document with no effects block at all migrates without inventing one`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":1,"clickgui":{"collapsed":[]}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations)

		val doc = store.load()

		assertFalse(doc.has("effects"))
		assertFalse(doc.getAsJsonObject("client")?.has(Effects.REDUCED) ?: false)
	}

	@Test
	fun `a client value already in the file outranks the block being migrated`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(
			path,
			"""{"version":1,"effects":{"reduced":true},"client":{"${Effects.REDUCED}":false}}"""
		)
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations)

		CorePersistence.apply(store.load())

		assertFalse(Effects.reduced)
	}

	@Test
	fun `the panel block is gone from the file the merge base rewrites`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"panels":{"DEV":{"x":12,"y":34,"collapsed":true}}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), CorePersistence.migrations, debounce = {})
		store.load()

		store.save(CorePersistence.snapshot(ClickGuiState(collapsed = linkedSetOf("DEV")), welcomeShown = false)).join()

		val written = json(Files.readString(path))
		assertFalse(written.has("panels"))
		assertEquals("DEV", written.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
	}

	@AfterEach
	fun restoreClientDefaults() {
		DhenFont.resetForTest()
		Effects.reduced = false
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.dhenFont.value = true
		ClientPrefs.sync()
	}

	@Test
	fun `the client settings survive a save and load round trip`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), CorePersistence.migrations, debounce = {})
		store.load()
		Effects.reduced = true
		ClientPrefs.accent.value = Color(TEAL)
		ClientPrefs.dhenFont.value = false

		store.save(CorePersistence.snapshot(ClickGuiState(), welcomeShown = false)).join()
		Effects.reduced = false
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.dhenFont.value = true
		CorePersistence.apply(ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations).load())

		assertTrue(Effects.reduced)
		assertEquals(TEAL, ClientPrefs.accent.value.argb)
		assertEquals(TEAL, DhenPalette.accent)
		assertFalse(ClientPrefs.dhenFont.value)
	}

	@Test
	fun `the first-run flag is absent on a fresh config and survives a round trip`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), CorePersistence.migrations, debounce = {})

		assertFalse(CorePersistence.apply(store.load()).welcomeShown)

		store.save(CorePersistence.snapshot(ClickGuiState(), welcomeShown = true)).join()

		val restored = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations).load()
		assertTrue(CorePersistence.apply(restored).welcomeShown)
	}

	@Test
	fun `a core HUD layout survives the core document round trip`() {
		val saved = FixedHudElement("Alerts")
		saved.anchor = HudAnchor.BOTTOM_RIGHT
		saved.offsetX = -18
		saved.scale = 1.4f
		val doc = CorePersistence.snapshot(ClickGuiState(), welcomeShown = false, listOf(saved))
		val restored = FixedHudElement("Alerts")

		CorePersistence.apply(doc, listOf(restored))

		assertEquals(HudAnchor.BOTTOM_RIGHT, restored.anchor)
		assertEquals(-18, restored.offsetX)
		assertEquals(1.4f, restored.scale)
	}

	private companion object {
		val TEAL = 0xFF55D6C2u.toInt()
	}
}
