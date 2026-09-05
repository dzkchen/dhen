package io.github.dzkchen.dhen.data.repo

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotClickableFiltersTest {
	@Test
	fun `each of the four match shapes fires and nothing else does`() {
		val filter = NameFilter(
			equals = setOf("Builder's Wand"),
			startsWith = listOf("Music Disc"),
			endsWith = listOf(" Rune I"),
			contains = listOf("Personal Deletor ")
		)
		assertTrue(filter.matches("Builder's Wand"))
		assertTrue(filter.matches("Music Disc Cat"))
		assertTrue(filter.matches("Sniper Rune I"))
		assertTrue(filter.matches("Your Personal Deletor 4000"))
		assertFalse(filter.matches("Builder's Wands"))
		assertFalse(filter.matches("The Music Disc"))
		assertFalse(filter.matches("Sniper Rune II"))
		assertFalse(filter.matches("PersonalDeletor 4000"))
	}

	@Test
	fun `armour sets expand into their four pieces and nothing wider`() {
		assertTrue(NotClickableFilters.salvageable.contains("Bouncy Helmet"))
		assertTrue(NotClickableFilters.salvageable.contains("Snow Suit Boots"))
		assertTrue(NotClickableFilters.salvageable.contains("Zombie Soldier Leggings"))
		assertFalse(NotClickableFilters.salvageable.contains("Bouncy"))
		assertFalse(NotClickableFilters.salvageable.contains("Bouncy Cloak"))
	}

	@Test
	fun `loose salvage entries survive the armour expansion`() {
		assertTrue(NotClickableFilters.salvageable.contains("Pickonimbus 2000"))
		assertTrue(NotClickableFilters.salvageable.contains("Zombie Soldier Cutlass"))
	}

	@Test
	fun `the npc list is an allow list and the storage list a block list`() {
		assertTrue(NotClickableFilters.npcSellAllowed.matches("Superboom TNT"))
		assertFalse(NotClickableFilters.npcSellAllowed.matches("Hyperion"))
		assertTrue(NotClickableFilters.storageBlocked.matches("Nether Wart Pouch"))
		assertTrue(NotClickableFilters.storageBlocked.matches("Red New Year Cake Bag"))
		assertFalse(NotClickableFilters.storageBlocked.matches("Ender Chest"))
	}

	@Test
	fun `talismans are blocked from trades and auctions by their suffix`() {
		assertTrue(NotClickableFilters.tradeBlocked.matches("Shiny Cat Talisman"))
		assertTrue(NotClickableFilters.auctionBlocked.matches("Lynx Talisman"))
		assertFalse(NotClickableFilters.auctionBlocked.matches("Spider Talisman"))
	}
}
