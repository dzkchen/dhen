package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
		val store = ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), migrations = listOf({ ran += "m" }))

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

	private fun numberDoc(n: Int): JsonObject = JsonObject().apply { addProperty("n", n) }
}
