package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.features.privacy.SpoofAsVanilla
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.locale.Language
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal interface ClientLanguageAccess {
	fun dhenTranslations(): Map<String, String>
}

internal interface SilentPacketTranslation {
	fun markSilent()
}

object TranslationProtection {
	enum class Type(val label: String) {
		TRANSLATION("Translation"),
		KEYBIND("Keybind")
	}

	enum class KeybindResolution {
		ORIGINAL,
		DEFAULT,
		TRANSLATABLE
	}

	private data class AlertKey(val type: Type, val key: String)

	private data class LogKey(
		val type: Type,
		val packet: String,
		val key: String,
		val original: String,
		val spoofed: String
	)

	@JvmField
	val ALLOW_ORIGINAL = String(charArrayOf('\u0000'))

	private const val MAX_DEDUPE_ENTRIES = 500
	private const val MAX_ALERT_VALUE_LENGTH = 256
	private const val HEADER_COOLDOWN_NANOS = 5_000_000_000L
	private const val HEADER = "Key resolution probe detected"
	private const val TOAST_HEADER = "Key Resolution Probe Detected"
	private const val HINT = "Chat and toast alerts can be disabled in Dhen Settings > Privacy."
	private const val LOG_CATEGORY = "Key Resolution"
	private const val NO_HEADER = Long.MIN_VALUE

	private val lock = Any()
	private val alerted = HashSet<AlertKey>()
	private val logged = HashSet<LogKey>()

	@Volatile
	private var clock = NanoClock.SYSTEM

	private var lastHeader = NO_HEADER
	private var headerPending = false
	private var subscription: Handle? = null
	private var persist: () -> Unit = {}
	private var scheduleHint: ((() -> Unit) -> Unit) = ::delayHint
	@Volatile
	private var installation = 0L
	@Volatile
	private var active = false

	fun install(
		bus: EventBus,
		persist: () -> Unit,
		clock: NanoClock = NanoClock.SYSTEM,
		scheduleHint: ((() -> Unit) -> Unit) = ::delayHint
	) {
		uninstall()
		this.persist = persist
		this.clock = clock
		this.scheduleHint = scheduleHint
		subscription = bus.subscribe<GuiOpenEvent> { if (it.screen is ConnectScreen) clearCache() }
		active = true
	}

	fun uninstall() {
		active = false
		subscription?.unsubscribe()
		subscription = null
		persist = {}
		scheduleHint = ::delayHint
		installation++
		clearCache()
	}

	@JvmStatic
	fun protecting(): Boolean = ClientPrefs.keyResolutionSpoofing.on

	@JvmStatic
	fun active(): Boolean = active

	@JvmStatic
	fun vanillaMode(): Boolean = SpoofAsVanilla.isSpoofing()

	@JvmStatic
	fun fakeDefaultKeybinds(): Boolean = ClientPrefs.fakeDefaultKeybinds.on

	@JvmStatic
	fun resolveKeybind(
		fromPacket: Boolean,
		singleplayer: Boolean,
		protecting: Boolean,
		whitelisted: Boolean,
		vanilla: Boolean,
		fakeDefaults: Boolean
	): KeybindResolution = when {
		!fromPacket || singleplayer || !protecting || whitelisted -> KeybindResolution.ORIGINAL
		vanilla && fakeDefaults -> KeybindResolution.DEFAULT
		vanilla -> KeybindResolution.ORIGINAL
		else -> KeybindResolution.TRANSLATABLE
	}

	@JvmStatic
	fun realKeybindValue(name: String): String = try {
		KeyMapping.createNameSupplier(name).get().string
	} catch (_: RuntimeException) {
		name
	}

	@JvmStatic
	fun resolve(
		key: String,
		defaultValue: String,
		fromPacket: Boolean,
		singleplayer: Boolean,
		protecting: Boolean,
		vanillaMode: Boolean
	): String = when {
		!fromPacket || singleplayer -> ALLOW_ORIGINAL
		!protecting || LanguageKeys.isVanillaKey(key) -> ALLOW_ORIGINAL
		!vanillaMode && LanguageKeys.isWhitelistedKey(key) -> ALLOW_ORIGINAL
		else -> LanguageKeys.serverPackValue(key) ?: defaultValue
	}

