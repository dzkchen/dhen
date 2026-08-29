package io.github.dzkchen.dhen.sound

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.roundToInt

internal val ANY_PITCH = Float.NaN

internal const val RECENT_RAW_LIMIT = 256

internal const val PENDING_REPLACEMENT_LIMIT = 32

internal data class RecentSound(val identifier: Identifier, val pitch: Float)

internal data class SoundRuleKey(val identifier: Identifier, val matchPitch: Float)

object SoundManager {
	private val ruleLock = Any()
	private val recentLock = Any()
	private val replacementLock = Any()
	private val pendingLock = Any()
	private val recentIds = arrayOfNulls<Identifier>(RECENT_RAW_LIMIT)
	private val recentPitches = FloatArray(RECENT_RAW_LIMIT)
	private val pendingIds = arrayOfNulls<Identifier>(PENDING_REPLACEMENT_LIMIT)
	private val pendingVolumes = FloatArray(PENDING_REPLACEMENT_LIMIT)
	private val pendingPitches = FloatArray(PENDING_REPLACEMENT_LIMIT)
	private var pendingHead = 0L
	private var pendingTail = 0L

	@Volatile
	private var rules = RuleSnapshot.EMPTY
	@Volatile
	internal var recordedStarts = 0L
		private set
	@Volatile
	private var replacingDepth = 0
	@Volatile
	private var recentBase = 0L
	private var suppressRecentDepth = 0
	private var store: ConfigStore? = null
	private val replacementDispatch = ReplacementDispatch(::dispatchReplacement)
	private val enginePlay: (SoundInstance) -> Unit = { Minecraft.getInstance().soundManager.play(it) }
	private val clientSchedule: (Runnable) -> Unit = { Minecraft.getInstance().execute(it) }
	private val pendingReplacement = Runnable { playPendingReplacement(enginePlay) }

	internal val migrations: List<(JsonObject) -> Unit> = listOf(::liftMultipliersIntoRules, ::wrapRulesInArrays)
	internal val authoritative: Set<String> = setOf(RULES)

	internal fun install(store: ConfigStore) = synchronized(ruleLock) {
		this.store = store
		rules = decode(store.load())
	}

	internal fun uninstall() {
		synchronized(ruleLock) {
			store = null
			rules = RuleSnapshot.EMPTY
		}
		forgetPlayback()
	}

	internal fun forgetPlayback() {
		clearRecentSounds()
		clearPendingReplacements()
	}

	@JvmStatic
	fun volumeOf(identifier: Identifier): Float = volumeOf(identifier, ANY_PITCH)

	@JvmStatic
	fun volumeOf(identifier: Identifier, pitch: Float): Float {
		val snapshot = rules
		val index = snapshot.matchingIndex(identifier, pitch)
		return if (index < 0) DEFAULT_VOLUME else snapshot.volumes[index]
	}

	@JvmStatic
	fun onSoundPlay(sound: SoundInstance): Boolean {
		if (sound is SubstituteSound) return false
		val identifier = sound.identifier
		val pitch = (sound as? SoundPitchAccess)?.dhenSourcePitch() ?: sound.pitch
		recordPlayedSound(identifier, pitch)
		return applyRule(identifier, pitch)
	}

	internal fun applyRule(
		identifier: Identifier,
		pitch: Float = DEFAULT_PITCH,
		dispatch: ReplacementDispatch = replacementDispatch
	): Boolean {
		if (replacingDepth != 0) return false
		val snapshot = rules
		val index = snapshot.matchingIndex(identifier, pitch)
		if (index < 0) return false
		if (snapshot.volumes[index] == MUTED) return true
		val replacement = snapshot.replacements[index] ?: return false
		return dispatch.play(replacement, snapshot.replacementVolumes[index], snapshot.replacementPitches[index])
	}

	internal fun hasRule(identifier: Identifier, matchPitch: Float = ANY_PITCH): Boolean =
		!rules.isDefault(identifier, matchPitch)

	internal fun replacementOf(identifier: Identifier, matchPitch: Float = ANY_PITCH): Identifier? {
		val snapshot = rules
		val index = snapshot.exactIndex(identifier, matchPitch)
		return if (index < 0) null else snapshot.replacements[index]
	}

