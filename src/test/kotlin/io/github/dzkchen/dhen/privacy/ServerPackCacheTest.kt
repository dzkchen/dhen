package io.github.dzkchen.dhen.privacy

import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerPackCacheTest {
	private val pack: UUID = UUID.randomUUID()
	private val url = "https://packs.example/skyblock.zip"

	@AfterEach
	fun reset() {
		ServerPacks.mode = ServerPacks.Mode.OFF
		ServerPacks.forgetAll()
	}

	@Test
	fun `only an http or https url naming a host can be fetched out of band`() {
		assertTrue(ServerPackCache.fetchable(url))
		assertTrue(ServerPackCache.fetchable("http://packs.example/skyblock.zip"))
		assertFalse(ServerPackCache.fetchable("file:///etc/passwd"))
		assertFalse(ServerPackCache.fetchable("level://pack.zip"))
		assertFalse(ServerPackCache.fetchable("packs.example/skyblock.zip"))
		assertFalse(ServerPackCache.fetchable("https:///skyblock.zip"))
		assertFalse(ServerPackCache.fetchable("not a url at all"))
	}

	@Test
	fun `only ask and always on fast accept a push`() {
		assertFalse(ServerPacks.fastAccept(pack, url, "", true))
		ServerPacks.mode = ServerPacks.Mode.MANUAL
		assertFalse(ServerPacks.fastAccept(pack, url, "", true))
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		assertTrue(ServerPacks.fastAccept(pack, url, "", true))
	}

	@Test
	fun `a url the client cannot fetch itself is left to vanilla`() {
		ServerPacks.mode = ServerPacks.Mode.ALWAYS_ON
		assertFalse(ServerPacks.fastAccept(pack, "file:///tmp/pack.zip", "", true))
	}

	@Test
	fun `fast accepting in ask mode queues the same consent vanilla's path would have`() {
		ServerPacks.mode = ServerPacks.Mode.ASK
		ServerPacks.pushed(pack)
		assertTrue(ServerPacks.fastAccept(pack, url, "", true))
		val consent = ServerPacks.takeConsent(0L)
		assertTrue(consent != null && consent.id == pack && consent.required)
	}
}
