package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object PlayerProfiles {
	private const val PROXY = "the profile proxy"
	private const val MOJANG = "Mojang"
	private const val ANY_SOURCE = "a player data service"
	private const val MOJANG_PRIMARY = "https://api.minecraftservices.com/minecraft/profile/lookup/name/"
	private const val MOJANG_FALLBACK = "https://api.mojang.com/users/profiles/minecraft/"
	private const val MAX_IN_FLIGHT = 5
	private const val MAX_ENTRIES = 128
	private const val NO_PROFILE = "none"

	private val NEGATIVE_TTL = 1.minutes
	private val UUID_TTL = 1.hours

	private val nameShape = Regex("[A-Za-z0-9_]{1,16}")
	private val idShape = Regex("[0-9a-fA-F-]{32,36}")

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val requirements = AtomicInteger()
	private val installation = AtomicInteger()
	private val mojang = Tally(MOJANG)
	private val proxy = Tally(PROXY)
	private val peak = AtomicInteger()
	private val limiter = Semaphore(MAX_IN_FLIGHT)
	private val uuids = Cache<String>(UUID_TTL)
	private val replies = Endpoint.entries.associateWith { Cache<JsonObject>(it.ttl) }

	@Volatile
	private var host: Host? = null

	@Volatile
	private var lastBase: String? = null

	val available: Boolean get() = configuredBase() != null

	val required: Int get() = requirements.get()

	internal val inFlight: Int get() = MAX_IN_FLIGHT - limiter.availablePermits

	internal val maxInFlight: Int get() = MAX_IN_FLIGHT

	internal val peakInFlight: Int get() = peak.get()

	internal val failures: Int get() = mojang.failures + proxy.failures

	internal val proxyHost: String? get() = configuredBase()?.let { runCatching { URI(it).host }.getOrNull() }

	fun require(): Handle {
		host ?: return Handle {}
		val installed = installation.get()
		requirements.incrementAndGet()
		if (installation.get() != installed) {
			release()
			return Handle {}
		}
		val released = AtomicBoolean()
		return Handle {
			if (released.compareAndSet(false, true) && installation.get() == installed) release()
		}
	}


	suspend fun uuidOf(playerName: String): String? {
		val host = host ?: return null
		if (!nameShape.matches(playerName) || requirements.get() == 0) return null
		syncBase()
		val key = playerName.lowercase(Locale.ROOT)
		uuids.read(key, host.clock.nanoTime())?.let { return it.value }
		val found = readId(fetch(host, MOJANG_PRIMARY + playerName))
			?: readId(fetch(host, MOJANG_FALLBACK + playerName))
		mojang.note(found != null)
		return store(host, uuids, key, found, unchanged = true)
	}

	suspend fun profiles(uuid: String): JsonObject? = ask(Endpoint.PROFILES, uuid)

	suspend fun player(uuid: String): JsonObject? = ask(Endpoint.PLAYER, uuid)

	suspend fun museum(profileId: String): JsonObject? = ask(Endpoint.MUSEUM, profileId)

	suspend fun garden(profileId: String): JsonObject? = ask(Endpoint.GARDEN, profileId)

	suspend fun status(uuid: String): JsonObject? = ask(Endpoint.STATUS, uuid)

	suspend fun selectedProfile(uuid: String): JsonObject? = profiles(uuid)?.let(::selected)

	fun lookup(playerName: String, report: (String) -> Unit) {
		val host = host ?: return
		host.scope.launch {
			val requirement = require()
			try {
				val uuid = uuidOf(playerName)
				if (uuid == null) {
					say(host, report, "Dhen could not look '$playerName' up — either no such account exists, or Mojang did not answer.")
					return@launch
				}
				say(host, report, "$playerName is $uuid.")
				if (configuredBase() == null) {
					say(host, report, "No profile proxy is set, so Dhen cannot ask for SkyBlock profiles.")
					return@launch
				}
				val reply = profiles(uuid)
				say(
					host,
					report,
					if (reply == null) "The profile proxy did not answer for $playerName."
					else "$playerName has ${profileList(reply).size()} SkyBlock profiles; the selected one is '${selectedName(reply)}'."
				)
			} finally {
				requirement.unsubscribe()
			}
		}
	}

	internal fun install(
		scope: CoroutineScope,
		clientDispatcher: CoroutineDispatcher,
		web: WebSource = WebClient(mapOf("User-Agent" to userAgent()), logAs = ANY_SOURCE),
		clock: NanoClock = NanoClock.SYSTEM,
		baseUrl: () -> String = { "" }
	) {
		uninstall()
		host = Host(scope, clientDispatcher, web, clock, baseUrl)
	}

	internal fun uninstall() {
		host = null
		installation.incrementAndGet()
		requirements.set(0)
		mojang.reset()
		proxy.reset()
		peak.set(0)
		lastBase = null
		clearCaches()
	}

	internal fun clearCaches() {
		uuids.clear()
		for (cache in replies.values) cache.clear()
	}

	internal fun cacheSummary(): String =
		Endpoint.entries.joinToString(prefix = "uuid=${uuids.size}, ") {
			"${it.name.lowercase(Locale.ROOT)}=${replies.getValue(it).size}"
		}

	private suspend fun ask(endpoint: Endpoint, key: String): JsonObject? {
		val host = host ?: return null
		if (!idShape.matches(key) || requirements.get() == 0) return null
		val base = syncBase() ?: return null
		val cache = replies.getValue(endpoint)
		cache.read(key, host.clock.nanoTime())?.let { return it.value }
		val reply = envelope(fetch(host, "$base${endpoint.path}?${endpoint.parameter}=$key"))
		proxy.note(reply != null)
		return store(host, cache, key, reply, unchanged = lastBase == base)
	}

	private suspend fun fetch(owner: Host, url: String): String? = withContext(Dispatchers.IO) {
		limiter.withPermit {
			peak.updateAndGet { seen -> maxOf(seen, inFlight) }
			owner.web.text(url)
		}
	}

	private fun <V> store(owner: Host, cache: Cache<V>, key: String, value: V?, unchanged: Boolean): V? {
		if (host === owner && unchanged) cache.write(key, value, owner.clock.nanoTime())
		return value
	}

	private suspend fun say(owner: Host, report: (String) -> Unit, line: String) =
		withContext(owner.clientDispatcher) { report(line) }

	private fun release() = requirements.updateAndGet { held -> maxOf(0, held - 1) }

	private fun syncBase(): String? {
		val base = configuredBase()
		if (base != lastBase) {
			clearCaches()
			lastBase = base
		}
		return base
	}

	private fun configuredBase(): String? = host?.baseUrl?.invoke()?.trim()?.trimEnd('/')?.ifEmpty { null }

	private fun envelope(body: String?): JsonObject? {
		val reply = asObject(body) ?: return null
		val success = reply.get("success")?.takeIf(JsonElement::isJsonPrimitive) ?: return reply
		return if (success.asBoolean) reply else null
	}

	private fun readId(body: String?): String? =
		asObject(body)?.get("id")?.takeIf(JsonElement::isJsonPrimitive)?.asString?.replace("-", "")?.ifEmpty { null }

	private fun asObject(body: String?): JsonObject? {
		if (body == null) return null
		return runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject
	}

	private fun profileList(reply: JsonObject): JsonArray =
		reply.get("profiles")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

	private fun selected(reply: JsonObject): JsonObject? = profileList(reply)
		.firstOrNull { it.isJsonObject && it.asJsonObject.get("selected")?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean == true }
		?.asJsonObject

	private fun selectedName(reply: JsonObject): String =
		selected(reply)?.get("cute_name")?.takeIf(JsonElement::isJsonPrimitive)?.asString ?: NO_PROFILE


	private fun userAgent(): String {
		val version = runCatching {
			FabricLoader.getInstance().getModContainer(Dhen.MOD_ID).orElse(null)?.metadata?.version?.friendlyString
		}.getOrNull()
		return "${Dhen.MOD_ID}/${version ?: "dev"}"
	}

	private enum class Endpoint(val path: String, val parameter: String, val ttl: Duration) {
		PROFILES("/v2/skyblock/profiles", "uuid", 5.minutes),
		PLAYER("/v2/player", "uuid", 5.minutes),
		MUSEUM("/v2/skyblock/museum", "profile", 5.minutes),
		GARDEN("/v2/skyblock/garden", "profile", 5.minutes),
		STATUS("/v2/status", "uuid", 1.minutes)
	}

	private class Cache<V>(ttl: Duration) {
		private val positive = ttl.inWholeNanoseconds
		private val negative = NEGATIVE_TTL.inWholeNanoseconds
		private val entries = ConcurrentHashMap<String, Entry<V>>()

		val size: Int get() = entries.size

		fun read(key: String, now: Long): Entry<V>? {
			val entry = entries[key] ?: return null
			if (!expired(entry, now)) return entry
			entries.remove(key, entry)
			return null
		}

		fun write(key: String, value: V?, now: Long) {
			if (entries.size >= MAX_ENTRIES) {
				entries.entries.removeIf { expired(it.value, now) }
				if (entries.size >= MAX_ENTRIES) entries.clear()
			}
			entries[key] = Entry(value, now)
		}

		fun clear() = entries.clear()

		private fun expired(entry: Entry<V>, now: Long): Boolean =
			now - entry.stamp >= if (entry.value == null) negative else positive

		class Entry<V>(val value: V?, val stamp: Long)
	}

	private class Tally(private val source: String) {
		private val count = AtomicInteger()

		val failures: Int get() = count.get()

		fun reset() = count.set(0)

		fun note(answered: Boolean) {
			if (!answered) {
				if (count.getAndIncrement() == 0) log.warn("Dhen could not read player data from {}", source)
				return
			}
			val previous = count.getAndSet(0)
			if (previous > 0) log.info("Dhen read player data from {} again after {} failures", source, previous)
		}
	}

	private class Host(
		val scope: CoroutineScope,
		val clientDispatcher: CoroutineDispatcher,
		val web: WebSource,
		val clock: NanoClock,
		val baseUrl: () -> String
	)
}