	internal fun replacementVolumeOf(identifier: Identifier, matchPitch: Float = ANY_PITCH): Float {
		val snapshot = rules
		val index = snapshot.exactIndex(identifier, matchPitch)
		return if (index < 0) MAX_REPLACEMENT_VOLUME else snapshot.replacementVolumes[index]
	}

	internal fun replacementPitchOf(identifier: Identifier, matchPitch: Float = ANY_PITCH): Float {
		val snapshot = rules
		val index = snapshot.exactIndex(identifier, matchPitch)
		return if (index < 0) DEFAULT_PITCH else snapshot.replacementPitches[index]
	}

	internal fun ruledSounds(): List<SoundRuleKey> {
		val snapshot = rules
		return buildList {
			for (index in 0 until snapshot.size) {
				val identifier = snapshot.identifiers[index] ?: continue
				if (!snapshot.isDefaultAt(index)) add(SoundRuleKey(identifier, snapshot.matchPitches[index]))
			}
		}
	}

	internal fun getVolumePercent(identifier: Identifier, matchPitch: Float = ANY_PITCH): Int {
		val snapshot = rules
		val index = snapshot.exactIndex(identifier, matchPitch)
		return ((if (index < 0) DEFAULT_VOLUME else snapshot.volumes[index]) * PERCENT_SCALE).roundToInt()
	}

	internal fun setVolumePercent(identifier: Identifier, percent: Int, matchPitch: Float = ANY_PITCH): Int = synchronized(ruleLock) {
		val normalized = normalizePercent(percent)
		if (store == null) return@synchronized getVolumePercent(identifier, matchPitch)
		publish(rules.withVolume(identifier, matchPitch, normalized / PERCENT_SCALE), identifier, matchPitch)
		normalized
	}

	internal fun setReplacement(
		identifier: Identifier,
		replacement: Identifier?,
		volume: Float,
		pitch: Float,
		matchPitch: Float = ANY_PITCH
	) =
		synchronized(ruleLock) {
			if (store == null) return@synchronized
			val registered = replacement?.takeIf(BuiltInRegistries.SOUND_EVENT::containsKey)
			publish(
				rules.withReplacement(
					identifier,
					matchPitch,
					registered,
					volume.coerceIn(MUTED, MAX_REPLACEMENT_VOLUME),
					pitch.coerceIn(MIN_PITCH, MAX_PITCH)
				),
				identifier,
				matchPitch
			)
		}

	internal fun moveRule(identifier: Identifier, fromPitch: Float, toPitch: Float) = synchronized(ruleLock) {
		val installed = store ?: return@synchronized
		val moved = rules.moving(identifier, fromPitch, toPitch) ?: return@synchronized
		rules = moved
		installed.save(encode(moved))
	}

	internal fun removeRule(identifier: Identifier, matchPitch: Float = ANY_PITCH) = synchronized(ruleLock) {
		val installed = store ?: return@synchronized
		rules = rules.without(identifier, matchPitch) ?: return@synchronized
		installed.save(encode(rules))
	}

	private fun publish(updated: RuleSnapshot, identifier: Identifier, matchPitch: Float) {
		rules = if (updated.isDefault(identifier, matchPitch)) updated.without(identifier, matchPitch) ?: updated else updated
		store?.save(encode(rules))
	}

	internal fun recordPlayedSound(identifier: Identifier, pitch: Float): Unit = synchronized(recentLock) {
		if (suppressRecentDepth != 0 || repeatsLatestRecent(identifier, pitch)) return@synchronized
		val slot = (recordedStarts % RECENT_RAW_LIMIT).toInt()
		recentIds[slot] = identifier
		recentPitches[slot] = pitch
		recordedStarts++
	}

	private fun repeatsLatestRecent(identifier: Identifier, pitch: Float): Boolean {
		if (recordedStarts == recentBase) return false
		val slot = ((recordedStarts - 1) % RECENT_RAW_LIMIT).toInt()
		return recentIds[slot] == identifier && pitchMatches(recentPitches[slot], pitch)
	}

	internal fun rawRecentSounds(): List<RecentSound> = synchronized(recentLock) {
		val retained = (recordedStarts - recentBase).coerceAtMost(RECENT_RAW_LIMIT.toLong()).toInt()
		List(retained) { offset ->
			val slot = ((recordedStarts - offset - 1) % RECENT_RAW_LIMIT).toInt()
			RecentSound(recentIds[slot]!!, recentPitches[slot])
		}
	}

