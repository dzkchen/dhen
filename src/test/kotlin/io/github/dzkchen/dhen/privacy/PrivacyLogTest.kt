package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor

private const val WINDOW_MS = 5000L
private const val MILLI_NANOS = 1_000_000L
private const val PACK_URL = "https://packs.example/pack.zip"

class PrivacyLogTest {
	private val bus = EventBus()
	private val chat = mutableListOf<Component>()
	private var nanos = 0L

	@BeforeEach
	@AfterEach
	fun reset() {
		chat.clear()
		nanos = 0L
		Notifications.clear()
		ClientPrefs.chatAlerts.reset()
		ClientPrefs.toastPopups.reset()
		ClientPrefs.logEvents.reset()
		ClientPrefs.debugAlerts.reset()
		PrivacyLog.install(bus, Executor(Runnable::run), chat::add, NanoClock { nanos })
		PrivacyLog.clearCooldowns()
		ServerPacks.mode = ServerPacks.Mode.OFF
		TrackPackDetector.reset()
		ShaderStripTracker.clear()
	}

	private fun advance(millis: Long) {
		nanos += millis * MILLI_NANOS
	}

	@Test
	fun `a repeat inside the window is suppressed and one after it is not`() {
		assertFalse(PrivacyLog.onCooldown("probe", WINDOW_MS))
		advance(WINDOW_MS - 1)
		assertTrue(PrivacyLog.onCooldown("probe", WINDOW_MS))
		advance(1)
		assertFalse(PrivacyLog.onCooldown("probe", WINDOW_MS))
	}

	@Test
	fun `the cooldown table evicts the least recently used key, not the oldest`() {
		for (index in 0 until PrivacyLog.MAX_COOLDOWNS) PrivacyLog.onCooldown("key$index", WINDOW_MS)
		advance(1)
		PrivacyLog.onCooldown("key0", WINDOW_MS)

		PrivacyLog.onCooldown("overflow", WINDOW_MS)

		assertTrue(PrivacyLog.onCooldown("key0", WINDOW_MS))
		assertFalse(PrivacyLog.onCooldown("key1", WINDOW_MS))
	}

	@Test
	fun `a cooled-down toast is suppressed and the next one after the window is not`() {
		PrivacyLog.toastWithCooldown(PrivacyLog.Alert.DANGER, "Probe", "probe", WINDOW_MS)
		PrivacyLog.toastWithCooldown(PrivacyLog.Alert.DANGER, "Probe", "probe", WINDOW_MS)
		assertEquals(1, Notifications.visible.size)

		advance(WINDOW_MS)
		PrivacyLog.toastWithCooldown(PrivacyLog.Alert.DANGER, "Probe", "probe", WINDOW_MS)

		assertEquals(2, Notifications.visible.size)
	}

	@Test
	fun `disconnecting forgets every cooldown`() {
		PrivacyLog.onCooldown("probe", WINDOW_MS)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertFalse(PrivacyLog.onCooldown("probe", WINDOW_MS))
	}

	@Test
	fun `chat alerts gate the chat line and nothing else`() {
		ClientPrefs.chatAlerts.on = false

		PrivacyLog.alert(PrivacyLog.Alert.DANGER, "Blocked")
		PrivacyLog.detail("why")
		PrivacyLog.toast(PrivacyLog.Alert.DANGER, "Blocked")

		assertTrue(chat.isEmpty())
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `toast popups gate the card and nothing else`() {
		ClientPrefs.toastPopups.on = false

		PrivacyLog.alert(PrivacyLog.Alert.WARNING, "Spotted")
		PrivacyLog.toast(PrivacyLog.Alert.WARNING, "Spotted")

		assertEquals(1, chat.size)
		assertTrue(Notifications.visible.isEmpty())
	}

	@Test
	fun `log events and debug alerts follow their own switches and no others`() {
		ClientPrefs.chatAlerts.on = false
		ClientPrefs.toastPopups.on = false

		assertTrue(PrivacyLog.logging)
		assertFalse(PrivacyLog.debugging)

		ClientPrefs.logEvents.on = false
		ClientPrefs.debugAlerts.on = true

		assertFalse(PrivacyLog.logging)
		assertTrue(PrivacyLog.debugging)
	}

	@Test
	fun `an uninstalled sink drops nothing silently and says nothing off-thread`() {
		PrivacyLog.uninstall()

		PrivacyLog.alert(PrivacyLog.Alert.DANGER, "Blocked")
		PrivacyLog.toast(PrivacyLog.Alert.DANGER, "Blocked")

		assertTrue(chat.isEmpty())
		assertTrue(Notifications.visible.isEmpty())
	}

	@Test
	fun `an alert leads with its severity glyph and a detail line does not`() {
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, "Port scan blocked")
		PrivacyLog.detail("127.0.0.1:8080")

		assertEquals("${PrivacyLog.Alert.DANGER.icon} Port scan blocked", chat[0].string)
		assertEquals("127.0.0.1:8080", chat[1].string)
	}

