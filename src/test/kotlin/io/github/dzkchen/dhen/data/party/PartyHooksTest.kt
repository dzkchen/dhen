package io.github.dzkchen.dhen.data.party

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.PartyEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PartyHooksTest {
	private val bus = EventBus()
	private val events = mutableListOf<String>()
	private var requests = 0
	private var onHypixel = true

	@BeforeEach
	fun install() {
		PartyHooks.install(bus, self = { "Me" }, request = { requests++ }, onHypixel = { onHypixel })
		bus.subscribe<PartyEvent.Joined> { events += "joined ${it.name}" }
		bus.subscribe<PartyEvent.Left> { events += "left ${it.name}" }
		bus.subscribe<PartyEvent.LeaderChanged> { events += "leader ${it.previous}>${it.leader}" }
		bus.subscribe<PartyEvent.Disbanded> { events += "disbanded" }
		bus.subscribe<PartyEvent.Updated> { events += "updated" }
	}

	@AfterEach
	fun uninstall() {
		PartyHooks.uninstall()
		SkyBlockLocation.reset()
	}

	@Test
	fun `joining someone's party seeds them, you and the leader`() {
		chat("You have joined [MVP+] Alice's party!")

		assertEquals(listOf("Alice", "Me"), PartyState.members)
		assertEquals("Alice", PartyState.leader)
		assertTrue(PartyState.inParty)
		assertFalse(PartyState.isLeader)
		assertEquals(listOf("joined Alice", "leader null>Alice", "joined Me", "updated"), events)
	}

	@Test
	fun `another player joining is added to the roster`() {
		chat("[MVP+] Bob joined the party.")

		assertEquals(listOf("Bob"), PartyState.members)
		assertTrue(PartyState.inParty)
	}

	@Test
	fun `every way a member is removed drops them from the roster`() {
		val lines = listOf(
			"[MVP+] Bob has left the party.",
			"[MVP+] Bob has been removed from the party.",
			"Kicked [MVP+] Bob because they were offline.",
			"[MVP+] Bob was removed from your party because they disconnected."
		)

		for (line in lines) {
			reinstall()
			chat("You have joined [MVP+] Alice's party!")
			chat("[MVP+] Bob joined the party.")

			chat(line)

			assertEquals(listOf("Alice", "Me"), PartyState.members, line)
			assertTrue(events.contains("left Bob"), line)
		}
	}

	@Test
	fun `a transfer by the leader names the new leader and keeps both players`() {
		chat("The party was transferred to [MVP+] Bob by [MVP+] Alice")

		assertEquals(listOf("Bob", "Alice"), PartyState.members)
		assertEquals("Bob", PartyState.leader)
	}

	@Test
	fun `a transfer because the leader left drops the player who left`() {
		chat("You have joined [MVP+] Alice's party!")
		chat("[MVP+] Bob joined the party.")

		chat("The party was transferred to [MVP+] Bob because [MVP+] Alice left")

		assertEquals(listOf("Me", "Bob"), PartyState.members)
		assertEquals("Bob", PartyState.leader)
	}

	@Test
	fun `a leader disconnecting and rejoining still names the leader`() {
		chat("The party leader, [MVP+] Alice has disconnected, they have 5 minutes to rejoin before the party is disbanded.")
		assertEquals("Alice", PartyState.leader)

		chat("The party leader [MVP+] Bob has rejoined.")
		assertEquals("Bob", PartyState.leader)
	}

	@Test
	fun `a party chat line proves the speaker is in the party`() {
		chat("Party > [MVP+] Bob: hello")

		assertEquals(listOf("Bob"), PartyState.members)
	}

	@Test
	fun `an invite adds the inviter and makes them leader when none is known`() {
		chat("[MVP+] Alice invited [VIP] Bob to the party! They have 60 seconds to accept.")

		assertEquals(listOf("Alice"), PartyState.members)
		assertEquals("Alice", PartyState.leader)
	}

	@Test
	fun `the partying-with line seeds every name it lists`() {
		chat("You'll be partying with: [VIP] FungalBeatle550, Carl")

		assertEquals(listOf("FungalBeatle550", "Carl"), PartyState.members)
	}

	@Test
	fun `queueing in the dungeon finder makes you the leader when none is known`() {
		chat("Party Finder > Your party has been queued in the dungeon finder!")

		assertEquals(listOf("Me"), PartyState.members)
		assertEquals("Me", PartyState.leader)
		assertTrue(PartyState.isLeader)
	}

	@Test
	fun `party finder joins are added for both dungeons and kuudra`() {
		chat("Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)")
		chat("Party Finder > [MVP+] Bob joined the group! (Combat Level 30)")

		assertEquals(listOf("GhostsTM", "Bob"), PartyState.members)
	}

	@Test
	fun `the party list lines split on the bullet and name the leader`() {
		chat("Party Leader: [MVP+] Alice ●")
		chat("Party Moderators: [VIP] Bob ● Carl ●")
		chat("Party Members: Dave ● [MVP++] Erin ●")

		assertEquals(listOf("Alice", "Bob", "Carl", "Dave", "Erin"), PartyState.members)
		assertEquals("Alice", PartyState.leader)
	}

	@Test
	fun `entering a floor names the player who started the run as leader`() {
		chat("-----------------\n[MVP+] Alice entered The Catacombs, Floor VII!\n-----------------")

		assertEquals(listOf("Alice"), PartyState.members)
		assertEquals("Alice", PartyState.leader)
	}

	@Test
	fun `every line that ends a party clears the roster`() {
		val lines = listOf(
			"[MVP+] Alice has disbanded the party!",
			"You have been kicked from the party by [MVP+] Alice ",
			"The party was disbanded because all invites expired and the party was empty.",
			"The party was disbanded because the party leader disconnected.",
			"You left the party.",
			"You are not currently in a party."
		)

		for (line in lines) {
			reinstall()
			chat("You have joined [MVP+] Alice's party!")

			chat(line)

			assertTrue(PartyState.members.isEmpty(), line)
			assertFalse(PartyState.inParty, line)
			assertNull(PartyState.leader, line)
			assertTrue(events.contains("disbanded"), line)
		}
	}

	@Test
	fun `losing the last member ends the party`() {
		chat("[MVP+] Bob joined the party.")
		events.clear()

		chat("[MVP+] Bob has left the party.")

		assertFalse(PartyState.inParty)
		assertEquals(listOf("left Bob", "disbanded", "updated"), events)
	}

	@Test
	fun `you lead the party when the leader is your own name`() {
		chat("Party Leader: Me ●")

		assertTrue(PartyState.isLeader)
	}

	@Test
	fun `a line that says nothing about a party changes nothing`() {
		chat("Bob: are we going?")

		assertTrue(PartyState.members.isEmpty())
		assertTrue(events.isEmpty())
	}

	@Test
	fun `chat is ignored off Hypixel`() {
		onHypixel = false

		chat("[MVP+] Bob joined the party.")

		assertTrue(PartyState.members.isEmpty())
	}

	@Test
	fun `party chat survives the location feed resetting`() {
		SkyBlockLocation.reset()

		chat("[MVP+] Bob joined the party.")

		assertEquals(listOf("Bob"), PartyState.members)
	}

	@Test
	fun `a change asks the mod api once on the next tick, not inside the chat handler`() {
		chat("[MVP+] Bob joined the party.")
		chat("[MVP+] Carl joined the party.")
		assertEquals(0, requests)

		tick()
		tick()

		assertEquals(1, requests)
	}

	@Test
	fun `a party chat line that changes nothing asks the mod api nothing`() {
		chat("[MVP+] Bob joined the party.")
		tick()

		chat("Party > [MVP+] Bob: hello")
		tick()

		assertEquals(1, requests)
	}

	@Test
	fun `the mod api greeting asks for the roster once the client ticks`() {
		PartyHooks.greeted()
		assertEquals(0, requests)

		tick()

		assertEquals(1, requests)
	}

	@Test
	fun `a reply that says we are in no party ends the party`() {
		chat("You have joined [MVP+] Alice's party!")

		PartyHooks.reconciled(false, null, emptyMap(), 0)

		assertFalse(PartyState.inParty)
		assertTrue(PartyState.members.isEmpty())
		assertTrue(events.contains("disbanded"))
	}

	@Test
	fun `a fully resolved reply is the truth and prunes names chat got wrong`() {
		chat("You have joined [MVP+] Alice's party!")
		chat("[MVP+] Ghost joined the party.")

		PartyHooks.reconciled(true, "Alice", mapOf("Alice" to PartyRole.LEADER, "Me" to PartyRole.MEMBER), 2)

		assertEquals(listOf("Alice", "Me"), PartyState.members)
		assertEquals(mapOf("Alice" to PartyRole.LEADER, "Me" to PartyRole.MEMBER), PartyState.roles)
		assertTrue(events.contains("left Ghost"))
	}

	@Test
	fun `a reply whose members are on other servers only adds what it resolved`() {
		chat("You have joined [MVP+] Alice's party!")

		PartyHooks.reconciled(true, null, mapOf("Me" to PartyRole.MEMBER), 3)

		assertEquals(listOf("Alice", "Me"), PartyState.members)
		assertEquals("Alice", PartyState.leader)
	}

	@Test
	fun `a reply promoting a member republishes the roster`() {
		PartyHooks.reconciled(true, "Alice", mapOf("Alice" to PartyRole.LEADER, "Me" to PartyRole.MEMBER), 2)
		events.clear()

		PartyHooks.reconciled(true, "Alice", mapOf("Alice" to PartyRole.LEADER, "Me" to PartyRole.MOD), 2)

		assertEquals(PartyRole.MOD, PartyState.roles["Me"])
		assertEquals(listOf("updated"), events)
	}

	@Test
	fun `a member who leaves takes their role with them`() {
		PartyHooks.reconciled(true, "Alice", mapOf("Alice" to PartyRole.LEADER, "Bob" to PartyRole.MEMBER), 2)

		chat("[MVP+] Bob has left the party.")

		assertNull(PartyState.roles["Bob"])
		assertEquals(PartyRole.LEADER, PartyState.roles["Alice"])
	}

	@Test
	fun `a reply that demotes you drops the leader chat still believes in`() {
		chat("Party Finder > Your party has been queued in the dungeon finder!")
		assertTrue(PartyState.isLeader)

		PartyHooks.reconciled(true, null, mapOf("Me" to PartyRole.MEMBER), 2)

		assertNull(PartyState.leader)
		assertFalse(PartyState.isLeader)
	}

	@Test
	fun `a reply that names no leader keeps one it cannot contradict`() {
		chat("You have joined [MVP+] Alice's party!")

		PartyHooks.reconciled(true, null, mapOf("Me" to PartyRole.MEMBER), 2)

		assertEquals("Alice", PartyState.leader)
	}

	@Test
	fun `a refusal Hypixel may take back leaves the request path alone`() {
		PartyHooks.refused(permanent = false)

		PartyHooks.greeted()
		tick()

		assertTrue(PartyHooks.requesting)
		assertEquals(1, requests)
	}

	@Test
	fun `a refusal Hypixel will not take back stops asking for the session`() {
		PartyHooks.refused(permanent = true)

		PartyHooks.greeted()
		tick()

		assertFalse(PartyHooks.requesting)
		assertEquals(0, requests)
	}

	@Test
	fun `an uninstalled feed forgets the party and stops listening`() {
		chat("You have joined [MVP+] Alice's party!")

		PartyHooks.uninstall()

		assertFalse(PartyHooks.active())
		assertFalse(PartyState.inParty)
		assertTrue(PartyState.members.isEmpty())
		assertNull(PartyState.leader)
	}

	private fun reinstall() {
		PartyHooks.uninstall()
		PartyHooks.install(bus, self = { "Me" }, request = { requests++ }, onHypixel = { onHypixel })
		events.clear()
	}

	private fun chat(line: String) {
		val event = ChatReceiveEvent()
		event.text = Component.literal(line)
		bus.type<ChatReceiveEvent>().dispatch(event)
	}

	private fun tick() = bus.type<ClientTickEvent.End>().dispatch(ClientTickEvent.End)
}
