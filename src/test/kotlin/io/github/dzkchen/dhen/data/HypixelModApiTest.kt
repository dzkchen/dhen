package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.EventBus
import net.hypixel.data.type.GameType
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HypixelModApiTest {
	@BeforeEach
	fun install() {
		HypixelLocationHooks.install(EventBus())
	}

	@AfterEach
	fun uninstall() {
		HypixelLocationHooks.uninstall()
	}

	@Test
	fun `a skyblock location packet lands in the fields it names`() {
		HypixelModApi.located(ClientboundLocationPacket("mini5B", GameType.SKYBLOCK, "lobbyname", "dungeon", "Dungeon"))

		assertEquals("mini5B", SkyBlockLocation.serverName)
		assertEquals("dungeon", SkyBlockLocation.mode)
		assertEquals("Dungeon", SkyBlockLocation.area)
		assertEquals(Island.CATACOMBS, SkyBlockLocation.island)
		assertTrue(SkyBlockLocation.inSkyBlock)
		assertTrue(SkyBlockLocation.onHypixel)
	}

	@Test
	fun `a packet from another Hypixel game is not skyblock`() {
		HypixelModApi.located(ClientboundLocationPacket("mini7C", GameType.BEDWARS, null, "BEDWARS_EIGHT_ONE", "Waterfall"))

		assertFalse(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertNull(SkyBlockLocation.area)
		assertTrue(SkyBlockLocation.onHypixel)
	}

	@Test
	fun `a lobby packet carries no server type at all`() {
		HypixelModApi.located(ClientboundLocationPacket("lobby3", null, "lobby3", null, null))

		assertFalse(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertEquals("lobby3", SkyBlockLocation.serverName)
	}
}
