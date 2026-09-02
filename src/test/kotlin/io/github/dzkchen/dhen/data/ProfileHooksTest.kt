package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.data.cookie.CookieState
import io.github.dzkchen.dhen.data.maxwell.MaxwellState
import io.github.dzkchen.dhen.data.maxwell.PowerTuning
import io.github.dzkchen.dhen.event.CookieUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.MaxwellUpdateEvent
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.json
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.obj
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProfileHooksTest {
	private val bus = EventBus()

	@AfterEach
	fun clear() {
		ProfileHooks.uninstall()
		TabWidgetState.reset()
		CookieState.reset()
		MaxwellState.reset()
	}

	@Test
	fun `a profile join restores what was written before the restart`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val writer = channels(store(path))
		writer.chatted("You are playing on profile: Lemon")
		CookieState.expires(EXPIRY)
		MaxwellState.select("Bloody")
		MaxwellState.empower(1743)
		MaxwellState.tune(listOf(PowerTuning("Strength", "50", "§c", "❁")))
		writer.remember()
		CookieState.reset()
		MaxwellState.reset()

		channels(store(path)).chatted("You are playing on profile: Lemon")

		assertEquals(EXPIRY, CookieState.expiry)
		assertEquals("Bloody", MaxwellState.power)
		assertEquals(1743, MaxwellState.magicalPower)
		assertEquals(listOf("Strength"), MaxwellState.tunings?.map(PowerTuning::name))
		assertEquals(listOf("50"), MaxwellState.tunings?.map(PowerTuning::amount))
		assertEquals(listOf("§c"), MaxwellState.tunings?.map(PowerTuning::color))
		assertEquals(listOf("❁"), MaxwellState.tunings?.map(PowerTuning::icon))
	}

	@Test
	fun `a second profile shows its own values rather than the first profile's`(@TempDir dir: Path) {
		val channels = channels(store(dir.resolve(FILE)))
		channels.chatted("You are playing on profile: Lemon")
		CookieState.expires(EXPIRY)
		MaxwellState.select("Bloody")
		channels.remember()

		channels.chatted("Your profile was changed to: Mango")

		assertEquals(CookieState.UNKNOWN, CookieState.expiry)
		assertNull(MaxwellState.power)

		channels.chatted("Your profile was changed to: Lemon")

		assertEquals(EXPIRY, CookieState.expiry)
		assertEquals("Bloody", MaxwellState.power)
	}

	@Test
	fun `an empty tuning list reads back as empty rather than unknown`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val writer = channels(store(path))
		writer.chatted("You are playing on profile: Lemon")
		MaxwellState.tune(emptyList())
		writer.remember()
		MaxwellState.reset()

		channels(store(path)).chatted("You are playing on profile: Lemon")

		assertEquals(emptyList<PowerTuning>(), MaxwellState.tunings)
	}

	@Test
	fun `a co-op profile line keys on the name alone`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val channels = channels(store(path))
		channels.chatted("You are playing on profile: Lemon (Co-op)")
		CookieState.expires(EXPIRY)
		channels.remember()

		assertEquals(setOf("$ACCOUNT/lemon"), profiles(path).keySet())
	}

	@Test
	fun `the rift's reversed profile name is read the right way round`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val store = store(path)
		val channels = ProfileHooks.Channels(bus, store, { ACCOUNT }, { true }, inRift = { true })

		widget(channels, "Profile: nomeL")
		CookieState.expires(EXPIRY)
		channels.remember()

		assertEquals(setOf("$ACCOUNT/lemon"), profiles(path).keySet())
	}

	@Test
	fun `nothing is written while the profile name is still unknown`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val channels = channels(store(path))

		CookieState.expires(EXPIRY)
		channels.remember()

		assertFalse(Files.exists(path))
	}

	@Test
	fun `two accounts on a profile of the same name keep separate values`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val mine = channels(store(path))
		mine.chatted("You are playing on profile: Lemon")
		CookieState.expires(EXPIRY)
		mine.remember()
		CookieState.reset()

		val alt = channels(store(path), "alt")
		alt.chatted("You are playing on profile: Lemon")

		assertEquals(CookieState.UNKNOWN, CookieState.expiry)

		CookieState.expires(EXPIRY + 1)
		alt.remember()

		assertEquals(EXPIRY, profiles(path).obj("$ACCOUNT/lemon").long(COOKIE))
		assertEquals(EXPIRY + 1, profiles(path).obj("alt/lemon").long(COOKIE))
	}

	@Test
	fun `rejoining the same profile writes nothing`(@TempDir dir: Path) {
		val store = store(dir.resolve(FILE))
		val channels = channels(store)
		channels.chatted("You are playing on profile: Lemon")
		CookieState.expires(EXPIRY)
		channels.remember()
		val written = store.writeCount

		channels.chatted("Your profile was changed to: Mango")
		channels.chatted("Your profile was changed to: Lemon")
		channels.remember()

		assertEquals(written, store.writeCount)
	}

	@Test
	fun `rejoining a profile read back off disk writes nothing`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		val writer = channels(store(path))
		writer.chatted("You are playing on profile: Lemon")
		CookieState.expires(EXPIRY)
		MaxwellState.select("Bloody")
		MaxwellState.empower(1743)
		MaxwellState.tune(listOf(PowerTuning("Strength", "50", "§c", "❁")))
		writer.remember()
		CookieState.reset()
		MaxwellState.reset()

		val reloaded = store(path)
		ProfileHooks.install(bus, reloaded) { ACCOUNT }
		TabWidgetState.read(TabWidget.PROFILE, PROFILE_LINE, PROFILE_LINE)
		bus.type<TabWidgetUpdateEvent>().dispatch(TabWidgetUpdateEvent(TabWidget.PROFILE, PROFILE_LINE, emptyList()))

		assertEquals(EXPIRY, CookieState.expiry)
		assertEquals(0, reloaded.writeCount)
	}

	@Test
	fun `a profile join asks the scoreboard to rebuild`(@TempDir dir: Path) {
		var cookies = 0
		var maxwells = 0
		bus.subscribe<CookieUpdateEvent> { cookies++ }
		bus.subscribe<MaxwellUpdateEvent> { maxwells++ }

		channels(store(dir.resolve(FILE))).chatted("You are playing on profile: Lemon")

		assertEquals(1, cookies)
		assertEquals(1, maxwells)
	}

	@Test
	fun `install wires the profile widget to the store`(@TempDir dir: Path) {
		val path = dir.resolve(FILE)
		ProfileHooks.install(bus, store(path)) { ACCOUNT }

		TabWidgetState.read(TabWidget.PROFILE, PROFILE_LINE, PROFILE_LINE)
		bus.type<TabWidgetUpdateEvent>().dispatch(TabWidgetUpdateEvent(TabWidget.PROFILE, PROFILE_LINE, emptyList()))
		CookieState.expires(EXPIRY)
		bus.type<CookieUpdateEvent>().dispatch(CookieUpdateEvent())

		assertEquals(EXPIRY, profiles(path).obj("$ACCOUNT/lemon").long(COOKIE))
	}

	private fun store(path: Path) = ConfigStore(
		path,
		CoroutineScope(Dispatchers.Unconfined),
		authoritative = ProfileHooks.authoritative,
		debounce = {}
	)

	private fun channels(store: ConfigStore, account: String = ACCOUNT) =
		ProfileHooks.Channels(bus, store, { account }, { true }, inRift = { false })

	private fun widget(channels: ProfileHooks.Channels, line: String) {
		val lines = listOf(line)
		TabWidgetState.read(TabWidget.PROFILE, lines, lines)
		channels.widget(TabWidgetUpdateEvent(TabWidget.PROFILE, lines, emptyList()))
	}

	private fun profiles(path: Path) = json(Files.readString(path)).obj(PROFILES)!!

	private companion object {
		private const val FILE = "profiles.json"
		private const val PROFILES = "profiles"
		private const val COOKIE = "cookie"
		private const val ACCOUNT = "0000-mine"
		private const val EXPIRY = 1_756_741_200_000L
		private val PROFILE_LINE = listOf("Profile: Lemon")
	}
}
