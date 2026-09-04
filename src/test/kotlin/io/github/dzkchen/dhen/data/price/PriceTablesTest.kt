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
		assertEquals(
			"ATTRIBUTE_SHARD-LIFE_REGENERATION-1",
			PriceTables.neuMarketId("ATTRIBUTE_SHARD_LIFE_REGENERATION;1")
		)
		assertEquals("PET-AMMONITE-LEGENDARY", PriceTables.neuMarketId("AMMONITE;4"))
		assertEquals("PET-GIRAFFE-EPIC", PriceTables.neuMarketId("GIRAFFE;3+100"))
	}

	@Test
	fun `a market id is read back into the NEU name the repo files it under`() {
		assertEquals("ULTIMATE_WISE;5", PriceTables.neuId("ENCHANTED_BOOK-ULTIMATE_WISE-5"))
		assertEquals("AMMONITE;4", PriceTables.neuId("PET-AMMONITE-LEGENDARY"))
		assertEquals("GOLDEN_DRAGON;4", PriceTables.neuId("PET-GOLDEN_DRAGON-LEGENDARY-200"))
		assertEquals("POTION_HASTE;4", PriceTables.neuId("POTION-HASTE-4"))
		assertEquals("DRAGON_RUNE;3", PriceTables.neuId("RUNE-DRAGON-3"))
		assertEquals(
			"ATTRIBUTE_SHARD_LIFE_REGENERATION;1",
			PriceTables.neuId("ATTRIBUTE_SHARD-LIFE_REGENERATION-1")
		)
	}

	@Test
	fun `a market id the repo files under its own name is left for the direct lookup`() {
		assertNull(PriceTables.neuId("HYPERION"))
		assertNull(PriceTables.neuId("LOG-2"))
		assertNull(PriceTables.neuId("PET-AMMONITE-SPECIAL"))
		assertNull(PriceTables.neuId("PET-AMMONITE-LEGENDARY-SHINY"))
		assertNull(PriceTables.neuId("POTION-SPEED-8-ENHANCED"))
		assertNull(PriceTables.neuId("ATTRIBUTE_SHARD-LIFE_REGENERATION-0"))
	}

	@Test
	fun `a spare lowest bin key whose tier is not a pet tier is dropped rather than guessed`() {
		assertNull(PriceTables.neuMarketId("SOMETHING;9"))
		assertNull(PriceTables.neuMarketId("SOMETHING;X"))
		assertNull(PriceTables.neuMarketId("ATTRIBUTE_SHARD_LIFE_REGENERATION;X"))
		assertNull(PriceTables.neuMarketId("ATTRIBUTE_SHARD_;1"))
	}

	@Test
	fun `a spare lowest bin listing narrower than a market id folds onto the market id`() {
		assertEquals("NEW_YEAR_CAKE", PriceTables.neuMarketId("NEW_YEAR_CAKE+158"))
		assertEquals("BOUNCY_LEGGINGS", PriceTables.neuMarketId("BOUNCY_LEGGINGS+PERFECT"))
	}

	@Test
	fun `two spare keys that fold onto one market id keep the cheaper listing`() {
		val prices = PriceTables.neuLowestBins("{\"AMMONITE;4\":1200000,\"AMMONITE;4+100\":9000000}")

		assertEquals(1200000.0, prices["PET-AMMONITE-LEGENDARY"])
	}

	@Test
	fun `a spare key shaped like both a pet and an enchanted book is filed under both`() {
		val prices = PriceTables.neuLowestBins("{\"ULTIMATE_WISE;5\":300000}")

		assertEquals(300000.0, prices["ENCHANTED_BOOK-ULTIMATE_WISE-5"])
		assertEquals(300000.0, prices["PET-ULTIMATE_WISE-MYTHIC"])
	}

	@Test
	fun `a spare key a suffix already disambiguates gets no enchanted book alias`() {
		assertNull(PriceTables.neuBookId("POTION_HASTE;4"))
		assertNull(PriceTables.neuBookId("DRAGON_RUNE;3"))
		assertNull(PriceTables.neuBookId("ATTRIBUTE_SHARD_LIFE_REGENERATION;1"))
		assertNull(PriceTables.neuBookId("HYPERION"))
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