	@Test
	fun `a 24 hash one URL burst alerts exactly once`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		for (index in 0 until 24) TrackPackDetector.recordRequest(PACK_URL, "hash$index")

		assertEquals(listOf("⛔ Resource pack fingerprinting pattern detected!"), chat.map(Component::getString))
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `fingerprint detection is inert while the pack module is off`() {
		for (index in 0 until 24) TrackPackDetector.recordRequest(PACK_URL, "hash$index")

		assertEquals(0, chat.size)
		assertEquals(0, Notifications.visible.size)
	}

	@Test
	fun `a normal push and an expired partial burst do not alert`() {
		TrackPackDetector.recordRequestAt(PACK_URL, "normal", 1L)
		assertEquals(0, chat.size)
		TrackPackDetector.reset()
		for (index in 0 until 4) TrackPackDetector.recordRequestAt(PACK_URL, "old$index", 10L + index)
		TrackPackDetector.recordRequestAt(PACK_URL, "new", 5_020L)

		assertEquals(0, chat.size)
		assertEquals(0, Notifications.visible.size)
	}

	@Test
	fun `a connection reset rearms the fingerprint alert`() {
		repeat(2) { burst ->
			for (index in 0 until 5) TrackPackDetector.recordRequestAt(PACK_URL, "$burst-$index", index.toLong())
			TrackPackDetector.reset()
		}

		assertEquals(2, chat.size)
		assertEquals(2, Notifications.visible.size)
	}

	@Test
	fun `shader strips wait for a player then announce once`() {
		ShaderStripTracker.onStripped("iris", "shaders/core/one.json")
		ShaderStripTracker.onStripped("iris", "shaders/core/two.json")
		ShaderStripTracker.flushPending(false)
		assertEquals(0, chat.size)

		ShaderStripTracker.flushPending(true)
		ShaderStripTracker.onStripped("iris", "shaders/core/three.json")
		ShaderStripTracker.flushPending(true)

		assertEquals(listOf("⛔ Stripped 2 server-pack shader override(s) targeting 'iris'"), chat.map(Component::getString))
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `a pack pop rearms shader detection and respects the toast cooldown`() {
		ShaderStripTracker.onStripped("iris", "shaders/core/one.json")
		ShaderStripTracker.flushPending(true)
		ServerPacks.popped(UUID.randomUUID())
		ShaderStripTracker.onStripped("iris", "shaders/core/two.json")
		ShaderStripTracker.flushPending(true)

		assertEquals(2, chat.size)
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `a late shader callback from a popped pack stays forgotten`() {
		val stale = ShaderStripTracker.token()
		ShaderStripTracker.clear()
		ShaderStripTracker.onStripped(stale, "iris", "shaders/core/late.json")

		ShaderStripTracker.flushPending(true)

		assertEquals(0, chat.size)
	}

	@Test
	fun `one namespace retains at most 64 shader paths`() {
		for (index in 0 until 100) ShaderStripTracker.onStripped("iris", "shaders/core/$index.json")

		ShaderStripTracker.flushPending(true)

		assertEquals("⛔ Stripped 64 server-pack shader override(s) targeting 'iris'", chat.single().string)
	}
}
