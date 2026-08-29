package io.github.dzkchen.dhen.privacy

import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
		assertEquals(ServerPacks.Mode.ALWAYS_ON, ServerPacks.effectiveMode(true, ServerPacks.ALWAYS_ON))
	}
}
