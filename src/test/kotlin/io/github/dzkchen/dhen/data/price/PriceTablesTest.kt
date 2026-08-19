package io.github.dzkchen.dhen.data.price

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PriceTablesTest {
	@Test
	fun `a bazaar enchantment product is keyed by the enchanted book market id`() {
		assertEquals("ENCHANTED_BOOK-BANE_OF_ARTHROPODS-6", BazaarSnapshot.marketId("ENCHANTMENT_BANE_OF_ARTHROPODS_6"))
		assertEquals("ENCHANTED_BOOK-ULTIMATE_WISE-5", BazaarSnapshot.marketId("ENCHANTMENT_ULTIMATE_WISE_5"))
	}

	@Test
	fun `a bazaar product with a damage value is keyed the way the item stack is`() {
		assertEquals("INK_SACK-3", BazaarSnapshot.marketId("INK_SACK:3"))
		assertEquals("ENCHANTED_DIAMOND", BazaarSnapshot.marketId("ENCHANTED_DIAMOND"))
	}

	@Test
	fun `a bazaar product that only looks like an enchantment keeps its own id`() {
		assertEquals("ENCHANTMENT_TABLE", BazaarSnapshot.marketId("ENCHANTMENT_TABLE"))
	}

	@Test
	fun `the spare lowest bin source is read into market ids`() {
		assertEquals("HYPERION", PriceTables.neuMarketId("HYPERION"))
		assertEquals("POTION-HASTE-4", PriceTables.neuMarketId("POTION_HASTE;4"))
		assertEquals("RUNE-DRAGON-3", PriceTables.neuMarketId("DRAGON_RUNE;3"))
		assertEquals("PET-AMMONITE-LEGENDARY", PriceTables.neuMarketId("AMMONITE;4"))
		assertEquals("PET-GIRAFFE-EPIC", PriceTables.neuMarketId("GIRAFFE;3+100"))
	}

	@Test
	fun `a spare lowest bin key whose tier is not a pet tier is dropped rather than guessed`() {
		assertNull(PriceTables.neuMarketId("SOMETHING;9"))
		assertNull(PriceTables.neuMarketId("SOMETHING;X"))
	}

	@Test
	fun `a spare lowest bin listing narrower than a market id folds onto the market id`() {
		assertEquals("NEW_YEAR_CAKE", PriceTables.neuMarketId("NEW_YEAR_CAKE+158"))
		assertEquals("BOUNCY_LEGGINGS", PriceTables.neuMarketId("BOUNCY_LEGGINGS+PERFECT"))
	}

	@Test
	fun `two spare keys that fold onto one market id keep the cheaper listing`() {
		val prices = PriceTables.neuLowestBins("{\"AMMONITE;4\":1200000,\"AMMONITE;4+100\":9000000}")

		assertEquals(1, prices.size)
		assertEquals(1200000.0, prices["PET-AMMONITE-LEGENDARY"])
	}

	@Test
	fun `the lowest bin source is read as market id to price`() {
		val prices = PriceTables.lowestBins("{\"ENCHANTED_BOOK-ANGLER-6\":500.0,\"BROKEN\":\"nope\"}")

		assertEquals(1, prices.size)
		assertEquals(500.0, prices["ENCHANTED_BOOK-ANGLER-6"])
	}

	@Test
	fun `a lowest bin listing with a damage value is keyed the way the item stack is`() {
		val prices = PriceTables.lowestBins("{\"FISHING_ROD:44\":700.0}")

		assertEquals(700.0, prices["FISHING_ROD-44"])
	}

	@Test
	fun `npc prices are read only for the items that have one`() {
		val prices = PriceTables.npcPrices(
			"{\"items\":[{\"id\":\"MANDRAA\",\"npc_sell_price\":1},{\"id\":\"CARPET:11\",\"npc_sell_price\":2},{\"id\":\"NO_PRICE\"}]}"
		)

		assertEquals(2, prices.size)
		assertEquals(1.0, prices["MANDRAA"])
		assertEquals(2.0, prices["CARPET-11"])
	}
}