	internal fun resolve(
		defaultValue: String,
		fromPacket: Boolean,
		singleplayer: Boolean,
		protecting: Boolean,
		vanillaMode: Boolean,
		vanillaKey: Boolean,
		whitelisted: Boolean,
		serverPackValue: String?
	): String = when {
		!fromPacket || singleplayer -> ALLOW_ORIGINAL
		vanillaKey || !protecting -> ALLOW_ORIGINAL
		!vanillaMode && whitelisted -> ALLOW_ORIGINAL
		else -> serverPackValue ?: defaultValue
	}

	@JvmStatic
	fun realValue(language: Language, key: String, defaultValue: String): String =
		(language as? ClientLanguageAccess)?.dhenTranslations()?.get(key) ?: language.getOrDefault(key, defaultValue)

	@JvmStatic
	fun shouldReport(blocked: Boolean): Boolean =
		if (blocked) ClientPrefs.chatAlerts.on || PrivacyLog.logging else PrivacyLog.debugging || PrivacyLog.logging

	@JvmStatic
	fun notifyExploitDetected() {
		if (!ClientPrefs.chatAlerts.on && !PrivacyLog.logging) return
		val now = clock.nanoTime()
		synchronized(lock) {
			if (lastHeader != NO_HEADER && now - lastHeader < HEADER_COOLDOWN_NANOS) return
			headerPending = true
		}
	}

	@JvmStatic
	fun sendDetail(type: Type, key: String, original: String, spoofed: String) {
		sendDetail(type, key, original, spoofed, PacketContext.packetName())
	}

	@JvmStatic
	fun sendDetail(type: Type, key: String, original: String, spoofed: String, packet: String) {
		if (!ClientPrefs.chatAlerts.on) return
		val emitHeader: Boolean
		synchronized(lock) {
			if (alerted.size >= MAX_DEDUPE_ENTRIES) alerted.clear()
			if (!alerted.add(AlertKey(type, key))) return
			emitHeader = headerPending
			if (emitHeader) {
				headerPending = false
				lastHeader = clock.nanoTime()
			}
		}
		if (emitHeader) emitHeader(packet)
		val detail = "[${truncate(key)}] '${truncate(original)}'→'${truncate(spoofed)}'"
		PrivacyLog.detail(if (PrivacyLog.debugging) "[${type.label}:$packet] $detail" else detail)
	}

	@JvmStatic
	fun sendDetailDebug(type: Type, key: String, original: String, spoofed: String) {
		if (PrivacyLog.debugging) sendDetail(type, key, original, spoofed)
	}

	@JvmStatic
	fun sendDetailDebug(type: Type, key: String, original: String, spoofed: String, packet: String) {
		if (PrivacyLog.debugging) sendDetail(type, key, original, spoofed, packet)
	}

	@JvmStatic
	fun logDetection(type: Type, key: String, original: String, spoofed: String) {
		if (!PrivacyLog.logging) return
		logDetection(type, key, original, spoofed, PacketContext.packetName())
	}

	@JvmStatic
	fun logDetection(type: Type, key: String, original: String, spoofed: String, packet: String) {
		if (!PrivacyLog.logging) return
		synchronized(lock) {
			if (logged.size >= MAX_DEDUPE_ENTRIES) logged.clear()
			if (!logged.add(LogKey(type, packet, key, original, spoofed))) return
		}
		PrivacyLog.logDetection(LOG_CATEGORY, "[${type.label}:$packet] '$key' '$original' -> '$spoofed'")
	}

	@JvmStatic
	fun clearDedup() {
		synchronized(lock) {
			alerted.clear()
			logged.clear()
			headerPending = false
		}
	}

	@JvmStatic
	fun clearCache() {
		synchronized(lock) {
			alerted.clear()
			logged.clear()
			lastHeader = NO_HEADER
			headerPending = false
		}
	}

	private fun emitHeader(packet: String) {
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, HEADER)
		PrivacyLog.toast(PrivacyLog.Alert.DANGER, TOAST_HEADER)
		PrivacyLog.logDetection(LOG_CATEGORY, "Probe detected via $packet")
		if (ClientPrefs.alertHintShown.on) return
		ClientPrefs.alertHintShown.on = true
		persist()
		val expectedInstallation = installation
		scheduleHint { if (installation == expectedInstallation) PrivacyLog.detail(HINT) }
	}

	private fun truncate(value: String): String =
		if (value.length <= MAX_ALERT_VALUE_LENGTH) value
		else value.substring(0, MAX_ALERT_VALUE_LENGTH) + "…(${value.length} chars)"

	private fun delayHint(hint: () -> Unit) {
		CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(hint)
	}
}