	internal fun recentSnapshot(): List<RecentSound> = buildList {
		for (recent in rawRecentSounds()) {
			if (none { shown -> shown.identifier == recent.identifier && pitchMatches(shown.pitch, recent.pitch) }) add(recent)
		}
	}

	internal fun retainedStartsSince(mark: Long): Int {
		val starts = recordedStarts
		return (starts - maxOf(recentBase, mark)).coerceIn(0L, RECENT_RAW_LIMIT.toLong()).toInt()
	}

	internal fun clearRecentSounds(): Long = synchronized(recentLock) {
		recentBase = recordedStarts
		recordedStarts
	}

	internal fun playPreview(sound: SoundEvent, pitch: Float = PREVIEW_PITCH) {
		val client = Minecraft.getInstance()
		client.execute {
			playPreview(sound, pitch, client.soundManager::play)
		}
	}

	internal fun playPreview(
		sound: SoundEvent,
		pitch: Float = PREVIEW_PITCH,
		play: (SimpleSoundInstance) -> Unit
	) = synchronized(recentLock) {
		suppressRecentDepth++
		try {
			play(SimpleSoundInstance.forUI(sound, pitch, PREVIEW_VOLUME))
		} finally {
			suppressRecentDepth--
		}
	}

	internal fun playOriginal(identifier: Identifier, pitch: Float = PREVIEW_PITCH) {
		val client = Minecraft.getInstance()
		client.execute {
			playSubstitute(identifier, PREVIEW_VOLUME, pitch, client.soundManager::play)
		}
	}

	internal fun playSubstitute(
		replacement: Identifier,
		volume: Float,
		pitch: Float,
		play: (SoundInstance) -> Unit
	) = synchronized(replacementLock) {
		replacingDepth++
		try {
			play(SubstituteSound(replacement, volume, pitch))
		} finally {
			replacingDepth--
		}
	}

	internal fun dispatchReplacement(
		replacement: Identifier,
		volume: Float,
		pitch: Float,
		schedule: (Runnable) -> Unit = clientSchedule
	): Boolean {
		if (!offerReplacement(replacement, if (previewing()) volume * PREVIEW_VOLUME else volume, pitch)) return false
		schedule(pendingReplacement)
		return true
	}

	internal fun offerReplacement(replacement: Identifier, volume: Float, pitch: Float): Boolean =
		synchronized(pendingLock) {
			if (pendingTail - pendingHead >= PENDING_REPLACEMENT_LIMIT) return@synchronized false
			val slot = (pendingTail % PENDING_REPLACEMENT_LIMIT).toInt()
			pendingIds[slot] = replacement
			pendingVolumes[slot] = volume
			pendingPitches[slot] = pitch
			pendingTail++
			true
		}

	internal fun playPendingReplacement(play: (SoundInstance) -> Unit): Boolean {
		var replacement: Identifier? = null
		var volume = 0f
		var pitch = 0f
		synchronized(pendingLock) {
			if (pendingHead != pendingTail) {
				val slot = (pendingHead % PENDING_REPLACEMENT_LIMIT).toInt()
				replacement = pendingIds[slot]
				volume = pendingVolumes[slot]
				pitch = pendingPitches[slot]
				pendingIds[slot] = null
				pendingHead++
			}
		}
		val pending = replacement ?: return false
		playSubstitute(pending, volume, pitch, play)
		return true
	}

	private fun clearPendingReplacements() = synchronized(pendingLock) {
		while (pendingHead != pendingTail) pendingIds[(pendingHead++ % PENDING_REPLACEMENT_LIMIT).toInt()] = null
	}

	private fun previewing(): Boolean = synchronized(recentLock) { suppressRecentDepth != 0 }

	private fun liftMultipliersIntoRules(doc: JsonObject) {
		val multipliers = doc.remove(MULTIPLIERS) as? JsonObject ?: return
		val lifted = doc.obj(RULES) ?: JsonObject().also { doc.add(RULES, it) }
		for ((rawIdentifier, value) in multipliers.entrySet()) {
			val volume = value.numberOrNull()?.takeUnless { lifted.has(rawIdentifier) } ?: continue
			lifted.add(rawIdentifier, JsonObject().apply { addProperty(VOLUME, volume) })
		}
	}

