package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

internal object PrivacyLog {
	enum class Alert(val color: ChatFormatting, val icon: String) {
		WARNING(ChatFormatting.YELLOW, "⚠"),
		DANGER(ChatFormatting.RED, "⛔")
	}

	const val MAX_COOLDOWNS = 50

	private const val COOLDOWN_CAPACITY = 16
	private const val COOLDOWN_LOAD_FACTOR = 0.75f
	private const val NANOS_PER_MILLI = 1_000_000L
	private const val DETECTION_FORMAT = "[Detection:{}] {}"
	private const val UNREACHABLE_FORMAT = "[{}] {} (Dhen had no client thread to say it on)"
	private const val DETAIL_LABEL = "DETAIL"

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private val cooldowns = object : LinkedHashMap<String, Long>(COOLDOWN_CAPACITY, COOLDOWN_LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean = size > MAX_COOLDOWNS
	}

	@Volatile
	private var clientThread: Executor? = null

	@Volatile
	private var announce: ((Component) -> Unit)? = null

	@Volatile
	private var clock: NanoClock = NanoClock.SYSTEM

	private var subscription: Handle? = null

	val debugging: Boolean
		get() = ClientPrefs.debugAlerts.on

	val logging: Boolean
		get() = ClientPrefs.logEvents.on

	fun install(
		bus: EventBus,
		clientThread: Executor,
		announce: (Component) -> Unit,
		clock: NanoClock = NanoClock.SYSTEM
	) {
		this.clientThread = clientThread
		this.announce = announce
		this.clock = clock
		subscription?.unsubscribe()
		subscription = bus.subscribe<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) clearCooldowns() }
	}

	fun uninstall() {
		subscription?.unsubscribe()
		subscription = null
		clientThread = null
		announce = null
		clearCooldowns()
	}

	fun alert(type: Alert, message: String) {
		if (!ClientPrefs.chatAlerts.on) return
		say(type.name, message) { DhenType.overWorld("${type.icon} $message", type.color) }
	}

	fun detail(message: String) {
		if (!ClientPrefs.chatAlerts.on) return
		say(DETAIL_LABEL, message) { DhenType.overWorld(message, ChatFormatting.DARK_GRAY) }
	}

	fun toast(type: Alert, title: String, message: String? = null) {
		if (!ClientPrefs.toastPopups.on) return
		val executor = clientThread
		if (executor == null) log.info(UNREACHABLE_FORMAT, type.name, title)
		else executor.execute { Notifications.push(type.icon, title, message) }
	}

	fun toastWithCooldown(type: Alert, title: String, key: String, millis: Long) {
		if (onCooldown(key, millis)) return
		toast(type, title)
	}

	fun logDetection(category: String, details: String) {
		if (!logging) return
		log.info(DETECTION_FORMAT, category, details)
	}

	fun onCooldown(key: String, millis: Long): Boolean {
		val now = clock.nanoTime()
		synchronized(cooldowns) {
			val last = cooldowns[key]
			if (last != null && now - last < millis * NANOS_PER_MILLI) return true
			cooldowns[key] = now
			return false
		}
	}

	fun clearCooldowns() {
		synchronized(cooldowns) { cooldowns.clear() }
	}

	private inline fun say(label: String, text: String, line: () -> Component) {
		val executor = clientThread
		if (executor == null) {
			log.info(UNREACHABLE_FORMAT, label, text)
			return
		}
		val component = line()
		executor.execute { announce?.invoke(component) }
	}
}
