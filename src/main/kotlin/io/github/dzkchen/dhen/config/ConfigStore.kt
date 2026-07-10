package io.github.dzkchen.dhen.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// Owns one JSON file: versioned load with ordered migrations, and a debounced,
// coalesced, atomic off-thread save that preserves fields the current code does
// not model (via a retained merge base).
class ConfigStore(
	private val path: Path,
	private val scope: CoroutineScope,
	private val migrations: List<(JsonObject) -> Unit> = emptyList(),
	private val debounce: suspend () -> Unit = { delay(DEFAULT_DEBOUNCE_MS) },
	private val gson: Gson = DEFAULT_GSON
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
			path.parent?.let { Files.createDirectories(it) }
			val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
			Files.writeString(tmp, gson.toJson(merged))
			move(tmp)
			writes++
		} catch (e: Exception) {
			log.error("Failed to write config {}", path, e)
		}
	}

	private fun move(tmp: Path) {
		try {
			Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: AtomicMoveNotSupportedException) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
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
		private const val DEFAULT_DEBOUNCE_MS = 1_000L
		private val DEFAULT_GSON: Gson = GsonBuilder().setPrettyPrinting().create()
	}
}
