package io.github.dzkchen.dhen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutomationNoticeTest {
	private val announcements = mutableListOf<String>()
	private var persists = 0
	private val notice = AutomationNotice({ persists++ }, announcements::add)

	@Test
	fun `the first hypixel connection shows and persists the onboarding notice`() {
		notice.install(alreadyShown = false)

		notice.hypixelConnected()
		notice.hypixelConnected()

		assertTrue(notice.hypixelNoticeShown)
		assertEquals(1, persists)
		assertEquals(
			listOf("Dhen does not condone cheating. Its automation features are not for use on Hypixel."),
			announcements
		)
	}

	@Test
	fun `a persisted onboarding notice stays silent on later connections`() {
		notice.install(alreadyShown = true)

		notice.hypixelConnected()

		assertTrue(notice.hypixelNoticeShown)
		assertEquals(0, persists)
		assertTrue(announcements.isEmpty())
	}

	@Test
	fun `a fresh install stays untouched before a hypixel connection`() {
		notice.install(alreadyShown = false)

		assertFalse(notice.hypixelNoticeShown)
		assertEquals(0, persists)
		assertTrue(announcements.isEmpty())
	}

	@Test
	fun `the onboarding latch closes before persistence can reenter`() {
		lateinit var reentrant: AutomationNotice
		reentrant = AutomationNotice(
			persist = { reentrant.hypixelConnected() },
			announceLine = announcements::add
		)
		reentrant.install(alreadyShown = false)

		reentrant.hypixelConnected()

		assertTrue(reentrant.hypixelNoticeShown)
		assertEquals(1, announcements.size)
	}

	@Test
	fun `an approved automation enable prints its module-specific warning`() {
		notice.announce("Zero-ping Etherwarp")

		assertEquals(listOf("Zero-ping Etherwarp should not be used on Hypixel."), announcements)
		assertEquals(0, persists)
	}
}