	private fun wrapRulesInArrays(doc: JsonObject) {
		val encoded = doc.obj(RULES) ?: return
		for ((identifier, element) in encoded.entrySet().toList()) {
			if (element is JsonObject) encoded.add(identifier, JsonArray().apply { add(element) })
		}
	}

	private fun decode(doc: JsonObject): RuleSnapshot {
		val encoded = doc.obj(RULES) ?: return RuleSnapshot.EMPTY
		val decoded = ArrayList<DecodedRule>()
		for ((rawIdentifier, element) in encoded.entrySet()) {
			val identifier = Identifier.tryParse(rawIdentifier)
			if (identifier == null) {
				log.warn("Skipping bad sound rule for {}", rawIdentifier)
				continue
			}
			when (element) {
				is JsonObject -> decodeRule(rawIdentifier, identifier, element)?.let(decoded::add)
				is JsonArray -> for (rule in element) {
					val ruleObject = rule as? JsonObject
					if (ruleObject == null) log.warn("Skipping bad sound rule for {}", rawIdentifier)
					else decodeRule(rawIdentifier, identifier, ruleObject)?.let(decoded::add)
				}
				else -> log.warn("Skipping bad sound rule for {}", rawIdentifier)
			}
		}
		return RuleSnapshot(
			Array(decoded.size) { decoded[it].identifier },
			FloatArray(decoded.size) { decoded[it].matchPitch },
			FloatArray(decoded.size) { decoded[it].volume },
			Array(decoded.size) { decoded[it].replacement },
			FloatArray(decoded.size) { decoded[it].replacementVolume },
			FloatArray(decoded.size) { decoded[it].replacementPitch },
			decoded.size
		)
	}

	private fun encode(snapshot: RuleSnapshot): JsonObject {
		val encoded = JsonObject()
		for (index in 0 until snapshot.size) {
			val rule = JsonObject()
			val matchPitch = snapshot.matchPitches[index]
			if (!matchPitch.isNaN()) rule.addProperty(MATCH_PITCH, matchPitch)
			rule.addProperty(VOLUME, snapshot.volumes[index])
			snapshot.replacements[index]?.let { replacement ->
				rule.addProperty(REPLACEMENT, replacement.toString())
				rule.addProperty(REPLACEMENT_VOLUME, snapshot.replacementVolumes[index])
				rule.addProperty(REPLACEMENT_PITCH, snapshot.replacementPitches[index])
			}
			val identifier = snapshot.identifiers[index].toString()
			val grouped = encoded.get(identifier) as? JsonArray ?: JsonArray().also { encoded.add(identifier, it) }
			grouped.add(rule)
		}
		return JsonObject().apply { add(RULES, encoded) }
	}

	private fun decodeRule(rawIdentifier: String, identifier: Identifier, rule: JsonObject): DecodedRule? {
		val matchPitch = if (rule.has(MATCH_PITCH)) {
			ruleNumber(rawIdentifier, rule, MATCH_PITCH)?.toFloat()?.takeIf(Float::isFinite) ?: return null
		} else {
			ANY_PITCH
		}
		return DecodedRule(
			identifier,
			matchPitch,
			decodedVolume(ruleNumber(rawIdentifier, rule, VOLUME)),
			ruleReplacement(rawIdentifier, rule),
			boundedReplacementVolume(ruleNumber(rawIdentifier, rule, REPLACEMENT_VOLUME)),
			boundedReplacementPitch(ruleNumber(rawIdentifier, rule, REPLACEMENT_PITCH))
		)
	}

	private fun ruleNumber(rawIdentifier: String, rule: JsonObject, member: String): Double? =
		rule.number(member).also {
			if (it == null && rule.has(member)) log.warn("Ignoring bad {} in sound rule for {}", member, rawIdentifier)
		}

	private fun ruleReplacement(rawIdentifier: String, rule: JsonObject): Identifier? {
		if (!rule.has(REPLACEMENT)) return null
		val replacement = rule.text(REPLACEMENT)?.let(Identifier::tryParse)
		if (replacement == null || !BuiltInRegistries.SOUND_EVENT.containsKey(replacement)) {
			log.warn("Ignoring invalid or unregistered {} in sound rule for {}", REPLACEMENT, rawIdentifier)
			return null
		}
		return replacement
	}

