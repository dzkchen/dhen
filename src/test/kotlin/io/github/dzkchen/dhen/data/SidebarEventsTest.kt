package io.github.dzkchen.dhen.data

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SidebarEventsTest {
	@AfterEach
	fun clear() {
		SidebarValues.reset()
		TabWidgetState.reset()
		TablistState.reset()
		SkyBlockLocation.reset()
	}

	@Test
	fun `every event carries a label the settings list can name`() {
		assertEquals(28, SidebarEvent.entries.size)
		assertEquals(SidebarEvent.DUNGEONS, SidebarEvent.of("Dungeons"))
	}

	@Test
	fun `the dungeon lines are one block and none of them are left undetected`() {
		inSkyBlock(Island.CATACOMBS)
		SidebarValues.read(
			listOf(
				" §7⏣ §cThe Catacombs §7(F7)",
				" Keys: §c■ §7✗ §a■ §a1x",
				" Time Elapsed: §a17m 12s",
				" Cleared: §c62% §8(§7231§8)",
				" §8- §c§4Power Dragon§a 497.3M§c❤"
			)
		)

		assertEquals(
			listOf(
				"Keys: §c■ §7✗ §a■ §a1x",
				"Time Elapsed: §a17m 12s",
				"Cleared: §c62% §8(§7231§8)",
				"§8- §c§4Power Dragon§a 497.3M§c❤"
			),
			SidebarEvents.lines(SidebarEvent.DUNGEONS)
		)
		assertEquals("The Catacombs §7(F7)", SidebarValues.area)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `Jacob's contest keeps its header and the three lines under it, and leaves the footer alone`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(
			listOf(
				" §eJacob's Contest",
				" §e○ §fCarrot §a18m17s",
				"  Collected §e8,264",
				" §ewww.hypixel.net"
			)
		)

		assertEquals(
			listOf("§eJacob's Contest", "§e○ §fCarrot §a18m17s", " Collected §e8,264"),
			SidebarEvents.lines(SidebarEvent.JACOB_CONTEST)
		)
		assertEquals("§ewww.hypixel.net", SidebarValues.text(SidebarField.FOOTER))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the dark auction shows the item named under Current Item`() {
		inSkyBlock(Island.DARK_AUCTION)
		SidebarValues.read(
			listOf(" Time Left: §b11", " Current Item:", " §5Travel Scroll to Sirius")
		)

		assertEquals(
			listOf("Time Left: §b11", "Current Item:", "§5Travel Scroll to Sirius"),
			SidebarEvents.lines(SidebarEvent.DARK_AUCTION)
		)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the trapper shows the pelts and the mob location under its header`() {
		inSkyBlock(Island.THE_FARMING_ISLANDS)
		SidebarValues.read(listOf(" Pelts: §5711", " Tracker Mob Location:", " §bMushroom Gorge"))

		assertEquals(
			listOf("Pelts: §5711", "Tracker Mob Location:", "§bMushroom Gorge"),
			SidebarEvents.lines(SidebarEvent.TRAPPER)
		)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `Galatea keeps two lines under each of its two contest headers`() {
		inSkyBlock(Island.MOONGLADE_MARSH)
		SidebarValues.read(
			listOf(
				" §fWhispers: §317k§b (+40)",
				" §eAgatha's Contest §a5m28s",
				" §e○ §fFig §a1m2s",
				"  Collected §e120",
				" §eMiria's Contest §a0m35s",
				" §e○ §fMangrove §a2m",
				"  Collected §e64"
			)
		)

		assertEquals(
			listOf(
				"§fWhispers: §317k§b (+40)",
				"§eAgatha's Contest §a5m28s",
				"§e○ §fFig §a1m2s",
				" Collected §e120",
				"§eMiria's Contest §a0m35s",
				"§e○ §fMangrove §a2m",
				" Collected §e64"
			),
			SidebarEvents.lines(SidebarEvent.GALATEA)
		)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the mining block rebuilds the zone event, the raffle and the goblin raid in SkyHanni's shape`() {
		inSkyBlock(Island.DWARVEN_MINES)
		SidebarValues.read(
			listOf(
				" Nearby Players: §a5 §cMAX",
				" Event: §6§LRAFFLE",
				" Zone: §bGoblin Burrows",
				" Tickets: §a8 §7(17.4%)",
				" Pool: §646",
				" Your kills: §c85 ☠",
				" Remaining: §a2 goblins",
				" Fossil Dust: §f3,281 §e(+1)"
			)
		)

		assertEquals(
			listOf(
				"§dBetter Together",
				" Nearby Players: §a5 §cMAX",
				"§6§LRAFFLE",
				"in §bGoblin Burrows",
				"Tickets: §a8 §7(17.4%)",
				"Pool: §646",
				"Your kills: §c85 ☠",
				"Remaining: §a2 goblins",
				"Fossil Dust: §f3,281 §e(+1)"
			),
			SidebarEvents.lines(SidebarEvent.MINING)
		)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the winter block drops a wave that has not started yet`() {
		inSkyBlock(Island.JERRYS_WORKSHOP)
		SidebarValues.read(listOf(" Next Wave: §a§aSoon!", " §cWave 5", " Magma Cubes Left: §c3"))

		assertEquals(listOf("§cWave 5", "Magma Cubes Left: §c3"), SidebarEvents.lines(SidebarEvent.WINTER))
	}

	@Test
	fun `the carnival block stays empty until its own header is on the sidebar`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" §fCarnival Tokens: §e129", " §fAccuracy: §a81.82%"))

		assertEquals(emptyList<String>(), SidebarEvents.lines(SidebarEvent.CARNIVAL))
		assertEquals(emptyList<String>(), SidebarValues.unknown)

		SidebarValues.read(listOf(" §eCarnival§f 85:33:57", " §fCarnival Tokens: §e129"))

		assertEquals(
			listOf("§eCarnival§f 85:33:57", "§fCarnival Tokens: §e129"),
			SidebarEvents.lines(SidebarEvent.CARNIVAL)
		)
	}

	@Test
	fun `the closing warning drops the server code after it`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf("§cServer closing: 03:11 §8m77A"))

		assertEquals(listOf("§cServer closing: 03:11 "), SidebarEvents.lines(SidebarEvent.SERVER_CLOSE))
	}

	@Test
	fun `the spooky block reads the candy count out of the tab list footer`() {
		inSkyBlock(Island.HUB)
		TablistState.frame("", "Ranks, Boosters, & MORE!\nYour Candy: §a1 Green§7, §50 Purple")
		SidebarValues.read(listOf(" §6Spooky Festival§f 50:54"))

		assertEquals(
			listOf("§6Spooky Festival§f 50:54", "§7Your Candy: ", "1 Green, 0 Purple"),
			SidebarEvents.lines(SidebarEvent.SPOOKY)
		)
	}

	@Test
	fun `an active tab list event names itself and the time it ends`() {
		widget(TabWidget.EVENT, listOf("Event: §dHoppity's Hunt", " Ends In: 26h"))

		assertEquals(
			listOf("§dHoppity's Hunt", " Ends in: §e26h"),
			SidebarEvents.lines(SidebarEvent.ACTIVE_TABLIST)
		)
		assertEquals(emptyList<String>(), SidebarEvents.lines(SidebarEvent.STARTING_SOON_TABLIST))
	}

	@Test
	fun `an event the sidebar already shows is not repeated from the tab list`() {
		widget(TabWidget.EVENT, listOf("Event: §6Spooky Festival", " Ends In: 26h"))

		assertEquals(emptyList<String>(), SidebarEvents.lines(SidebarEvent.ACTIVE_TABLIST))

		widget(TabWidget.EVENT, listOf("Event: §d5th SkyBlock Anniversary", " Ends In: 26h"))

		assertEquals(emptyList<String>(), SidebarEvents.lines(SidebarEvent.ACTIVE_TABLIST))
	}

	@Test
	fun `a soon-to-start tab list event names the time it starts`() {
		widget(TabWidget.EVENT, listOf("Event: §6Mining Fiesta", " Starts In: 52min"))

		assertEquals(
			listOf("§6Mining Fiesta", " Starts in: §e52min"),
			SidebarEvents.lines(SidebarEvent.STARTING_SOON_TABLIST)
		)
	}

	@Test
	fun `the broodmother reads the tab list widget and its sidebar line stays hidden`() {
		inSkyBlock(Island.SPIDERS_DEN)
		widget(TabWidget.BROODMOTHER, listOf("Broodmother: §6Soon"))
		SidebarValues.read(listOf(" §4Broodmother§7: §6Soon"))

		assertEquals(listOf("Broodmother: §6Soon"), SidebarEvents.lines(SidebarEvent.BROODMOTHER))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a half-drawn line is swept away without eating the powder line beside it`() {
		inSkyBlock(Island.DWARVEN_MINES)
		SidebarValues.read(listOf(" §2᠅ §fMithril§f: §235,448", " §e§l⚡ §cRedston", "      §ce: §e§b0%"))

		assertEquals("35,448", SidebarValues.powder(PowderKind.MITHRIL))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a line no event claims still surfaces as undetected`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" Something Hypixel added yesterday"))

		assertTrue(SidebarValues.unknown.isNotEmpty())
	}

	@Test
	fun `an event line on the wrong island is left undetected rather than swallowed`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" Tokens: §565"))

		assertEquals(emptyList<String>(), SidebarEvents.lines(SidebarEvent.KUUDRA))
		assertEquals(listOf("Tokens: §565"), SidebarValues.unknown)

		inSkyBlock(Island.KUUDRA)
		SidebarValues.read(listOf(" Tokens: §565"))

		assertEquals(listOf("Tokens: §565"), SidebarEvents.lines(SidebarEvent.KUUDRA))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a claim-only pattern keeps its line off the undetected list without showing it`() {
		inSkyBlock(Island.THE_RIFT)
		SidebarValues.read(listOf(" §fRift Dimension", " Clues: §a0/8"))

		assertEquals(listOf("Clues: §a0/8"), SidebarEvents.lines(SidebarEvent.RIFT))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the objective block keeps its body when an event claims the same line`() {
		inSkyBlock(Island.THE_RIFT)
		SidebarValues.read(listOf(" Objective", " §eFirst Up", " Find and talk with Barry"))

		assertEquals(listOf("Objective", "§eFirst Up"), SidebarValues.objective)
		assertEquals(
			listOf("§eFirst Up", "Find and talk with Barry"),
			SidebarEvents.lines(SidebarEvent.RIFT)
		)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a line under a section header still feeds its own scoreboard row`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(
			listOf(" §eJacob's Contest", " §e○ §fCarrot §a18m17s", "  Collected §e8,264", " Purse: §6100")
		)

		assertEquals("100", SidebarValues.text(SidebarField.PURSE))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the raffle's flavour lines are claimed without being shown`() {
		inSkyBlock(Island.DWARVEN_MINES)
		SidebarValues.read(
			listOf(
				" Find tickets on the",
				" ground and bring them",
				" to the raffle box",
				" Tickets: §a8 §7(17.4%)"
			)
		)

		assertEquals(listOf("Tickets: §a8 §7(17.4%)"), SidebarEvents.lines(SidebarEvent.MINING))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	private fun inSkyBlock(island: Island) {
		SkyBlockLocation.located("mini1A", skyBlock = true, mode = island.modeId, map = null, resolvedIsland = island)
		if (SkyBlockLocation.awaitingGuestTitle) SkyBlockLocation.titled("SKYBLOCK")
	}

	private fun widget(widget: TabWidget, lines: List<String>) =
		TabWidgetState.read(widget, lines, lines.map { it.replace(Regex("§."), "") })
}
