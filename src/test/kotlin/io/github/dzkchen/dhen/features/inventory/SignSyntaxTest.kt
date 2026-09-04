package io.github.dzkchen.dhen.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignSyntaxTest {
	private fun sign(vararg lines: String): Array<String> = Array(4) { lines.getOrElse(it) { "" } }

	private val auctionPrice = sign("", "^^^^^^^^^^^^^^^", "Your auction", "starting bid")
	private val bazaarAmount = sign("", "^^^^^^^^^^^^^^^", "Enter the", "amount to order")
	private val bazaarPrice = sign("", "^^^^^^^^^^^^^^^", "Enter the price", "per unit")
	private val searchSign = sign("", "^^^^^^^^^^^^^^^", "Enter your", "Enter query")
	private val superCraft = sign("", "^^^^^^", "Enter the", "amount")
	private val renameSign = sign("", "^^^^^^", "Enter name", "")
	private val bestiarySearch = sign("", "^^^^^^", "Search for your", "mob")
	private val flipSign = sign("", "^^Flipping^^", "Enter the", "price")

	@Test
	fun `the calculator takes number signs and leaves search and rename signs alone`() {
		assertTrue(SignCalculator.isInputSign(auctionPrice))
		assertTrue(SignCalculator.isInputSign(bazaarAmount))
		assertTrue(SignCalculator.isInputSign(bazaarPrice))
		assertTrue(SignCalculator.isInputSign(superCraft))
		assertTrue(SignCalculator.isInputSign(flipSign))
		assertFalse(SignCalculator.isInputSign(searchSign))
		assertFalse(SignCalculator.isInputSign(renameSign))
		assertFalse(SignCalculator.isInputSign(bestiarySearch))
		assertFalse(SignCalculator.isInputSign(sign("", "", "", "")))
		assertFalse(SignCalculator.isInputSign(sign("", "^^^^^^^^^^^^^^^", "Search the", "Enter query")))
	}

	@Test
	fun `a search sign is the one asking for a query`() {
		assertTrue(SearchOverlay.isSearchSign(searchSign))
		assertFalse(SearchOverlay.isSearchSign(auctionPrice))
		assertFalse(SearchOverlay.isSearchSign(bestiarySearch))
	}

	@Test
	fun `the menu the sign came from picks the item list`() {
		assertEquals(SearchPlace.AUCTION, SearchOverlay.place("Auction House"))
		assertEquals(SearchPlace.AUCTION, SearchOverlay.place("Cosmetics Auctions"))
		assertEquals(SearchPlace.BAZAAR, SearchOverlay.place("Bazaar"))
		assertEquals(SearchPlace.MUSEUM, SearchOverlay.place("Your Museum"))
		assertEquals(null, SearchOverlay.place("Ender Chest (1/9)"))
	}

	@Test
	fun `a search longer than one line breaks on a space and never mid word`() {
		assertEquals("Hyperion" to "", SearchOverlay.splitOverTwoLines("Hyperion"))
		assertEquals("Necron's Handle" to "", SearchOverlay.splitOverTwoLines("Necron's Handle"))
		assertEquals("Shadow Assassin" to "Chestplate", SearchOverlay.splitOverTwoLines("Shadow Assassin Chestplate"))
		assertEquals("Superboomtnttnt" to "", SearchOverlay.splitOverTwoLines("Superboomtnttnttnt"))
	}

	@Test
	fun `a search starting with null is quoted so Hypixel does not drop it`() {
		assertEquals("\"Null Ovoid\"", SearchOverlay.quoted("Null Ovoid"))
		assertEquals("\"nullifier\"", SearchOverlay.quoted("nullifier"))
		assertEquals("Hyperion", SearchOverlay.quoted("Hyperion"))
	}
}
