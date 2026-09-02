package io.github.dzkchen.dhen.privacy

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ServerPacks {
	enum class Mode { OFF, MANUAL, ASK, ALWAYS_ON }

	const val MANUAL = "Manual"
	const val ASK = "Ask"
	const val ALWAYS_ON = "Always On"
	const val CONSENT_DELAY_MILLIS = 750L

	val CHOOSABLE_MODES = listOf(MANUAL, ASK, ALWAYS_ON)

	@Volatile
	var mode: Mode = Mode.OFF

	private val wrapped = ConcurrentHashMap.newKeySet<UUID>()
	private val full = ConcurrentHashMap.newKeySet<UUID>()
	private var consentOffered = false
	private var pendingConsent: UUID? = null
	private var pendingRequired = false
	private var lastConsentAt = NO_CONSENT_TIME

	fun effectiveMode(enabled: Boolean, choice: String): Mode = when {
		!enabled -> Mode.OFF
		choice == ASK -> Mode.ASK
		choice == ALWAYS_ON -> Mode.ALWAYS_ON
		else -> Mode.MANUAL
	}

	@JvmStatic
	fun pushed(id: UUID) {
		if (mode == Mode.OFF) return
		if (mode == Mode.ASK || mode == Mode.ALWAYS_ON) full -= id else full += id
		wrapped += id
	}

	@JvmStatic
	fun fastAccept(id: UUID, url: String, hash: String, required: Boolean): Boolean {
		if (mode != Mode.ASK && mode != Mode.ALWAYS_ON) return false
		if (!ServerPackCache.fetchable(url)) return false
		offerConsent(id, required)
		ServerPackCache.requested(id, url, hash)
		return true
	}

	@JvmStatic
	fun offerConsent(id: UUID, required: Boolean) {
		if (mode != Mode.ASK || consentOffered) return
		consentOffered = true
		pendingConsent = id
		pendingRequired = required
	}

	fun takeConsent(now: Long): Consent? {
		val id = pendingConsent ?: return null
		if (mode != Mode.ASK || id !in wrapped) {
			pendingConsent = null
			return null
		}
		if (lastConsentAt != NO_CONSENT_TIME && now - lastConsentAt < CONSENT_DELAY_MILLIS) return null
		pendingConsent = null
		lastConsentAt = now
		return Consent(id, pendingRequired)
	}

	fun requeueConsent(consent: Consent) {
		if (mode != Mode.ASK || pendingConsent != null || consent.id !in wrapped) return
		pendingConsent = consent.id
		pendingRequired = consent.required
	}

	fun apply(id: UUID) {
		if (id in wrapped) full += id
	}

	@JvmStatic
	fun popped(id: UUID?) {
		if (id == null) {
			forgetAll()
			return
		}
		wrapped -= id
		full -= id
		if (pendingConsent == id) pendingConsent = null
		ServerPackCache.released(id)
		ShaderStripTracker.clear()
	}

	fun forgetAll() {
		wrapped.clear()
		full.clear()
		consentOffered = false
		pendingConsent = null
		pendingRequired = false
		lastConsentAt = NO_CONSENT_TIME
		ServerPackCache.released(null)
		ShaderStripTracker.clear()
	}

	@JvmStatic
	fun isWrapped(id: UUID): Boolean = mode != Mode.OFF && id in wrapped

	@JvmStatic
	fun suppressesPrompt(): Boolean = mode == Mode.ASK || mode == Mode.ALWAYS_ON

	fun stripsContent(id: UUID): Boolean = isWrapped(id) && id !in full

	data class Consent(val id: UUID, val required: Boolean)

	private const val NO_CONSENT_TIME = -1L
}
