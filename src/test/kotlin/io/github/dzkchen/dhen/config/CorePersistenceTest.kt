package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
		assertTrue(doc.getAsJsonObject("effects").get("reduced").asBoolean)
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
}
