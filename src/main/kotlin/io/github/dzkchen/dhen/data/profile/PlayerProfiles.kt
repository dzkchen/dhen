package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.Requirement
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebResponse
import io.github.dzkchen.dhen.util.WebSource
import io.github.dzkchen.dhen.util.text
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
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
	private const val MAX_HELD_PROFILES = 16
	private const val PROXY_AGNOSTIC = 0
	private const val NOT_FOUND = 404
	private const val TOO_MANY_REQUESTS = 429

	private val NEGATIVE_TTL = 1.minutes
	private val MOJANG_RETRY_AFTER = 5.minutes
	private val UUID_TTL = 1.hours
	private val ALWAYS = { true }
	private val MOJANG_ENDPOINTS = arrayOf(MOJANG_PRIMARY, MOJANG_FALLBACK)

	private val nameShape = Regex("[A-Za-z0-9_]{1,16}")
	private val idShape = Regex("[0-9a-fA-F-]{32,36}")

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val requirement = Requirement()
	private val mojang = Tally(MOJANG)
	private val proxy = Tally(PROXY)
	private val peak = AtomicInteger()
	private val limiter = Semaphore(MAX_IN_FLIGHT)
	private val uuids = Cache<String>(UUID_TTL)
	private val replies = Endpoint.entries.associateWith { Cache<JsonObject>(it.ttl, it.cap) }
	private val repoReady = { ItemRepo.ready }
	private val slices = Cache<ProfileSlice>(Endpoint.PROFILES.ttl)
	private val models = Cache<SkyBlockProfile>(Endpoint.PROFILES.ttl, MAX_HELD_PROFILES)
	private val holdings = Cache<ProfileHoldings>(Endpoint.PROFILES.ttl, MAX_HELD_PROFILES)
	private val gardens = Cache<GardenProfile>(Endpoint.GARDEN.ttl, MAX_HELD_PROFILES)

	private val currentBase = AtomicReference(Base(null, 0))

	@Volatile
	private var host: Host? = null

	val available: Boolean get() = configuredBase() != null

	val required: Int get() = requirement.count

	internal val inFlight: Int get() = MAX_IN_FLIGHT - limiter.availablePermits

	internal val maxInFlight: Int get() = MAX_IN_FLIGHT

	internal val peakInFlight: Int get() = peak.get()

	internal val failures: Int get() = mojang.failures + proxy.failures

	internal val proxyHost: String? get() = configuredBase()?.let { runCatching { URI(it).host }.getOrNull() }

	fun require(): Handle {
		val owner = host ?: return Handle {}
		val repo = AtomicReference<Handle?>()
		val held = requirement.require(alive = { host === owner }, taken = { repo.set(ItemRepo.require()) })
		return Handle {
			held.unsubscribe()
			repo.get()?.unsubscribe()
		}
	}

	suspend fun uuidOf(playerName: String): String? {
		val host = host ?: return null
		if (!nameShape.matches(playerName) || requirement.count == 0) return null
		syncBase()
		val key = playerName.lowercase(Locale.ROOT)
		uuids.read(key, host.clock.nanoTime(), PROXY_AGNOSTIC)?.let { return it.value }
		var found: String? = null
		for (index in MOJANG_ENDPOINTS.indices) {
			val now = host.clock.nanoTime()
			if (now < host.retryNotBefore.get(index)) continue
			val response = fetchResponse(host, MOJANG_ENDPOINTS[index] + playerName)
			if (response.statusCode == TOO_MANY_REQUESTS) {
				coolDown(host, index)
				continue
			}
			if (response.statusCode == NOT_FOUND) break
			found = readId(response.body)
			if (found != null) break
		}
		mojang.note(found != null)
		return store(host, uuids, key, found, PROXY_AGNOSTIC, keep = true)
	}

	private fun coolDown(owner: Host, endpoint: Int) {
		val retryAt = owner.clock.nanoTime() + MOJANG_RETRY_AFTER.inWholeNanoseconds
		while (true) {
			val previous = owner.retryNotBefore.get(endpoint)
			if (previous >= retryAt || owner.retryNotBefore.compareAndSet(endpoint, previous, retryAt)) return
		}
	}

	suspend fun profiles(uuid: String): JsonObject? = ask(Endpoint.PROFILES, uuid)

	suspend fun player(uuid: String): JsonObject? = ask(Endpoint.PLAYER, uuid)

	suspend fun museum(profileId: String): JsonObject? = ask(Endpoint.MUSEUM, profileId)

	suspend fun garden(profileId: String): JsonObject? = ask(Endpoint.GARDEN, profileId)

	suspend fun status(uuid: String): JsonObject? = ask(Endpoint.STATUS, uuid)

	suspend fun selectedProfile(uuid: String): JsonObject? = profiles(uuid)?.let(ProfileSlices::selectedProfile)

	suspend fun slice(uuid: String): ProfileSlice? = cached(slices, uuid) { owner, _, generation ->
		models.read(uuid, owner.clock.nanoTime(), generation)?.value?.slice
			?: profiles(uuid)?.let { reply -> ProfileSlices.of(uuid, reply) }
	}

	suspend fun holdings(uuid: String): ProfileHoldings? = cached(holdings, uuid, repoReady) { decode(uuid) }

	suspend fun profile(uuid: String): SkyBlockProfile? =
		cached(models, uuid, repoReady) { profiles(uuid)?.let { reply -> SkyBlockProfiles.of(uuid, reply) } }

	suspend fun gardenProfile(profileId: String): GardenProfile? =
		cached(gardens, profileId, repoReady) { garden(profileId)?.let(GardenProfiles::of) }

	suspend fun accountSecrets(uuid: String): Long? = player(uuid)?.let(ProfileSlices::secrets)

	suspend fun onlineStatus(uuid: String): ProfileStatus = ProfileSlices.status(status(uuid))

	suspend fun profileNames(uuid: String): ProfileNames? = profiles(uuid)?.let(ProfileSlices::names)

	internal fun reporting(report: (String) -> Unit, prose: suspend (suspend (List<String>) -> Unit) -> Unit): Boolean {
		val owner = host ?: return false
		owner.scope.launch {
			val held = require()
			try {
				prose { lines -> withContext(owner.clientDispatcher) { for (line in lines) report(line) } }
			} finally {
				held.unsubscribe()
			}
		}
		return true
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
		requirement.reset()
		mojang.reset()
		proxy.reset()
		peak.set(0)
		uuids.clear()
		invalidate(null)
	}

	internal fun clearCaches() {
		uuids.clear()
		invalidate(configuredBase())
	}

	private fun clearProxyCaches() {
		for (cache in replies.values) cache.clear()
		slices.clear()
		models.clear()
		holdings.clear()
		gardens.clear()
	}

	internal fun cacheSummary(): String = Endpoint.entries.joinToString(
		prefix = "uuid=${uuids.size}, ",
		postfix = ", slices=${slices.size}, models=${models.size}, holdings=${holdings.size}, gardens=${gardens.size}"
	) { "${it.name.lowercase(Locale.ROOT)}=${replies.getValue(it).size}" }

	private suspend fun decode(uuid: String): ProfileHoldings? {
		val profile = selectedProfile(uuid) ?: return null
		val member = ProfileSlices.member(profile, uuid) ?: return null
		return ProfileHoldings.of(profile, member)
	}

	private suspend fun ask(endpoint: Endpoint, key: String): JsonObject? =
		cached(replies.getValue(endpoint), key) { owner, url, _ ->
			envelope(fetchResponse(owner, "$url${endpoint.path}?${endpoint.parameter}=$key").body)
				.also { proxy.note(it != null) }
		}

	private suspend fun <V> cached(
		cache: Cache<V>,
		key: String,
		ready: () -> Boolean = ALWAYS,
		produce: suspend (Host, String, Int) -> V?
	): V? {
		val owner = host ?: return null
		if (!idShape.matches(key) || requirement.count == 0) return null
		val base = syncBase()
		val url = base.url ?: return null
		cache.read(key, owner.clock.nanoTime(), base.generation)?.let { return it.value }
		if (!ready()) return null
		val value = withContext(Dispatchers.IO) { produce(owner, url, base.generation) }
		val current = ready() && currentBase.get().generation == base.generation
		return store(owner, cache, key, value, base.generation, keep = current)
	}

	private suspend fun <V> cached(
		cache: Cache<V>,
		key: String,
		ready: () -> Boolean = ALWAYS,
		produce: suspend () -> V?
	): V? = cached(cache, key, ready) { _, _, _ -> produce() }

	private suspend fun fetchResponse(owner: Host, url: String): WebResponse = withContext(Dispatchers.IO) {
		limiter.withPermit {
			peak.updateAndGet { seen -> maxOf(seen, inFlight) }
			owner.web.response(url)
		}
	}

	private fun <V> store(owner: Host, cache: Cache<V>, key: String, value: V?, generation: Int, keep: Boolean): V? {
		if (host === owner && keep) cache.write(key, value, owner.clock.nanoTime(), generation)
		return value
	}

	private fun syncBase(): Base {
		val url = configuredBase()
		val previous = currentBase.get()
		return if (previous.url == url) previous else invalidate(url)
	}

	private fun invalidate(url: String?): Base {
		while (true) {
			val previous = currentBase.get()
			val next = Base(url, previous.generation + 1)
			if (currentBase.compareAndSet(previous, next)) {
				clearProxyCaches()
				return next
			}
		}
	}

	private fun configuredBase(): String? = host?.baseUrl?.invoke()?.trim()?.trimEnd('/')?.ifEmpty { null }

	private fun envelope(body: String?): JsonObject? {
		val reply = asObject(body) ?: return null
		val success = reply.get("success")?.takeIf(JsonElement::isJsonPrimitive) ?: return reply
		return if (success.asBoolean) reply else null
	}

	private fun readId(body: String?): String? = asObject(body)?.text("id")?.replace("-", "")?.ifEmpty { null }

	private fun asObject(body: String?): JsonObject? {
		if (body == null) return null
		return runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject
	}

	private fun userAgent(): String {
		val version = runCatching {
			FabricLoader.getInstance().getModContainer(Dhen.MOD_ID).orElse(null)?.metadata?.version?.friendlyString
		}.getOrNull()
		return "${Dhen.MOD_ID}/${version ?: "dev"}"
	}

	private enum class Endpoint(
		val path: String,
		val parameter: String,
		val ttl: Duration,
		val cap: Int = MAX_HELD_PROFILES
	) {
		PROFILES("/v2/skyblock/profiles", "uuid", 5.minutes),
		PLAYER("/v2/player", "uuid", 5.minutes),
		MUSEUM("/v2/skyblock/museum", "profile", 5.minutes),
		GARDEN("/v2/skyblock/garden", "profile", 5.minutes),
		STATUS("/v2/status", "uuid", 1.minutes, MAX_ENTRIES)
	}

	private class Base(val url: String?, val generation: Int)

	private class Cache<V>(ttl: Duration, private val maxEntries: Int = MAX_ENTRIES) {
		private val positive = ttl.inWholeNanoseconds
		private val negative = NEGATIVE_TTL.inWholeNanoseconds
		private val entries = ConcurrentHashMap<String, Entry<V>>()

		val size: Int get() = entries.size

		fun read(key: String, now: Long, generation: Int): Entry<V>? {
			val entry = entries[key] ?: return null
			if (entry.generation == generation && !expired(entry, now)) return entry
			if (entry.generation <= generation) entries.remove(key, entry)
			return null
		}

		fun write(key: String, value: V?, now: Long, generation: Int) {
			if (entries.size >= maxEntries) {
				entries.entries.removeIf { expired(it.value, now) }
				while (entries.size >= maxEntries) {
					val oldest = entries.entries.minByOrNull { it.value.stamp } ?: break
					entries.remove(oldest.key, oldest.value)
				}
			}
			entries[key] = Entry(value, now, generation)
		}

		fun clear() = entries.clear()

		private fun expired(entry: Entry<V>, now: Long): Boolean =
			now - entry.stamp >= if (entry.value == null) negative else positive

		class Entry<V>(val value: V?, val stamp: Long, val generation: Int)
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
		val baseUrl: () -> String,
		val retryNotBefore: AtomicLongArray = AtomicLongArray(MOJANG_ENDPOINTS.size)
	)
}
