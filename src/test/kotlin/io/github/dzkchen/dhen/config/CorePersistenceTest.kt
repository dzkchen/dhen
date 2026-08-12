package io.github.dzkchen.dhen.config

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.Effects
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

		val collapsed = CorePersistence.apply(store.load())

		assertTrue(Effects.reduced)
		assertEquals(setOf("DEV"), collapsed)
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

		store.save(CorePersistence.snapshot(setOf("DEV"))).join()

		val written = JsonParser.parseString(Files.readString(path)).asJsonObject
		assertFalse(written.has("panels"))
		assertEquals("DEV", written.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
	}

	@AfterEach
	fun restoreClientDefaults() {
		Effects.reduced = false
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.sync()
	}

	@Test
	fun `the client settings survive a save and load round trip`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), CorePersistence.migrations, debounce = {})
		store.load()
		Effects.reduced = true
		ClientPrefs.accent.value = Color(TEAL)

		store.save(CorePersistence.snapshot(emptySet())).join()
		Effects.reduced = false
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		CorePersistence.apply(ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), CorePersistence.migrations).load())

		assertTrue(Effects.reduced)
		assertEquals(TEAL, ClientPrefs.accent.value.argb)
		assertEquals(TEAL, DhenPalette.accent)
	}

	private companion object {
		val TEAL = 0xFF55D6C2u.toInt()
	}
}
