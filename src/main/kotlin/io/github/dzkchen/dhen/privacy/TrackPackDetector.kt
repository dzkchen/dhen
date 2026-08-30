package io.github.dzkchen.dhen.privacy

internal object TrackPackDetector {
	private val recentTimestamps = LongArray(MAX_RECENT_REQUESTS)
	private val uniqueHashes = arrayOfNulls<String>(MAX_UNIQUE_VALUES)
	private val uniqueUrls = arrayOfNulls<String>(MAX_UNIQUE_VALUES)
	private val lock = Any()

	private var recentHead = 0
	private var recentSize = 0
	private var hashCount = 0
	private var urlCount = 0
	private var lastRequestMillis = 0L
	private var consecutiveRapidRequests = 0
	private var notified = false

	@JvmStatic
	fun recordRequest(url: String, hash: String) {
		if (ServerPacks.mode == ServerPacks.Mode.OFF) return
		recordRequestAt(url, hash, System.nanoTime() / NANOS_PER_MILLI)
	}

	internal fun recordRequestAt(url: String, hash: String, nowMillis: Long) {
		var rapid = false
		var probing = false
		var fingerprinting = false
		var loggedRapidCount = 0
		var loggedHashCount = 0
		var loggedUrlCount = 0
		synchronized(lock) {
			evict(nowMillis)
			if (recentSize == 0) clearUniqueValues()
			hashCount = addUnique(uniqueHashes, hashCount, hash)
			urlCount = addUnique(uniqueUrls, urlCount, url)
			recentTimestamps[(recentHead + recentSize) % MAX_RECENT_REQUESTS] = nowMillis
			recentSize++

			consecutiveRapidRequests = if (
				lastRequestMillis > 0L && nowMillis - lastRequestMillis < RAPID_REQUEST_INTERVAL_MS
			) consecutiveRapidRequests + 1 else 0
			lastRequestMillis = nowMillis

			if (PrivacyLog.logging) {
				rapid = consecutiveRapidRequests >= RAPID_REQUEST_THRESHOLD ||
					recentWithin(nowMillis, RAPID_WINDOW_MS) >= RAPID_REQUEST_THRESHOLD
				probing = isHashProbing()
				loggedRapidCount = consecutiveRapidRequests
				loggedHashCount = hashCount
				loggedUrlCount = urlCount
			}
			if (!notified && isFingerprinting()) {
				notified = true
				fingerprinting = true
			}
		}

		if (rapid) PrivacyLog.logDetection(CATEGORY, "Rapid request pattern: $loggedRapidCount")
		if (probing) {
			PrivacyLog.logDetection(CATEGORY, "Hash probing: $loggedHashCount hashes across $loggedUrlCount URL(s)")
		}
		if (fingerprinting) {
			PrivacyLog.alert(PrivacyLog.Alert.DANGER, ALERT)
			PrivacyLog.toast(PrivacyLog.Alert.DANGER, TOAST)
		}
	}

	fun reset() {
		synchronized(lock) {
			recentHead = 0
			recentSize = 0
			lastRequestMillis = 0L
			consecutiveRapidRequests = 0
			notified = false
			clearUniqueValues()
		}
	}

	private fun evict(nowMillis: Long) {
		while (
			recentSize > 0 &&
				(nowMillis - recentTimestamps[recentHead] > DETECTION_WINDOW_MS ||
					recentSize >= MAX_RECENT_REQUESTS)
		) {
			recentHead = (recentHead + 1) % MAX_RECENT_REQUESTS
			recentSize--
		}
	}

	private fun addUnique(values: Array<String?>, count: Int, value: String): Int {
		if (value.isEmpty()) return count
		var size = count
		if (size >= MAX_UNIQUE_VALUES) {
			values.fill(null)
			size = 0
		}
		for (index in 0 until size) if (values[index] == value) return size
		values[size] = value
		return size + 1
	}

	private fun clearUniqueValues() {
		uniqueHashes.fill(null)
		uniqueUrls.fill(null)
		hashCount = 0
		urlCount = 0
	}

	private fun recentWithin(nowMillis: Long, windowMillis: Long): Int {
		var count = 0
		for (index in 0 until recentSize) {
			val timestamp = recentTimestamps[(recentHead + index) % MAX_RECENT_REQUESTS]
			if (nowMillis - timestamp < windowMillis) count++
		}
		return count
	}

	private fun isHashProbing(): Boolean =
		recentSize >= MIN_REQUESTS_FOR_HASH_ANALYSIS &&
			hashCount >= UNIQUE_HASH_THRESHOLD &&
			hashCount.toDouble() / urlCount.coerceAtLeast(1) >= HASHES_PER_URL_THRESHOLD

	private fun isFingerprinting(): Boolean =
		recentSize >= FINGERPRINT_REQUEST_THRESHOLD &&
			hashCount >= UNIQUE_HASH_THRESHOLD &&
			hashCount.toDouble() / urlCount.coerceAtLeast(1) >= HASHES_PER_URL_THRESHOLD

	private const val CATEGORY = "TrackPack"
	private const val ALERT = "Resource pack fingerprinting pattern detected!"
	private const val TOAST = "Resource Pack Fingerprinting Detected"
	private const val NANOS_PER_MILLI = 1_000_000L
	private const val DETECTION_WINDOW_MS = 5_000L
	private const val RAPID_WINDOW_MS = 1_000L
	private const val RAPID_REQUEST_INTERVAL_MS = 200L
	private const val FINGERPRINT_REQUEST_THRESHOLD = 5
	private const val RAPID_REQUEST_THRESHOLD = 6
	private const val UNIQUE_HASH_THRESHOLD = 3
	private const val HASHES_PER_URL_THRESHOLD = 3.0
	private const val MIN_REQUESTS_FOR_HASH_ANALYSIS = 2
	private const val MAX_RECENT_REQUESTS = 100
	private const val MAX_UNIQUE_VALUES = 50
}
