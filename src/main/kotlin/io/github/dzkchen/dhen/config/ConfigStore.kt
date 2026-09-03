package io.github.dzkchen.dhen.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.JsonFile
import io.github.dzkchen.dhen.util.int
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration.Companion.seconds

class ConfigStore(
	private val path: Path,
	private val scope: CoroutineScope,
	private val migrations: List<(JsonObject) -> Unit> = emptyList(),
	private val authoritative: Set<String> = emptySet(),
	private val debounce: suspend () -> Unit = { delay(DEFAULT_DEBOUNCE) },
	private val gson: Gson = JsonFile.pretty
) {
	private val version: Int get() = migrations.size

	private val lock = Any()
	private val writeLock = Any()
	private var pending: JsonObject? = null
	private var writer: Job? = null

	@Volatile
	private var base: JsonObject = JsonObject()

	@Volatile
	private var writes = 0

	@Volatile
	private var stampedVersion = version

	internal val writeCount: Int get() = writes

	fun load(): JsonObject {
		val doc = read() ?: JsonObject()
		val from = doc.int("version")
		for (v in from until version) migrations.getOrNull(v)?.invoke(doc)
		stampedVersion = maxOf(from, version)
		doc.addProperty("version", stampedVersion)
		base = doc.deepCopy()
		return doc
	}

	fun save(owned: JsonObject): Job = synchronized(lock) {
		pending = owned
		writer?.takeIf { it.isActive }?.let { return it }
		scope.launch { drain() }.also { writer = it }
	}

	fun flush(): Boolean = synchronized(writeLock) {
		val snapshot = synchronized(lock) { pending.also { pending = null } }
		snapshot?.let(::write)
		snapshot != null
	}

	private suspend fun drain() {
		while (true) {
			debounce()
			if (flush()) continue
			synchronized(lock) { if (pending == null) { writer = null; return } }
		}
	}

	private fun write(snapshot: JsonObject) {
		val merged = deepMerge(base, snapshot, authoritative)
		merged.addProperty("version", stampedVersion)
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
		val parsed = try {
			val text = Files.readString(path)
			if (text.isBlank()) return null
			JsonParser.parseString(text) as? JsonObject
		} catch (e: Exception) {
			log.error("Could not read config {}", path, e)
			null
		}
		if (parsed == null) setAsideUnusable()
		return parsed
	}

	private fun setAsideUnusable() {
		val unusable = path.resolveSibling(path.fileName.toString() + UNUSABLE)
		try {
			Files.move(path, unusable, StandardCopyOption.REPLACE_EXISTING)
			log.warn("Moved unusable config {} to {}", path, unusable)
		} catch (e: Exception) {
			log.error("Could not set aside unusable config {}", path, e)
		}
	}

	private fun deepMerge(base: JsonObject, overlay: JsonObject, replaced: Set<String> = emptySet()): JsonObject {
		val result = base.deepCopy()
		for ((key, value) in overlay.entrySet()) {
			val existing = result.get(key)
			if (key !in replaced && existing is JsonObject && value is JsonObject) result.add(key, deepMerge(existing, value, replaced))
			else result.add(key, value)
		}
		return result
	}

	private companion object {
		private const val UNUSABLE = ".unusable"
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		private val DEFAULT_DEBOUNCE = 1.seconds
	}
}
