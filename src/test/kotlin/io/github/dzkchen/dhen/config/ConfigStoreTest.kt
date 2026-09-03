package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
	fun `a config from a newer build keeps its version and runs no migration`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":5,"known":1,"future":{"keep":"yes"}}""")
		val ran = mutableListOf<String>()
		val store = ConfigStore(
			path,
			CoroutineScope(Dispatchers.IO),
			migrations = listOf({ ran += "first" }, { ran += "second" }),
			debounce = {}
		)

		val doc = store.load()
		store.save(JsonObject().apply { addProperty("known", 2) }).join()

		assertTrue(ran.isEmpty())
		assertEquals(5, doc.get("version").asInt)
		val written = json(Files.readString(path))
		assertEquals(5, written.get("version").asInt)
		assertEquals(2, written.get("known").asInt)
		assertEquals("yes", written.getAsJsonObject("future").get("keep").asString)
	}

	@Test
	fun `an upgrade replays only the migrations above the file's version`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("core.json")
		Files.writeString(path, """{"version":1,"known":1}""")
		val ran = mutableListOf<String>()
		val store = ConfigStore(
			path,
			CoroutineScope(Dispatchers.IO),
			migrations = listOf({ ran += "first" }, { ran += "second" }, { ran += "third" }),
			debounce = {}
		)

		val doc = store.load()
		store.save(JsonObject().apply { addProperty("known", 2) }).join()

		assertEquals(listOf("second", "third"), ran)
		assertEquals(3, doc.get("version").asInt)
		assertEquals(3, json(Files.readString(path)).get("version").asInt)
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

		val written = json(Files.readString(path))
		assertEquals(2, written.get("known").asInt)
		assertEquals("yes", written.getAsJsonObject("mystery").get("keep").asString)
	}

	@Test
	fun `an authoritative key is written whole while every other key still merges`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("sounds.json")
		Files.writeString(path, """{"version":0,"rules":{"kept":{"volume":0.5},"dropped":{"volume":0.25}},"other":{"mystery":1}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), authoritative = setOf("rules"), debounce = {})
		store.load()

		store.save(json("""{"rules":{"kept":{"volume":0.5}},"other":{"fresh":2}}""")).join()

		val written = json(Files.readString(path))
		assertEquals(setOf("kept"), written.getAsJsonObject("rules").keySet())
		assertEquals(1, written.getAsJsonObject("other").get("mystery").asInt)
		assertEquals(2, written.getAsJsonObject("other").get("fresh").asInt)
	}

	@Test
	fun `an authoritative key is written whole wherever it is nested`(@TempDir dir: Path) = runBlocking {
		val path = dir.resolve("modules.json")
		Files.writeString(path, """{"version":0,"modules":{"Utility HUDs":{"enabled":true,"hud":{"FPS":{"x":300}}}}}""")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), authoritative = setOf("hud"), debounce = {})
		store.load()

		store.save(json("""{"modules":{"Utility HUDs":{"enabled":true,"hud":{}}}}""")).join()

		val written = json(Files.readString(path))
		val module = written.getAsJsonObject("modules").getAsJsonObject("Utility HUDs")
		assertEquals(0, module.getAsJsonObject("hud").size())
		assertTrue(module.get("enabled").asBoolean)
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
		assertEquals(3, writtenNumber(path))
	}

	@Test
	fun `flush writes the pending snapshot without waiting for the debounce`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = { awaitCancellation() })
		store.save(numberDoc(7))

		store.flush()

		assertEquals(1, store.writeCount)
		assertEquals(7, writtenNumber(path))
	}

	@Test
	fun `flush with nothing pending writes nothing`(@TempDir dir: Path) {
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = {})

		store.flush()

		assertEquals(0, store.writeCount)
		assertFalse(Files.exists(path))
	}

	@Test
	fun `flush after the debounced write landed does not write twice`(@TempDir dir: Path) = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val path = dir.resolve("core.json")
		val store = ConfigStore(path, CoroutineScope(Dispatchers.IO), debounce = { gate.await() })

		val job = store.save(numberDoc(4))
		gate.complete(Unit)
		job.join()
		store.flush()

		assertEquals(1, store.writeCount)
		assertEquals(4, writtenNumber(path))
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
		assertEquals(2, json(Files.readString(path)).get("known").asInt)
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

	private fun writtenNumber(path: Path): Int =
		json(Files.readString(path)).get("n").asInt

	private fun numberDoc(n: Int): JsonObject = JsonObject().apply { addProperty("n", n) }
}
