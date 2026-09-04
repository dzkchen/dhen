package io.github.dzkchen.dhen.features.visual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WaypointsTest {
	private fun parsed(line: String): ChatCoordinates =
		requireNotNull(chatCoordinates(line)) { "no coordinates in: $line" }

	@Test
	fun `a party line with a rank and an emblem yields the sender and the block`() {
		val shared = parsed("Party > [MVP+] Bob ⚒: x: 12, y: 70, z: -34")
		assertEquals(PARTY_CHANNEL, shared.channel)
		assertEquals("Bob", shared.sender)
		assertEquals(12, shared.x)
		assertEquals(70, shared.y)
		assertEquals(-34, shared.z)
		assertEquals("", shared.note)
	}

	@Test
	fun `public chat carries no channel and still parses`() {
		val shared = parsed("[VIP] Alice: x: -1, y: 2, z: 3")
		assertEquals("", shared.channel)
		assertEquals("Alice", shared.sender)
		assertEquals(-1, shared.x)
	}

	@Test
	fun `the skyblock level badge and its emblem sit in front of the rank`() {
		assertEquals("lrg89", parsed("[209] [MVP+] lrg89: x: 1, y: 2, z: 3").sender)
		assertEquals("lrg89", parsed("[266] ♫ [MVP+] lrg89: x: 1, y: 2, z: 3").sender)
		assertEquals("nea89o", parsed("[58] nea89o: x: 1, y: 2, z: 3").sender)
		assertEquals("Bob", parsed("Party > [209] [MVP+] Bob: x: 1, y: 2, z: 3").sender)
	}

	@Test
	fun `an unranked name with no channel parses`() {
		val shared = parsed("Bob: x: 0, y: 0, z: 0")
		assertEquals("Bob", shared.sender)
	}

	@Test
	fun `the commas after x and y are optional`() {
		val shared = parsed("Party > Bob: x: 5 y: 6 z: 7")
		assertEquals(5, shared.x)
		assertEquals(6, shared.y)
		assertEquals(7, shared.z)
	}

	@Test
	fun `a trailing note is taken without its pipe`() {
		val shared = parsed("Party > Bob: x: 5, y: 6, z: 7 | left of the crypt")
		assertEquals("left of the crypt", shared.note)
	}

	@Test
	fun `a trailing note without a pipe is still taken`() {
		val shared = parsed("Party > Bob: x: 5, y: 6, z: 7 second chest")
		assertEquals("second chest", shared.note)
	}

	@Test
	fun `the guild channel is reported so the module can drop it`() {
		val shared = parsed("Guild > Bob: x: 5, y: 6, z: 7")
		assertEquals(GUILD_CHANNEL, shared.channel)
	}

	@Test
	fun `the co-op channel keeps its hyphen`() {
		val shared = parsed("Co-op > Bob: x: 5, y: 6, z: 7")
		assertEquals("Co-op", shared.channel)
	}

	@Test
	fun `coordinates past five thousand on any axis are out of bounds`() {
		assertFalse(outOfBounds(5000, -5000, 0))
		assertTrue(outOfBounds(5001, 0, 0))
		assertTrue(outOfBounds(0, 0, -5001))
	}

	@Test
	fun `the distance is dropped from the label inside the cutoff and kept outside it`() {
		assertEquals("Bob", waypointLabel("Bob", 20, 50))
		assertEquals("Bob", waypointLabel("Bob", 50, 50))
		assertEquals("Bob (51m)", waypointLabel("Bob", 51, 50))
		assertEquals("Bob (0m)", waypointLabel("Bob", 0, 0))
	}

	@Test
	fun `opacity fades from a fifth up to full between four and a half and a hundred blocks`() {
		assertEquals(0.2f, dynamicOpacity(0.0))
		assertEquals(0.2f, dynamicOpacity(4.5))
		assertEquals(1.0f, dynamicOpacity(100.0))
		assertEquals(1.0f, dynamicOpacity(500.0))
		assertEquals(1.0f, dynamicOpacity(Double.NaN))
		assertTrue(dynamicOpacity(52.25) in 0.59f..0.61f)
	}

	@Test
	fun `the label never shrinks below the text scale and grows with distance`() {
		assertEquals(0.7f, labelScale(0.0, 0.7))
		assertEquals(0.7f, labelScale(20.0, 0.7))
		assertEquals(1.4f, labelScale(40.0, 0.7), 1.0e-6f)
	}

	@Test
	fun `the nearest hub warp wins and the crypt pays its extra blocks`() {
		assertEquals("hub", nearestWarp(0.0, 77.0, 0.0))
		assertEquals("museum", nearestWarp(29.5, 72.0, 1.5))
		assertEquals("crypt", nearestWarp(-160.5, 62.0, -106.5))
		assertEquals("castle", nearestWarp(-250.0, 130.0, 45.0))
	}

	@Test
	fun `saved waypoints survive a round trip through their stored line`() {
		val stored = formatSaved(listOf(SavedPoint("crypt", "HUB", 1, 2, 3), SavedPoint("bank", "HUB", -4, 5, -6)))
		var text = stored
		val book = savedBook { text }
		assertEquals(listOf("crypt", "bank"), book.all().keys.toList())
		val bank = requireNotNull(book.all()["bank"])
		assertEquals("HUB", bank.island)
		assertEquals(-4, bank.x)
		assertEquals(5, bank.y)
		assertEquals(-6, bank.z)
		text = formatSaved(listOf(SavedPoint("crypt", "HUB", 1, 2, 3)))
		assertEquals(listOf("crypt"), book.all().keys.toList())
	}

	@Test
	fun `a malformed stored line is skipped rather than breaking the rest`() {
		val book = savedBook { "crypt HUB 1 2 3\nrubbish\n\nbank DWARVEN_MINES -4 5 -6" }
		assertEquals(listOf("crypt", "bank"), book.all().keys.toList())
	}

	@Test
	fun `a waypoint name has to be one word`() {
		assertNull(savedNameFault("crypt"))
		assertEquals("A waypoint needs a name.", savedNameFault(" "))
		assertEquals("A waypoint name cannot contain a space.", savedNameFault("dark auction"))
	}

	@Test
	fun `the shared coordinate line drops the pipe when there is no note`() {
		assertEquals("x: 1, y: 2, z: 3", coordinateLine(1, 2, 3, ""))
		assertEquals("x: 1, y: 2, z: 3 | second chest", coordinateLine(1, 2, 3, "second chest"))
	}

	@Test
	fun `a line this module sent round trips back into a waypoint`() {
		val sent = coordinateLine(10, 60, -20, "second chest")
		val shared = parsed("Party > [MVP+] Bob: $sent")
		assertEquals(10, shared.x)
		assertEquals(60, shared.y)
		assertEquals(-20, shared.z)
		assertEquals("second chest", shared.note)
	}

	@BeforeEach
	@AfterEach
	fun defaults() {
		for (setting in Waypoints.settings) setting.reset()
	}

	@Test
	fun `the module declares its controls in the order the reference groups them`() {
		assertEquals("Waypoints", Waypoints.name)
		assertEquals(Category.VISUAL, Waypoints.category)
		assertEquals(
			listOf(
				"From Party Chat",
				"From All Chat",
				"Personal Waypoint",
				"Waypoint Time",
				"Waypoint Color",
				"Dynamic Opacity",
				"Waypoint Opacity",
				"Text Opacity",
				"Text Scale",
				"Distance Cutoff",
				"Hide When Close",
				"Chat Buttons",
				"Ping Waypoint",
				"Ping Keybind",
				"Send Pinged Location",
				"Ping Waypoint Time",
				"Ping Distance",
				"Saved Waypoints"
			),
			Waypoints.settings.map { it.name }
		)
	}

	@Test
	fun `the four ping controls stay hidden until ping waypoint is switched on`() {
		val dependent = listOf("Ping Keybind", "Send Pinged Location", "Ping Waypoint Time", "Ping Distance")
		val ping = Waypoints.settings.filter { it.name in dependent }
		assertEquals(dependent.size, ping.size)
		assertTrue(ping.none { it.isVisible })
		Waypoints.pingWaypointSetting.on = true
		assertTrue(ping.all { it.isVisible })
	}

	@Test
	fun `the saved waypoint store is never shown as a control`() {
		val stored = requireNotNull(Waypoints.settings.firstOrNull { it.name == "Saved Waypoints" })
		assertFalse(stored.isVisible)
	}

	@Test
	fun `the fixed opacity slider only shows while the fade is off`() {
		val fixed = requireNotNull(Waypoints.settings.firstOrNull { it.name == "Waypoint Opacity" })
		assertFalse(fixed.isVisible)
		Waypoints.fadeWithDistanceSetting.on = false
		assertTrue(fixed.isVisible)
	}
}
