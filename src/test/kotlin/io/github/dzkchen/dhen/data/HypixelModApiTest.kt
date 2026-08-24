package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyRole
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.event.EventBus
import net.hypixel.data.type.GameType
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class HypixelModApiTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		HypixelLocationHooks.install(bus)
		PartyHooks.install(bus, self = { "Me" }, request = {})
		HypixelModApi.install { it.run() }
	}

	@AfterEach
	fun uninstall() {
		HypixelLocationHooks.uninstall()
		PartyHooks.uninstall()
		HypixelModApi.uninstall()
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

	@Test
	fun `a party packet lands the leader, the members and their roles`() {
		val leader = UUID.fromString("00000000-0000-0000-0000-00000000000a")
		val member = UUID.fromString("00000000-0000-0000-0000-00000000000b")

		HypixelModApi.partyInfo(partyOf(leader to PartyRole.LEADER, member to PartyRole.MOD)) {
			if (it == leader) "Alice" else "Bob"
		}

		assertTrue(PartyState.inParty)
		assertEquals("Alice", PartyState.leader)
		assertEquals(listOf("Alice", "Bob"), PartyState.members)
		assertEquals(mapOf("Alice" to PartyRole.LEADER, "Bob" to PartyRole.MOD), PartyState.roles)
	}

	@Test
	fun `a member the tab list cannot name is left to the chat roster`() {
		val leader = UUID.fromString("00000000-0000-0000-0000-00000000000a")
		val member = UUID.fromString("00000000-0000-0000-0000-00000000000b")

		HypixelModApi.partyInfo(partyOf(leader to PartyRole.LEADER, member to PartyRole.MEMBER)) {
			if (it == leader) "Alice" else null
		}

		assertEquals(listOf("Alice"), PartyState.members)
		assertEquals(mapOf("Alice" to PartyRole.LEADER), PartyState.roles)
	}

	@Test
	fun `a packet saying we are in no party clears the roster`() {
		val leader = UUID.fromString("00000000-0000-0000-0000-00000000000a")
		HypixelModApi.partyInfo(partyOf(leader to PartyRole.LEADER)) { "Alice" }

		HypixelModApi.partyInfo(ClientboundPartyInfoPacket(2, false, emptyMap())) { "Alice" }

		assertFalse(PartyState.inParty)
		assertTrue(PartyState.members.isEmpty())
	}

	@Test
	fun `a throwing packet handler turns the mod api feed off and reports it off`() {
		assertTrue(HypixelModApi.active())

		HypixelModApi.delivered("party info") { error("boom") }

		assertFalse(HypixelModApi.active())
	}

	@Test
	fun `a latched feed delivers nothing until it is installed again`() {
		var delivered = 0
		HypixelModApi.delivered("party info") { error("boom") }

		HypixelModApi.delivered("party info") { delivered++ }

		assertEquals(0, delivered)
		HypixelModApi.install { it.run() }
		HypixelModApi.delivered("party info") { delivered++ }
		assertEquals(1, delivered)
	}

	private fun partyOf(vararg members: Pair<UUID, PartyRole>): ClientboundPartyInfoPacket =
		ClientboundPartyInfoPacket(
			2,
			true,
			members.associate { (uuid, role) ->
				uuid to ClientboundPartyInfoPacket.PartyMember(uuid, packetRole(role))
			}
		)

	private fun packetRole(role: PartyRole): ClientboundPartyInfoPacket.PartyRole = when (role) {
		PartyRole.LEADER -> ClientboundPartyInfoPacket.PartyRole.LEADER
		PartyRole.MOD -> ClientboundPartyInfoPacket.PartyRole.MOD
		PartyRole.MEMBER -> ClientboundPartyInfoPacket.PartyRole.MEMBER
	}
}
