package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.WebSource
import org.slf4j.LoggerFactory
import kotlin.time.Duration

internal class CachedFeed<T : Any>(
	val name: String,
	private val url: String,
	private val ttl: Duration,
	private val count: (T) -> Int,
	private val parse: (String) -> T?
) {
	@Volatile
	var value: T? = null
		private set

	@Volatile
	var updatedAt: Long = 0L
		private set

	@Volatile
	var failures: Int = 0
		private set

	val loaded: Boolean get() = value != null

	val size: Int get() = value?.let(count) ?: 0

	fun stale(now: Long): Boolean = value == null || now - updatedAt >= ttl.inWholeNanoseconds

	fun refresh(web: WebSource, now: Long, stillWanted: () -> Boolean): Boolean {
		val parsed = web.text(url)?.let(::read)?.takeIf { count(it) > 0 }
		if (!stillWanted()) return false
		if (parsed == null) {
			if (failures++ == 0) {
				if (loaded) log.warn("Dhen kept the last {} it read, its source did not answer usefully", name)
				else log.warn("Dhen has no {}, its source did not answer usefully", name)
			}
			return false
		}
		if (failures > 0) log.info("Dhen read the {} again after {} failed refreshes", name, failures)
		value = parsed
		updatedAt = now
		failures = 0
		return true
	}

	fun reset() {
		value = null
		updatedAt = 0L
		failures = 0
	}

	private fun read(body: String): T? = try {
		parse(body)
	} catch (throwable: Throwable) {
		log.warn("Dhen could not read the {}", name, throwable)
		null
	}

	private companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
