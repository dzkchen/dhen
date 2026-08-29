package io.github.dzkchen.dhen.sound

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
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

object SoundManager {
	private val ruleLock = Any()
	private val recentLock = Any()
	private val replacementLock = Any()
	private val recentIds = arrayOfNulls<Identifier>(RECENT_LIMIT)

	@Volatile
	private var rules = RuleSnapshot.EMPTY
	@Volatile
	private var recentVersion = 0L
	@Volatile
	private var replacing = false
	private var recentCount = 0
	private var suppressRecentDepth = 0
	private var store: ConfigStore? = null
	private val replacementDispatch = ReplacementDispatch(::dispatchReplacement)

	internal val migrations: List<(JsonObject) -> Unit> = listOf(::liftMultipliersIntoRules)

	internal val recentSoundsVersion: Long
		get() = recentVersion

	internal fun install(store: ConfigStore) = synchronized(ruleLock) {
		this.store = store
		rules = decode(store.load())
	}

	internal fun uninstall() {
		synchronized(ruleLock) {
			store = null
			rules = RuleSnapshot.EMPTY
		}
		clearRecentSounds()
	}

	@JvmStatic
	fun volumeOf(identifier: Identifier): Float {
		val snapshot = rules
		val index = snapshot.indexOf(identifier)
		return if (index < 0) DEFAULT_VOLUME else snapshot.volumes[index]
	}

	@JvmStatic
	fun onSoundPlay(sound: SoundInstance): Boolean {
		val identifier = sound.identifier
		recordPlayedIdentifier(identifier)
		return applyRule(identifier)
	}

	internal fun applyRule(identifier: Identifier, dispatch: ReplacementDispatch = replacementDispatch): Boolean {
		if (replacing) return false
		val snapshot = rules
		val index = snapshot.indexOf(identifier)
		if (index < 0) return false
		if (snapshot.volumes[index] == MUTED) return true
		val replacement = snapshot.replacements[index] ?: return false
		dispatch.play(replacement, snapshot.replacementVolumes[index], snapshot.replacementPitches[index])
		return true
	}

	internal fun hasRule(identifier: Identifier): Boolean {
		val snapshot = rules
		val index = snapshot.indexOf(identifier)
		return index >= 0 && (snapshot.volumes[index] != DEFAULT_VOLUME || snapshot.replacements[index] != null)
	}

	internal fun getVolumePercent(identifier: Identifier): Int =
		(volumeOf(identifier) * PERCENT_SCALE).roundToInt()

	internal fun setVolumePercent(identifier: Identifier, percent: Int): Int = synchronized(ruleLock) {
		val installed = store ?: return@synchronized getVolumePercent(identifier)
		val normalized = normalizePercent(percent)
		rules = rules.withVolume(identifier, normalized / PERCENT_SCALE)
		installed.save(encode(rules))
		normalized
	}

	internal fun recordPlayedIdentifier(identifier: Identifier) = synchronized(recentLock) {
		if (suppressRecentDepth != 0) return@synchronized
		for (index in 0 until recentCount) {
			if (recentIds[index] == identifier) return@synchronized
		}
		if (recentCount == RECENT_LIMIT) {
			System.arraycopy(recentIds, 1, recentIds, 0, RECENT_LIMIT - 1)
			recentIds[RECENT_LIMIT - 1] = identifier
		} else {
			recentIds[recentCount] = identifier
			recentCount++
		}
		recentVersion++
	}

	internal fun recentSoundIds(): List<Identifier> = synchronized(recentLock) {
		List(recentCount) { offset -> recentIds[recentCount - offset - 1]!! }
	}

	internal fun clearRecentSounds() = synchronized(recentLock) {
		if (recentCount == 0) return@synchronized
		java.util.Arrays.fill(recentIds, 0, recentCount, null)
		recentCount = 0
		recentVersion++
	}

	internal fun playPreview(sound: SoundEvent) {
		val client = Minecraft.getInstance()
		client.execute {
			playPreview(sound, client.soundManager::play)
		}
	}

	internal fun playPreview(sound: SoundEvent, play: (SimpleSoundInstance) -> Unit) = synchronized(recentLock) {
		suppressRecentDepth++
		try {
			play(SimpleSoundInstance.forUI(sound, PREVIEW_PITCH, PREVIEW_VOLUME))
		} finally {
			suppressRecentDepth--
		}
	}

	internal fun playSubstitute(
		replacement: Identifier,
		volume: Float,
		pitch: Float,
		play: (SoundInstance) -> Unit
	) = synchronized(replacementLock) {
		replacing = true
		try {
			play(SubstituteSound(replacement, volume, pitch))
		} finally {
			replacing = false
		}
	}

	private fun dispatchReplacement(replacement: Identifier, volume: Float, pitch: Float) {
		val client = Minecraft.getInstance()
		client.execute {
			playSubstitute(replacement, volume, pitch, client.soundManager::play)
		}
	}

	private fun liftMultipliersIntoRules(doc: JsonObject) {
		val multipliers = doc.remove(MULTIPLIERS) as? JsonObject ?: return
		val lifted = doc.obj(RULES) ?: JsonObject().also { doc.add(RULES, it) }
		for ((rawIdentifier, value) in multipliers.entrySet()) {
			val volume = value.numberOrNull()?.takeUnless { lifted.has(rawIdentifier) } ?: continue
			lifted.add(rawIdentifier, JsonObject().apply { addProperty(VOLUME, volume) })
		}
	}

