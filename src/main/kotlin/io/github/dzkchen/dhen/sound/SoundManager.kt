package io.github.dzkchen.dhen.sound

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ConfigStore
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

object SoundManager {
	private val volumeLock = Any()
	private val recentLock = Any()
	private val recentIds = arrayOfNulls<Identifier>(RECENT_LIMIT)

	@Volatile
	private var volumes = VolumeSnapshot.EMPTY
	@Volatile
	private var recentVersion = 0L
	private var recentCount = 0
	private var suppressRecentDepth = 0
	private var store: ConfigStore? = null

	internal val recentSoundsVersion: Long
		get() = recentVersion

	internal fun install(store: ConfigStore) = synchronized(volumeLock) {
		this.store = store
		volumes = decode(store.load())
	}

	internal fun uninstall() {
		synchronized(volumeLock) {
			store = null
			volumes = VolumeSnapshot.EMPTY
		}
		clearRecentSounds()
	}

	@JvmStatic
	fun getMultiplier(identifier: Identifier): Float {
		val snapshot = volumes
		for (index in 0 until snapshot.size) {
			if (snapshot.identifiers[index] == identifier) return snapshot.multipliers[index]
		}
		return DEFAULT_MULTIPLIER
	}

	internal fun getVolumePercent(identifier: Identifier): Int =
		(volumes.multiplier(identifier) * PERCENT_SCALE).roundToInt()

	internal fun setVolumePercent(identifier: Identifier, percent: Int): Int = synchronized(volumeLock) {
		val installed = store ?: return@synchronized getVolumePercent(identifier)
		val normalized = normalizePercent(percent)
		volumes = volumes.with(identifier, normalized / PERCENT_SCALE)
		installed.save(encode(volumes))
		normalized
	}

	@JvmStatic
	fun recordPlayedSound(sound: SoundInstance) {
		recordPlayedIdentifier(sound.identifier)
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

	private fun decode(doc: JsonObject): VolumeSnapshot {
		val encoded = doc.get(MULTIPLIERS) as? JsonObject ?: return VolumeSnapshot.EMPTY
		val identifiers = arrayOfNulls<Identifier>(encoded.size())
		val multipliers = FloatArray(encoded.size())
		var size = 0
		for ((rawIdentifier, value) in encoded.entrySet()) {
			val identifier = Identifier.tryParse(rawIdentifier) ?: continue
			val percent = try {
				(value.asFloat * PERCENT_SCALE).roundToInt()
			} catch (exception: RuntimeException) {
				log.warn("Skipping bad sound multiplier for {}", rawIdentifier, exception)
				continue
			}
			identifiers[size] = identifier
			multipliers[size] = normalizePercent(percent) / PERCENT_SCALE
			size++
		}
		return VolumeSnapshot(identifiers, multipliers, size)
	}

	private fun encode(snapshot: VolumeSnapshot): JsonObject {
		val encoded = JsonObject()
		for (index in 0 until snapshot.size) {
			encoded.addProperty(snapshot.identifiers[index].toString(), snapshot.multipliers[index])
		}
		return JsonObject().apply { add(MULTIPLIERS, encoded) }
	}

	private fun normalizePercent(percent: Int): Int {
		val bounded = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
		return ((bounded + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
	}

	private class VolumeSnapshot(
		val identifiers: Array<Identifier?>,
		val multipliers: FloatArray,
		val size: Int
	) {
		fun multiplier(identifier: Identifier): Float {
			for (index in 0 until size) {
				if (identifiers[index] == identifier) return multipliers[index]
			}
			return DEFAULT_MULTIPLIER
		}

		fun with(identifier: Identifier, multiplier: Float): VolumeSnapshot {
			for (index in 0 until size) {
				if (identifiers[index] != identifier) continue
				val changed = multipliers.copyOf()
				changed[index] = multiplier
				return VolumeSnapshot(identifiers, changed, size)
			}
			val extendedIdentifiers = identifiers.copyOf(size + 1)
			val extendedMultipliers = multipliers.copyOf(size + 1)
			extendedIdentifiers[size] = identifier
			extendedMultipliers[size] = multiplier
			return VolumeSnapshot(extendedIdentifiers, extendedMultipliers, size + 1)
		}

		companion object {
			val EMPTY = VolumeSnapshot(emptyArray(), FloatArray(0), 0)
		}
	}

	private const val MULTIPLIERS = "multipliers"
	private const val RECENT_LIMIT = 100
	private const val MIN_PERCENT = 0
	private const val MAX_PERCENT = 200
	private const val STEP_PERCENT = 5
	private const val PERCENT_SCALE = 100f
	private const val DEFAULT_MULTIPLIER = 1f
	private const val PREVIEW_VOLUME = 0.25f
	private const val PREVIEW_PITCH = 1f
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
}
