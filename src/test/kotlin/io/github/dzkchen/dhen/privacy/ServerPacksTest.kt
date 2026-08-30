package io.github.dzkchen.dhen.privacy

import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerPacksTest {
	private val pack: UUID = UUID.randomUUID()
	private val other: UUID = UUID.randomUUID()

	@AfterEach
	fun reset() {
		ServerPacks.mode = ServerPacks.Mode.OFF
		ServerPacks.forgetAll()
	}

	@Test
	fun `a disabled module records nothing and strips nothing`() {
		ServerPacks.pushed(pack)
		assertFalse(ServerPacks.isWrapped(pack))
		assertFalse(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `manual wraps the pack but lets all of it through`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		ServerPacks.pushed(pack)
		assertTrue(ServerPacks.isWrapped(pack))
		assertFalse(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `always on wraps the pack and strips everything but language files`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		ServerPacks.pushed(pack)
		assertTrue(ServerPacks.isWrapped(pack))
		assertTrue(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `ask starts stripped and apply makes the existing wrapper pass the full pack`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		assertTrue(ServerPacks.stripsContent(pack))

		ServerPacks.apply(pack)

		assertFalse(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `ask offers only the first pack in a session`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = true)
		ServerPacks.pushed(other)
		ServerPacks.offerConsent(other, required = false)

		val consent = requireNotNull(ServerPacks.takeConsent(1_000L))

		assertEquals(pack, consent.id)
		assertTrue(consent.required)
		assertNull(ServerPacks.takeConsent(2_000L))
	}

	@Test
	fun `a displaced consent waits before it is shown again`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = false)
		val consent = requireNotNull(ServerPacks.takeConsent(1_000L))

		ServerPacks.requeueConsent(consent)

		assertNull(ServerPacks.takeConsent(1_749L))
		assertEquals(consent, ServerPacks.takeConsent(1_750L))
	}

	@Test
	fun `disconnect clears the consent session and its delay`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = true)
		requireNotNull(ServerPacks.takeConsent(1_000L))

		ServerPacks.forgetAll()
		ServerPacks.pushed(other)
		ServerPacks.offerConsent(other, required = false)

		assertEquals(other, ServerPacks.takeConsent(1L)?.id)
	}

	@Test
	fun `manual and disabled modes never offer Dhen consent`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = true)
		assertNull(ServerPacks.takeConsent(1_000L))

		ServerPacks.mode = ServerPacks.Mode.OFF
		ServerPacks.offerConsent(other, required = true)
		assertNull(ServerPacks.takeConsent(1_000L))
	}

	@Test
	fun `a mode change discards consent before it can open`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = true)

		ServerPacks.mode = ServerPacks.Mode.MANUAL

		assertNull(ServerPacks.takeConsent(1_000L))
	}

	@Test
	fun `popping a queued pack discards its consent`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		ServerPacks.offerConsent(pack, required = true)

		ServerPacks.popped(pack)

		assertNull(ServerPacks.takeConsent(1_000L))
	}

	@Test
	fun `a re-push under a new mode replaces the earlier decision`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		ServerPacks.pushed(pack)
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		ServerPacks.pushed(pack)
		assertTrue(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `popping one pack leaves the others recorded`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		ServerPacks.pushed(pack)
		ServerPacks.pushed(other)
		ServerPacks.popped(pack)
		assertFalse(ServerPacks.isWrapped(pack))
		assertTrue(ServerPacks.isWrapped(other))
	}

	@Test
	fun `popping without an id drops every pack`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		ServerPacks.pushed(pack)
		ServerPacks.pushed(other)
		ServerPacks.popped(null)
		assertFalse(ServerPacks.isWrapped(pack))
		assertFalse(ServerPacks.isWrapped(other))
	}

	@Test
	fun `a pack the module has no record of is never filtered`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		assertFalse(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `forgetting an applied pack leaves a live wrapper passing everything through`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		ServerPacks.pushed(pack)
		ServerPacks.forgetAll()
		assertFalse(ServerPacks.stripsContent(pack))
	}

	@Test
	fun `turning the module off stops an already wrapped pack from filtering`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		ServerPacks.pushed(pack)
		ServerPacks.mode = ServerPacks.Mode.OFF
		assertFalse(ServerPacks.stripsContent(pack))
		assertFalse(ServerPacks.isWrapped(pack))
	}

	@Test
	fun `the chosen mode label maps onto the published mode`() {
		assertEquals(ServerPacks.Mode.OFF, ServerPacks.effectiveMode(false, ServerPacks.ALWAYS_ON))
		assertEquals(ServerPacks.Mode.MANUAL, ServerPacks.effectiveMode(true, ServerPacks.MANUAL))
		assertEquals(ServerPacks.Mode.ASK, ServerPacks.effectiveMode(true, ServerPacks.ASK))
		assertEquals(ServerPacks.Mode.ALWAYS_ON, ServerPacks.effectiveMode(true, ServerPacks.ALWAYS_ON))
	}

	@Test
	fun `ask and always on suppress Minecraft prompt while manual does not`() {
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		assertFalse(ServerPacks.suppressesPrompt())
		ServerPacks.mode = ServerPacks.Mode.ASK
		assertTrue(ServerPacks.suppressesPrompt())
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		assertTrue(ServerPacks.suppressesPrompt())
		ServerPacks.mode = ServerPacks.Mode.OFF
		assertFalse(ServerPacks.suppressesPrompt())
	}
}