	private fun decode(doc: JsonObject): RuleSnapshot {
		val encoded = doc.obj(RULES) ?: return RuleSnapshot.EMPTY
		val identifiers = arrayOfNulls<Identifier>(encoded.size())
		val volumes = FloatArray(encoded.size())
		val replacements = arrayOfNulls<Identifier>(encoded.size())
		val replacementVolumes = FloatArray(encoded.size())
		val replacementPitches = FloatArray(encoded.size())
		var size = 0
		for ((rawIdentifier, element) in encoded.entrySet()) {
			val identifier = Identifier.tryParse(rawIdentifier)
			val rule = element as? JsonObject
			if (identifier == null || rule == null) {
				log.warn("Skipping bad sound rule for {}", rawIdentifier)
				continue
			}
			identifiers[size] = identifier
			volumes[size] = decodedVolume(ruleNumber(rawIdentifier, rule, VOLUME))
			replacements[size] = ruleReplacement(rawIdentifier, rule)
			replacementVolumes[size] = boundedReplacementVolume(ruleNumber(rawIdentifier, rule, REPLACEMENT_VOLUME))
			replacementPitches[size] = boundedReplacementPitch(ruleNumber(rawIdentifier, rule, REPLACEMENT_PITCH))
			size++
		}
		return RuleSnapshot(identifiers, volumes, replacements, replacementVolumes, replacementPitches, size)
	}

	private fun encode(snapshot: RuleSnapshot): JsonObject {
		val encoded = JsonObject()
		for (index in 0 until snapshot.size) {
			val rule = JsonObject()
			rule.addProperty(VOLUME, snapshot.volumes[index])
			snapshot.replacements[index]?.let { replacement ->
				rule.addProperty(REPLACEMENT, replacement.toString())
				rule.addProperty(REPLACEMENT_VOLUME, snapshot.replacementVolumes[index])
				rule.addProperty(REPLACEMENT_PITCH, snapshot.replacementPitches[index])
			}
			encoded.add(snapshot.identifiers[index].toString(), rule)
		}
		return JsonObject().apply { add(RULES, encoded) }
	}

	private fun ruleNumber(rawIdentifier: String, rule: JsonObject, member: String): Double? =
		rule.number(member).also {
			if (it == null && rule.has(member)) log.warn("Ignoring bad {} in sound rule for {}", member, rawIdentifier)
		}

	private fun ruleReplacement(rawIdentifier: String, rule: JsonObject): Identifier? {
		if (!rule.has(REPLACEMENT)) return null
		val replacement = rule.text(REPLACEMENT)?.let(Identifier::tryParse)
		if (replacement == null) log.warn("Ignoring bad {} in sound rule for {}", REPLACEMENT, rawIdentifier)
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
		fun play(replacement: Identifier, volume: Float, pitch: Float)
	}

	private class RuleSnapshot(
		val identifiers: Array<Identifier?>,
		val volumes: FloatArray,
		val replacements: Array<Identifier?>,
		val replacementVolumes: FloatArray,
		val replacementPitches: FloatArray,
		val size: Int
	) {
		fun indexOf(identifier: Identifier): Int {
			for (index in 0 until size) {
				if (identifiers[index] == identifier) return index
			}
			return -1
		}

		fun withVolume(identifier: Identifier, volume: Float): RuleSnapshot {
			val index = indexOf(identifier)
			if (index < 0) return appended(identifier, volume)
			val changed = volumes.copyOf()
			changed[index] = volume
			return RuleSnapshot(identifiers, changed, replacements, replacementVolumes, replacementPitches, size)
		}

		private fun appended(identifier: Identifier, volume: Float): RuleSnapshot {
			val grown = size + 1
			val extendedIdentifiers = identifiers.copyOf(grown)
			val extendedVolumes = volumes.copyOf(grown)
			val extendedReplacements = replacements.copyOf(grown)
			val extendedReplacementVolumes = replacementVolumes.copyOf(grown)
			val extendedReplacementPitches = replacementPitches.copyOf(grown)
			extendedIdentifiers[size] = identifier
			extendedVolumes[size] = volume
			extendedReplacements[size] = null
			extendedReplacementVolumes[size] = MAX_REPLACEMENT_VOLUME
			extendedReplacementPitches[size] = DEFAULT_PITCH
			return RuleSnapshot(
				extendedIdentifiers,
				extendedVolumes,
				extendedReplacements,
				extendedReplacementVolumes,
				extendedReplacementPitches,
				grown
			)
		}

		companion object {
			val EMPTY = RuleSnapshot(emptyArray(), FloatArray(0), emptyArray(), FloatArray(0), FloatArray(0), 0)
		}
	}

	private const val MULTIPLIERS = "multipliers"
	private const val RULES = "rules"
	private const val VOLUME = "volume"
	private const val REPLACEMENT = "replacement"
	private const val REPLACEMENT_VOLUME = "replacementVolume"
	private const val REPLACEMENT_PITCH = "replacementPitch"
	private const val RECENT_LIMIT = 100
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
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
}