	private fun decodedVolume(value: Double?): Float =
		normalizePercent(((value ?: DEFAULT_VOLUME.toDouble()) * PERCENT_SCALE).roundToInt()) / PERCENT_SCALE

	private fun boundedReplacementVolume(value: Double?): Float =
		value?.toFloat()?.coerceIn(MUTED, MAX_REPLACEMENT_VOLUME) ?: MAX_REPLACEMENT_VOLUME

	private fun boundedReplacementPitch(value: Double?): Float =
		value?.toFloat()?.coerceIn(MIN_PITCH, MAX_PITCH) ?: DEFAULT_PITCH

	private fun normalizePercent(percent: Int): Int {
		val bounded = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
		return ((bounded + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
	}

	internal fun interface ReplacementDispatch {
		fun play(replacement: Identifier, volume: Float, pitch: Float): Boolean
	}

	private class RuleSnapshot(
		val identifiers: Array<Identifier?>,
		val matchPitches: FloatArray,
		val volumes: FloatArray,
		val replacements: Array<Identifier?>,
		val replacementVolumes: FloatArray,
		val replacementPitches: FloatArray,
		val size: Int
	) {
		fun matchingIndex(identifier: Identifier, pitch: Float): Int {
			var unbound = -1
			for (index in 0 until size) {
				if (identifiers[index] != identifier) continue
				val matchPitch = matchPitches[index]
				if (matchPitch.isNaN()) unbound = index
				else if (pitchMatches(matchPitch, pitch)) return index
			}
			return unbound
		}

		fun exactIndex(identifier: Identifier, matchPitch: Float): Int {
			for (index in 0 until size) {
				if (identifiers[index] == identifier && sameBinding(matchPitches[index], matchPitch)) return index
			}
			return -1
		}

		fun isDefault(identifier: Identifier, matchPitch: Float): Boolean {
			val index = exactIndex(identifier, matchPitch)
			return index < 0 || isDefaultAt(index)
		}

		fun isDefaultAt(index: Int): Boolean = volumes[index] == DEFAULT_VOLUME && replacements[index] == null

		fun withVolume(identifier: Identifier, matchPitch: Float, volume: Float): RuleSnapshot {
			val index = exactIndex(identifier, matchPitch)
			if (index < 0) return appended(identifier, matchPitch, volume)
			val changed = volumes.copyOf()
			changed[index] = volume
			return RuleSnapshot(identifiers, matchPitches, changed, replacements, replacementVolumes, replacementPitches, size)
		}

		fun withReplacement(identifier: Identifier, matchPitch: Float, replacement: Identifier?, volume: Float, pitch: Float): RuleSnapshot {
			val index = exactIndex(identifier, matchPitch)
			if (index < 0) {
				return appended(identifier, matchPitch, DEFAULT_VOLUME)
					.withReplacement(identifier, matchPitch, replacement, volume, pitch)
			}
			val changedReplacements = replacements.copyOf()
			val changedVolumes = replacementVolumes.copyOf()
			val changedPitches = replacementPitches.copyOf()
			changedReplacements[index] = replacement
			changedVolumes[index] = volume
			changedPitches[index] = pitch
			return RuleSnapshot(identifiers, matchPitches, volumes, changedReplacements, changedVolumes, changedPitches, size)
		}

		fun moving(identifier: Identifier, fromPitch: Float, toPitch: Float): RuleSnapshot? {
			val from = exactIndex(identifier, fromPitch)
			if (from < 0 || sameBinding(fromPitch, toPitch)) return null
			val to = exactIndex(identifier, toPitch)
			if (to < 0) {
				val changed = matchPitches.copyOf()
				changed[from] = toPitch
				return RuleSnapshot(identifiers, changed, volumes, replacements, replacementVolumes, replacementPitches, size)
			}
			val changedVolumes = volumes.copyOf()
			val changedReplacements = replacements.copyOf()
			val changedReplacementVolumes = replacementVolumes.copyOf()
			val changedReplacementPitches = replacementPitches.copyOf()
			changedVolumes[to] = volumes[from]
			changedReplacements[to] = replacements[from]
			changedReplacementVolumes[to] = replacementVolumes[from]
			changedReplacementPitches[to] = replacementPitches[from]
			return RuleSnapshot(
				identifiers,
				matchPitches,
				changedVolumes,
				changedReplacements,
				changedReplacementVolumes,
				changedReplacementPitches,
				size
			).withoutAt(from)
		}

		fun without(identifier: Identifier, matchPitch: Float): RuleSnapshot? {
			val index = exactIndex(identifier, matchPitch)
			if (index < 0) return null
			return withoutAt(index)
		}

		private fun withoutAt(index: Int): RuleSnapshot {
			val last = size - 1
			val keptIdentifiers = identifiers.copyOf()
			val keptMatchPitches = matchPitches.copyOf()
			val keptVolumes = volumes.copyOf()
			val keptReplacements = replacements.copyOf()
			val keptReplacementVolumes = replacementVolumes.copyOf()
			val keptReplacementPitches = replacementPitches.copyOf()
			keptIdentifiers[index] = keptIdentifiers[last]
			keptMatchPitches[index] = keptMatchPitches[last]
			keptVolumes[index] = keptVolumes[last]
			keptReplacements[index] = keptReplacements[last]
			keptReplacementVolumes[index] = keptReplacementVolumes[last]
			keptReplacementPitches[index] = keptReplacementPitches[last]
			keptIdentifiers[last] = null
			keptReplacements[last] = null
			return RuleSnapshot(
				keptIdentifiers,
				keptMatchPitches,
				keptVolumes,
				keptReplacements,
				keptReplacementVolumes,
				keptReplacementPitches,
				last
			)
		}

		private fun appended(identifier: Identifier, matchPitch: Float, volume: Float): RuleSnapshot {
			val grown = size + 1
			val extendedIdentifiers = identifiers.copyOf(grown)
			val extendedMatchPitches = matchPitches.copyOf(grown)
			val extendedVolumes = volumes.copyOf(grown)
			val extendedReplacements = replacements.copyOf(grown)
			val extendedReplacementVolumes = replacementVolumes.copyOf(grown)
			val extendedReplacementPitches = replacementPitches.copyOf(grown)
			extendedIdentifiers[size] = identifier
			extendedMatchPitches[size] = matchPitch
			extendedVolumes[size] = volume
			extendedReplacements[size] = null
			extendedReplacementVolumes[size] = MAX_REPLACEMENT_VOLUME
			extendedReplacementPitches[size] = DEFAULT_PITCH
			return RuleSnapshot(
				extendedIdentifiers,
				extendedMatchPitches,
				extendedVolumes,
				extendedReplacements,
				extendedReplacementVolumes,
				extendedReplacementPitches,
				grown
			)
		}

		companion object {
			val EMPTY = RuleSnapshot(emptyArray(), FloatArray(0), FloatArray(0), emptyArray(), FloatArray(0), FloatArray(0), 0)
		}
	}

	private data class DecodedRule(
		val identifier: Identifier,
		val matchPitch: Float,
		val volume: Float,
		val replacement: Identifier?,
		val replacementVolume: Float,
		val replacementPitch: Float
	)

	private const val MULTIPLIERS = "multipliers"
	private const val RULES = "rules"
	private const val VOLUME = "volume"
	private const val REPLACEMENT = "replacement"
	private const val REPLACEMENT_VOLUME = "replacementVolume"
	private const val REPLACEMENT_PITCH = "replacementPitch"
	private const val MATCH_PITCH = "matchPitch"
	private const val MIN_PERCENT = 0
	private const val MAX_PERCENT = 200
	private const val STEP_PERCENT = 5
	private const val PERCENT_SCALE = 100f
	private const val MUTED = 0f
	private const val DEFAULT_VOLUME = 1f
	private const val MAX_REPLACEMENT_VOLUME = 1f
	private const val DEFAULT_PITCH = 1f
	private const val MIN_PITCH = 0.5f
	private const val MAX_PITCH = 2f
	private const val PREVIEW_VOLUME = 0.25f
	private const val PREVIEW_PITCH = 1f
	private const val PITCH_EPSILON = 0.0001f
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private fun pitchMatches(expected: Float, actual: Float): Boolean = abs(expected - actual) <= PITCH_EPSILON

	private fun sameBinding(first: Float, second: Float): Boolean =
		first.isNaN() && second.isNaN() || !first.isNaN() && !second.isNaN() && pitchMatches(first, second)
}
