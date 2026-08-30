package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

class TranslationProtectionTest {
	private val bus = EventBus()
	private val chat = mutableListOf<Component>()
	private var nanos = 0L

	@BeforeEach
	fun prepare() {
		ClientPrefs.chatAlerts.on = true
		ClientPrefs.toastPopups.on = true
		ClientPrefs.logEvents.on = false
		ClientPrefs.debugAlerts.on = false
		ClientPrefs.alertHintShown.on = true
		Notifications.clear()
		PrivacyLog.install(bus, Executor(Runnable::run), chat::add, NanoClock { nanos })
		TranslationProtection.install(bus, {}, NanoClock { nanos }) { it() }
	}

	@AfterEach
	fun reset() {
		TranslationProtection.uninstall()
		PrivacyLog.uninstall()
		ClientPrefs.keyResolutionSpoofing.reset()
		ClientPrefs.fakeDefaultKeybinds.reset()
		ClientPrefs.alertHintShown.reset()
		ClientPrefs.chatAlerts.reset()
		ClientPrefs.toastPopups.reset()
		ClientPrefs.logEvents.reset()
		ClientPrefs.debugAlerts.reset()
	}

	@Test
	fun `resolution preserves local vanilla disabled and whitelisted values`() {
		assertSame(TranslationProtection.ALLOW_ORIGINAL, resolve(fromPacket = false))
		assertSame(TranslationProtection.ALLOW_ORIGINAL, resolve(vanillaKey = true))
		assertSame(TranslationProtection.ALLOW_ORIGINAL, resolve(protecting = false))
		assertSame(TranslationProtection.ALLOW_ORIGINAL, resolve(vanillaMode = false, whitelisted = true))
	}

	@Test
	fun `vanilla and fabric modes block untrusted mod keys with the server pack value first`() {
		assertEquals("key.example.menu", resolve())
		assertEquals("key.example.menu", resolve(vanillaMode = false))
		assertEquals("Server menu", resolve(serverPackValue = "Server menu"))
	}

	@Test
	fun `keybind resolution passes trusted values and chooses defaults or silent translations for blocked values`() {
		assertEquals(
			TranslationProtection.KeybindResolution.ORIGINAL,
			resolveKeybind(fromPacket = false)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.ORIGINAL,
			resolveKeybind(singleplayer = true)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.ORIGINAL,
			resolveKeybind(protecting = false)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.ORIGINAL,
			resolveKeybind(whitelisted = true)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.DEFAULT,
			resolveKeybind(vanilla = true)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.ORIGINAL,
			resolveKeybind(vanilla = true, fakeDefaults = false)
		)
		assertEquals(
			TranslationProtection.KeybindResolution.TRANSLATABLE,
			resolveKeybind()
		)
	}

	@Test
	fun `an identical burst emits one deferred header and one detail`() {
		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "key.example.menu", "Example menu", "key.example.menu")
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "key.example.menu", "Example menu", "key.example.menu")

		assertEquals(2, chat.size)
		assertTrue(chat[0].string.endsWith("Key resolution probe detected"))
		assertEquals("[key.example.menu] 'Example menu'→'key.example.menu'", chat[1].string)
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `packet clear restores details but keeps the header cooldown`() {
		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "first", "First", "first")
		TranslationProtection.clearDedup()
		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "first", "First", "first")

		assertEquals(3, chat.size)
		assertEquals(1, Notifications.visible.size)
	}

	@Test
	fun `the first header persists and schedules the hint once`() {
		var persists = 0
		ClientPrefs.alertHintShown.on = false
		TranslationProtection.install(bus, { persists++ }, NanoClock { nanos }) { it() }

		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "first", "First", "first")
		TranslationProtection.clearCache()
		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "second", "Second", "second")

		assertEquals(1, persists)
		assertTrue(ClientPrefs.alertHintShown.on)
		assertEquals(1, chat.count { it.string.contains("alerts can be disabled") })
	}

	@Test
	fun `detail fields are bounded without changing their reported length`() {
		val poisoned = "x".repeat(257)
		TranslationProtection.notifyExploitDetected()

		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, poisoned, poisoned, poisoned)

		assertEquals(3, Regex("…\\(257 chars\\)").findAll(chat[1].string).count())
	}

	@Test
	fun `an uninstall discards a delayed hint`() {
		var delayed: (() -> Unit)? = null
		ClientPrefs.alertHintShown.on = false
		TranslationProtection.install(bus, {}, NanoClock { nanos }) { delayed = it }
		TranslationProtection.notifyExploitDetected()
		TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, "first", "First", "first")
		val beforeHint = chat.size

		TranslationProtection.uninstall()
		delayed?.invoke()

		assertEquals(beforeHint, chat.size)
	}

	@Test
	fun `uninstall makes the mixin-facing resolver inert`() {
		assertTrue(TranslationProtection.active())

		TranslationProtection.uninstall()

		assertFalse(TranslationProtection.active())
	}

	private fun resolve(
		fromPacket: Boolean = true,
		singleplayer: Boolean = false,
		protecting: Boolean = true,
		vanillaMode: Boolean = true,
		vanillaKey: Boolean = false,
		whitelisted: Boolean = false,
		serverPackValue: String? = null
	): String = TranslationProtection.resolve(
		"key.example.menu",
		fromPacket,
		singleplayer,
		protecting,
		vanillaMode,
		vanillaKey,
		whitelisted,
		serverPackValue
	)

	private fun resolveKeybind(
		fromPacket: Boolean = true,
		singleplayer: Boolean = false,
		protecting: Boolean = true,
		whitelisted: Boolean = false,
		vanilla: Boolean = false,
		fakeDefaults: Boolean = true
	): TranslationProtection.KeybindResolution = TranslationProtection.resolveKeybind(
		fromPacket,
		singleplayer,
		protecting,
		whitelisted,
		vanilla,
		fakeDefaults
	)
}
