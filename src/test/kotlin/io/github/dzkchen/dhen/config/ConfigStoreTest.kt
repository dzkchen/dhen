package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigStoreTest {
	@Test
	fun `migrations run in order to the current version`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":0}""")
		val steps = mutableListOf<String>()
		val first: (JsonObject) -> Unit = { doc -> steps += "first"; doc.addProperty("v", 1) }
		val second: (JsonObject) -> Unit = { doc ->
			steps += "second"
			require(doc.get("v").asInt == 1) { "second ran before first" }
			doc.addProperty("v", 2)
		}
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), migrations = listOf(first, second))

		val doc = store.load()

		assertEquals(listOf("first", "second"), steps)
		assertEquals(2, doc.get("version").asInt)
		assertEquals(2, doc.get("v").asInt)
	}

	@Test
	fun `a non-numeric version falls back instead of crashing load`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":"garbage","known":1}""")
		val ran = mutableListOf<String>()
		val migration: (JsonObject) -> Unit = { ran += "m" }
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), migrations = listOf(migration))

		val doc = store.load()

		assertEquals(listOf("m"), ran)
		assertEquals(1, doc.get("version").asInt)
		assertEquals(1, doc.get("known").asInt)
	}

	@Test
	fun `unknown fields survive a load then save round trip`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":0,"known":1,"mystery":{"keep":"yes"}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = {})
		store.load()

		store.save(JsonObject().apply { addProperty("known", 2) }).join()

		val written = JsonParser.parseString(Files.readString(path)).asJsonObject
		assertEquals(2, written.get("known").asInt)
		assertEquals("yes", written.getAsJsonObject("mystery").get("keep").asString)
	}

	@Test
	fun `debounce coalesces rapid saves into one write of the latest snapshot`(@TempDir dir: Path) = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = { gate.await() })

		val job = store.save(numberDoc(1))
		store.save(numberDoc(2))
		store.save(numberDoc(3))

		assertFalse(Files.exists(path))
		assertEquals(0, store.writeCount)

		gate.complete(Unit)
		job.join()

		assertEquals(1, store.writeCount)
		val written = JsonParser.parseString(Files.readString(path)).asJsonObject
		assertEquals(3, written.get("n").asInt)
	}

	@Test
	fun `a missing config loads as a fresh document`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined))

		val doc = store.load()

		assertEquals(0, doc.get("version").asInt)
		assertFalse(Files.exists(path))
	}

	@Test
	fun `a blank config loads as fresh and is left in place`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.writeString(path, "   ")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined))

		val doc = store.load()

		assertEquals(0, doc.get("version").asInt)
		assertTrue(Files.exists(path))
		assertFalse(Files.exists(unusable(path)))
	}

	@Test
	fun `a half-written config is set aside instead of being overwritten`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		val half = """{"version":0,"known":1,"mystery":{"kee"""
		Files.writeString(path, half)
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = {})

		val doc = store.load()
		store.save(JsonObject().apply { addProperty("known", 2) }).join()

		assertNull(doc.get("known"))
		assertEquals(half, Files.readString(unusable(path)))
		assertEquals(2, JsonParser.parseString(Files.readString(path)).asJsonObject.get("known").asInt)
	}

	@Test
	fun `an unreadable config does not escape load`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		Files.write(path, byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D))
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined))

		val doc = store.load()

		assertEquals(0, doc.get("version").asInt)
		assertTrue(Files.exists(unusable(path)))
	}

	private fun unusable(path: Path): Path = path.resolveSibling(path.fileName.toString() + ".unusable")

	private fun numberDoc(n: Int): JsonObject = JsonObject().apply { addProperty("n", n) }
}
