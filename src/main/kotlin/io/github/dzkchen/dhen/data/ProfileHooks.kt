package io.github.dzkchen.dhen.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.data.cookie.CookieState
import io.github.dzkchen.dhen.data.maxwell.MaxwellState
import io.github.dzkchen.dhen.data.maxwell.PowerTuning
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.CookieUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.MaxwellUpdateEvent
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

internal object ProfileHooks : GuardedHooks<ProfileHooks.Channels> {
	override val feed = "Per-profile storage"

	override val failsafe = Failsafe("Dhen {} failed, its per-profile storage is off until restart")

	val authoritative = setOf(PROFILES)

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(
		bus: EventBus,
		store: ConfigStore,
		account: () -> String = { Minecraft.getInstance().user.profileId.toString() }
	) {
		uninstall()
		channels = Channels(bus, store, account)
		subscriptions = arrayOf(
			bus.subscribe<TabWidgetUpdateEvent>(BEFORE_FEATURES) { widget(it) },
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.stripped) },
			bus.subscribe<CookieUpdateEvent> { remembered() },
			bus.subscribe<MaxwellUpdateEvent> { remembered() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
	}

	override fun bound() = channels

	private fun widget(event: TabWidgetUpdateEvent) = guarded("profile widget") { it.widget(event) }

	private fun chatted(message: String) = guarded("profile chat") { it.chatted(message) }

	private fun remembered() = guarded("profile storage") { it.remember() }

	internal class Channels(
		bus: EventBus,
		private val store: ConfigStore,
		private val account: () -> String,
		private val onHypixel: () -> Boolean = { SkyBlockLocation.onHypixel },
		private val inRift: () -> Boolean = { SkyBlockLocation.island == Island.THE_RIFT }
	) {
		private val cookieUpdates = bus.type<CookieUpdateEvent>()
		private val maxwellUpdates = bus.type<MaxwellUpdateEvent>()
		private val profiles = store.load().obj(PROFILES) ?: JsonObject()
		private val profileLine = Pattern.compile(PROFILE_LINE).matcher("")
		private var key: String? = null

		fun widget(event: TabWidgetUpdateEvent) {
			if (event.widget != TabWidget.PROFILE) return
			val name = TabWidgetState.capture(TabWidget.PROFILE, "profile")?.lowercase()?.trim() ?: return
			joined(if (inRift()) name.reversed() else name)
		}

		fun chatted(message: String) {
			if (!onHypixel() || !profileLine.reset(message).matches()) return
			joined(profileLine.group("profile").replace(CO_OP, "", ignoreCase = true).trim().lowercase())
		}

		fun remember() {
			val key = key ?: return
			val stored = snapshot()
			if (stored == (profiles.obj(key) ?: NOTHING_STORED)) return
			profiles.add(key, stored)
			store.save(JsonObject().also { it.add(PROFILES, profiles.deepCopy()) })
		}

		private fun joined(name: String) {
			if (name.isEmpty()) return
			val key = account() + PROFILE_SEPARATOR + name
			if (key == this.key) return
			this.key = key
			restore(profiles.obj(key))
			cookieUpdates.dispatch(CookieUpdateEvent())
			maxwellUpdates.dispatch(MaxwellUpdateEvent())
		}

		private fun restore(stored: JsonObject?) {
			CookieState.expires(stored.long(COOKIE, CookieState.UNKNOWN))
			MaxwellState.reset()
			stored?.text(POWER)?.let(MaxwellState::select)
			MaxwellState.empower(stored.int(MAGICAL_POWER, MaxwellState.ABSENT))
			stored?.array(TUNINGS)?.let { MaxwellState.tune(restoredTunings(it)) }
		}

		private fun snapshot(): JsonObject = JsonObject().also { stored ->
			if (CookieState.expiry != CookieState.UNKNOWN) stored.addProperty(COOKIE, CookieState.expiry)
			MaxwellState.power?.let { stored.addProperty(POWER, it) }
			val magical = MaxwellState.magicalPower
			if (magical != MaxwellState.ABSENT) stored.addProperty(MAGICAL_POWER, magical)
			MaxwellState.tunings?.let { stored.add(TUNINGS, storedTunings(it)) }
		}

		private fun restoredTunings(stored: JsonArray): List<PowerTuning> {
			val restored = ArrayList<PowerTuning>(stored.size())
			for (element in stored) {
				val tuning = element as? JsonObject ?: continue
				restored += PowerTuning(
					tuning.text(NAME) ?: continue,
					tuning.text(AMOUNT) ?: continue,
					tuning.text(COLOR) ?: continue,
					tuning.text(ICON) ?: continue
				)
			}
			return restored
		}

		private fun storedTunings(held: List<PowerTuning>): JsonArray = JsonArray().also { stored ->
			for (tuning in held) {
				stored.add(
					JsonObject().also {
						it.addProperty(NAME, tuning.name)
						it.addProperty(AMOUNT, tuning.amount)
						it.addProperty(COLOR, tuning.color)
						it.addProperty(ICON, tuning.icon)
					}
				)
			}
		}
	}

	private const val PROFILES = "profiles"
	private const val PROFILE_SEPARATOR = "/"
	private const val COOKIE = "cookie"
	private const val POWER = "power"
	private const val MAGICAL_POWER = "magicalPower"
	private const val TUNINGS = "tunings"
	private const val NAME = "name"
	private const val AMOUNT = "amount"
	private const val COLOR = "color"
	private const val ICON = "icon"
	private const val PROFILE_LINE = "(?i)(?:You are playing on profile|Your profile was changed to): (?<profile>.*)"
	private const val CO_OP = "(Co-op)"
	private val NOTHING_STORED = JsonObject()
}
