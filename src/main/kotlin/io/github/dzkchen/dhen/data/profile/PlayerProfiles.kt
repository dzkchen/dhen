package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoState
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebSource
import io.github.dzkchen.dhen.util.array
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
	private const val MAX_HELD_PROFILES = 16
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
	private val repoReady = { ItemRepo.state == RepoState.READY }
	private val slices = Cache<ProfileSlice>(Endpoint.PROFILES.ttl)
	private val models = Cache<SkyBlockProfile>(Endpoint.PROFILES.ttl, MAX_HELD_PROFILES, repoReady)
	private val holdings = Cache<ProfileHoldings>(Endpoint.PROFILES.ttl, MAX_HELD_PROFILES, repoReady)
	private val gardens = Cache<GardenProfile>(Endpoint.GARDEN.ttl, MAX_HELD_PROFILES, repoReady)

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
		val repo = ItemRepo.require()
		val released = AtomicBoolean()
		return Handle {
			if (released.compareAndSet(false, true)) {
				repo.unsubscribe()
				if (installation.get() == installed) release()
			}
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

	suspend fun selectedProfile(uuid: String): JsonObject? = profiles(uuid)?.let(ProfileSlices::selectedProfile)

	suspend fun slice(uuid: String): ProfileSlice? =
		cached(slices, uuid) { profiles(uuid)?.let { reply -> ProfileSlices.of(uuid, reply) } }

	suspend fun holdings(uuid: String): ProfileHoldings? = cached(holdings, uuid) { decode(uuid) }

	suspend fun profile(uuid: String): SkyBlockProfile? =
		cached(models, uuid) { profiles(uuid)?.let { reply -> SkyBlockProfiles.of(uuid, reply) } }

	suspend fun gardenProfile(profileId: String): GardenProfile? =
		cached(gardens, profileId) { garden(profileId)?.let(GardenProfiles::of) }

	suspend fun accountSecrets(uuid: String): Long? = player(uuid)?.let(ProfileSlices::secrets)

	suspend fun onlineStatus(uuid: String): ProfileStatus = ProfileSlices.status(status(uuid))

	fun lookup(playerName: String, report: (String) -> Unit) {
		val host = host ?: return report("Dhen's player-profile service is not running, so it cannot look anybody up.")
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
				if (reply == null) {
					say(host, report, "The profile proxy did not answer for $playerName.")
					return@launch
				}
				val opening = "$playerName has ${profileList(reply).size()} SkyBlock profiles; the selected one is '${selectedName(reply)}'."
				val slice = slice(uuid)
				val secrets = if (slice?.dungeons == null) null else accountSecrets(uuid)
				sayAll(host, report, listOf(opening) + sliceLines(slice, secrets, onlineStatus(uuid)))
			} finally {
				requirement.unsubscribe()
			}
		}
	}

	private fun sliceLines(slice: ProfileSlice?, accountSecrets: Long?, status: ProfileStatus): List<String> = buildList {
		if (slice == null) {
			add("Dhen could not read that profile, so it has no stats to show.")
		} else {
			slice.dungeons?.also { add(dungeonLine(it)); add(secretsLine(it, accountSecrets)) }
				?: add("That profile has no dungeon data.")
			add(powerLine(slice))
			add("The inventory API is ${if (slice.inventoryApi) "on" else "off"} for that profile.")
		}
		add(onlineLine(status))
	}

	private fun dungeonLine(dungeons: DungeonSlice): String =
		"Catacombs ${dungeons.catacombsLevel}, class average ${rounded(dungeons.classAverage)}, " +
			"playing ${dungeons.selectedClass ?: "no class"}."

	private fun secretsLine(dungeons: DungeonSlice, accountSecrets: Long?): String =
		"${dungeons.secrets} secrets over ${dungeons.runs} runs on this profile (${rounded(dungeons.secretsPerRun)} a run), " +
			"${accountSecrets ?: "an unknown number"} on the whole account, and ${dungeons.bloodMobKills} blood-mob kills."

	private fun powerLine(slice: ProfileSlice): String = when {
		slice.magicalPower == null ->
			"Dhen cannot read the talisman bag, so it is assuming a magical power of ${slice.assumedMagicalPower}."
		slice.magicalPower == 0 && slice.assumedMagicalPower > 0 ->
			"Magical power ${slice.assumedMagicalPower}, assumed from tuning points because the talisman bag is empty."
		else -> "Magical power ${slice.magicalPower}."
	}

	private fun onlineLine(status: ProfileStatus): String = when (status.reading) {
		OnlineReading.ONLINE -> "Right now they are online${status.gameType?.let { " in $it" } ?: ""}${placeOf(status)}."
		OnlineReading.OFFLINE -> "Right now they are offline."
		OnlineReading.UNKNOWN -> "Dhen could not tell whether they are online."
	}

	private fun placeOf(status: ProfileStatus): String =
		listOfNotNull(status.mode, status.map).takeIf(List<String>::isNotEmpty)?.joinToString(", ", " (", ")") ?: ""

	private fun rounded(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

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
		cached(replies.getValue(endpoint), key) { owner, base ->
			envelope(fetch(owner, "$base${endpoint.path}?${endpoint.parameter}=$key")).also { proxy.note(it != null) }
		}

	private suspend fun <V> cached(cache: Cache<V>, key: String, produce: suspend (Host, String) -> V?): V? {
		val owner = host ?: return null
		if (!idShape.matches(key) || requirements.get() == 0) return null
		val base = syncBase() ?: return null
		cache.read(key, owner.clock.nanoTime())?.let { return it.value }
		val readyBefore = cache.stable()
		val value = withContext(Dispatchers.IO) { produce(owner, base) }
		return store(owner, cache, key, value, unchanged = lastBase == base && readyBefore && cache.stable())
	}

	private suspend fun <V> cached(cache: Cache<V>, key: String, produce: suspend () -> V?): V? =
		cached(cache, key) { _, _ -> produce() }

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

	private suspend fun sayAll(owner: Host, report: (String) -> Unit, lines: List<String>) =
		withContext(owner.clientDispatcher) { for (line in lines) report(line) }

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

	private fun readId(body: String?): String? = asObject(body)?.text("id")?.replace("-", "")?.ifEmpty { null }

	private fun asObject(body: String?): JsonObject? {
		if (body == null) return null
		return runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject
	}

	private fun profileList(reply: JsonObject): JsonArray = reply.array("profiles") ?: JsonArray()

	private fun selectedName(reply: JsonObject): String =
		ProfileSlices.selectedProfile(reply)?.text("cute_name") ?: NO_PROFILE


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

	private class Cache<V>(ttl: Duration, private val maxEntries: Int = MAX_ENTRIES, val stable: () -> Boolean = { true }) {
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
			if (entries.size >= maxEntries) {
				entries.entries.removeIf { expired(it.value, now) }
				while (entries.size >= maxEntries) {
					val oldest = entries.entries.minByOrNull { it.value.stamp } ?: break
					entries.remove(oldest.key, oldest.value)
				}
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
