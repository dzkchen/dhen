package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyRole
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.ModuleManager
import net.hypixel.data.type.GameType
import net.hypixel.modapi.error.BuiltinErrorReason
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.network.chat.Component
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
		PartyHooks.install(bus, self = { "Me" }, request = {}, onHypixel = { true })
		HypixelModApi.install { it.run() }
	}

	@AfterEach
	fun uninstall() {
		HypixelLocationHooks.uninstall()
		PartyHooks.uninstall()
		HypixelModApi.uninstall()
		ScoreboardState.reset()
		TablistState.reset()
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
	fun `a permanent location refusal is diagnosed and resolves current fallback state`() {
		HypixelLocationHooks.greeted()
		ScoreboardState.heading("SBScoreboard", "SKYBLOCK")
		TablistState.read(listOf(" Area: Hub"), listOf(" Area: Hub"))

		HypixelModApi.locationRefused(BuiltinErrorReason.DISABLED)

		assertTrue(HypixelModApi.locationFallbackActive)
		assertTrue(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.HUB, SkyBlockLocation.island)
		assertEquals("Hub", SkyBlockLocation.area)
		assertEquals(
			"Hypixel Mod API location: refused=DISABLED, fallback=true",
			Diagnostics(ModuleManager()).lines().first { it.startsWith("Hypixel Mod API location:") }
		)
	}

	@Test
	fun `an armed fallback follows scoreboard and dungeon tab updates`() {
		HypixelLocationHooks.greeted()
		HypixelModApi.locationRefused(BuiltinErrorReason.NO_LONGER_SUPPORTED)
		ScoreboardState.heading("SBScoreboard", "SKYBLOCK")
		HypixelLocationHooks.scoreboardTitled("SBScoreboard", "SKYBLOCK")
		TablistState.read(listOf(" Dungeon: Catacombs"), listOf(" Dungeon: Catacombs"))
		bus.type<TablistUpdateEvent>().dispatch(TablistUpdateEvent(TablistState.lines, emptyList()))

		assertTrue(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.CATACOMBS, SkyBlockLocation.island)
		assertEquals("Catacombs", SkyBlockLocation.area)

		val previous = TablistState.lines
		TablistState.read(listOf(" Area: Garden Guest"), listOf(" Area: Garden Guest"))
		bus.type<TablistUpdateEvent>().dispatch(TablistUpdateEvent(TablistState.lines, previous))

		assertEquals(Island.GARDEN_GUEST, SkyBlockLocation.island)
		assertTrue(SkyBlockLocation.isGuest)
	}

	@Test
	fun `an unknown fallback area never guesses an island`() {
		HypixelLocationHooks.greeted()
		ScoreboardState.heading("SBScoreboard", "SKYBLOCK")
		TablistState.read(listOf(" Area: Future Island"), listOf(" Area: Future Island"))

		HypixelModApi.locationRefused(BuiltinErrorReason.DISABLED)

		assertEquals(Island.UNKNOWN, SkyBlockLocation.island)
		assertNull(SkyBlockLocation.area)
	}

	@Test
	fun `a real location packet disarms an earlier fallback refusal`() {
		HypixelLocationHooks.greeted()
		HypixelModApi.locationRefused(BuiltinErrorReason.DISABLED)

		HypixelModApi.located(ClientboundLocationPacket("mini5B", GameType.SKYBLOCK, null, "dungeon", "Dungeon"))

		assertFalse(HypixelModApi.locationFallbackActive)
		assertNull(HypixelModApi.locationRefusal)
		assertEquals(Island.CATACOMBS, SkyBlockLocation.island)
	}

	@Test
	fun `a transient location error is visible without arming the fallback`() {
		HypixelLocationHooks.greeted()

		HypixelModApi.locationRefused(BuiltinErrorReason.RATE_LIMITED)

		assertFalse(HypixelModApi.locationFallbackActive)
		assertEquals("RATE_LIMITED", HypixelModApi.locationRefusal)
	}

	@Test
	fun `an incompatible location packet version arms the fallback`() {
		HypixelLocationHooks.greeted()

		HypixelModApi.locationRefused(BuiltinErrorReason.INVALID_PACKET_VERSION)

		assertTrue(HypixelModApi.locationFallbackActive)
		assertEquals("INVALID_PACKET_VERSION", HypixelModApi.locationRefusal)
	}

	@Test
	fun `a changed scoreboard objective clears fallback location even when lines are unchanged`() {
		HypixelLocationHooks.greeted()
		ScoreboardState.heading("SBScoreboard", "SKYBLOCK")
		TablistState.read(listOf(" Area: Hub"), listOf(" Area: Hub"))
		HypixelModApi.locationRefused(BuiltinErrorReason.DISABLED)

		ScoreboardState.heading("lobby", "Lobby")
		HypixelLocationHooks.scoreboardTitled("lobby", "Lobby")

		assertFalse(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.NONE, SkyBlockLocation.island)
	}

	@Test
	fun `fallback updates after disconnect do not recreate a Hypixel location`() {
		HypixelLocationHooks.greeted()
		ScoreboardState.heading("SBScoreboard", "SKYBLOCK")
		TablistState.read(listOf(" Area: Hub"), listOf(" Area: Hub"))
		HypixelModApi.locationRefused(BuiltinErrorReason.DISABLED)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))
		ScoreboardState.reset()
		HypixelLocationHooks.scoreboardTitled("", "")

		assertTrue(HypixelModApi.locationFallbackActive)
		assertFalse(SkyBlockLocation.onHypixel)
		assertFalse(SkyBlockLocation.inSkyBlock)
		assertEquals(Island.NONE, SkyBlockLocation.island)
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
	fun `a throwing adapter drops mod api and location but leaves party chat live`() {
		HypixelModApi.located(ClientboundLocationPacket("mini5B", GameType.SKYBLOCK, null, "dungeon", "Dungeon"))
		assertTrue(HypixelModApi.active())
		assertTrue(HypixelLocationHooks.active())
		assertTrue(PartyHooks.active())

		HypixelModApi.delivered("party info") { error("boom") }

		assertFalse(HypixelModApi.active())
		assertFalse(HypixelLocationHooks.active())
		assertTrue(PartyHooks.active())
		assertEquals(Island.NONE, SkyBlockLocation.island)

		val event = ChatReceiveEvent()
		event.text = Component.literal("[MVP+] Bob joined the party.")
		bus.type<ChatReceiveEvent>().dispatch(event)

		assertEquals(listOf("Bob"), PartyState.members)
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
