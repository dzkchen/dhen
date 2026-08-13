package io.github.dzkchen.dhen.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.util.JsonFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class ConfigStore(
	private val path: Path,
	private val scope: CoroutineScope,
	private val migrations: List<(JsonObject) -> Unit> = emptyList(),
	private val debounce: suspend () -> Unit = { delay(DEFAULT_DEBOUNCE) },
	private val gson: Gson = JsonFile.pretty
) {
	private val version: Int get() = migrations.size

	private val lock = Any()
	private var pending: JsonObject? = null
	private var writer: Job? = null

	@Volatile
	private var base: JsonObject = JsonObject()

	@Volatile
	private var writes = 0

	// Completed atomic writes; a diagnostic, also read by tests to assert coalescing.
	internal val writeCount: Int get() = writes

	fun load(): JsonObject {
		val doc = read() ?: JsonObject()
		val from = doc.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
		for (v in from until version) migrations.getOrNull(v)?.invoke(doc)
		doc.addProperty("version", version)
		base = doc.deepCopy()
		return doc
	}

	fun save(snapshot: JsonObject): Job = synchronized(lock) {
		pending = snapshot.deepCopy()
		writer?.takeIf { it.isActive }?.let { return it }
		scope.launch { drain() }.also { writer = it }
	}

	private suspend fun drain() {
		while (true) {
			debounce()
			val snapshot = synchronized(lock) { pending.also { pending = null } }
			if (snapshot == null) {
				synchronized(lock) { if (pending == null) { writer = null; return } }
				continue
			}
			write(snapshot)
		}
	}

	private fun write(snapshot: JsonObject) {
		val merged = deepMerge(base, snapshot)
		merged.addProperty("version", version)
		base = merged
		try {
			JsonFile.writeAtomic(path, gson.toJson(merged))
			writes++
		} catch (e: Exception) {
			log.error("Failed to write config {}", path, e)
		}
	}

	private fun read(): JsonObject? {
		if (!Files.exists(path)) return null
		val text = Files.readString(path)
		if (text.isBlank()) return null
		return try {
			JsonParser.parseString(text) as? JsonObject
		} catch (e: JsonParseException) {
			log.warn("Ignoring unparseable config {}", path, e)
			null
		}
	}

	private fun deepMerge(base: JsonObject, overlay: JsonObject): JsonObject {
		val result = base.deepCopy()
		for ((key, value) in overlay.entrySet()) {
			val existing = result.get(key)
			if (existing is JsonObject && value is JsonObject) result.add(key, deepMerge(existing, value))
			else result.add(key, value)
		}
		return result
	}

	private companion object {
		private val log = LoggerFactory.getLogger(ConfigStore::class.java)
		private val DEFAULT_DEBOUNCE = 1.seconds
	}
}
