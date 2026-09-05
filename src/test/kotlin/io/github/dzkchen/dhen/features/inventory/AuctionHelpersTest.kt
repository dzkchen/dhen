package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AuctionHelpersTest {
	@Test
	fun `every auction price wording is read, colour codes and separators and all`() {
		assertEquals(2_599_999_999L, price("§7Buy it now: §62,599,999,999 coins"))
		assertEquals(985_000_000L, price("§7Starting bid: §6985,000,000 coins"))
		assertEquals(67L, price("§7Top bid: §667 coins"))
	}

	@Test
	fun `the underbid highlight reads only the buy it now line`() {
		assertEquals(1_000L, price("§7Buy it now: §61,000 coins", binOnly = true))
		assertNull(price("§7Top bid: §667 coins", binOnly = true))
		assertNull(price("§7Starting bid: §6985,000,000 coins", binOnly = true))
	}

	@Test
	fun `a line that only mentions coins is not a price`() {
		assertNull(price("§7Sold for §6a lot of §7coins"))
		assertNull(price("§7Buy it now: §6soon"))
		assertNull(price("§7Ends in: §e13h 20m"))
	}

	@Test
	fun `the first priced line wins and unpriced lore reads as nothing`() {
		val stack = ItemFixture.lored(
			"§7Seller: §aSomeone",
			"§7Top bid: §61,500 coins",
			"§7Buy it now: §69,000 coins"
		)
		assertEquals(1_500L, AuctionHelpers.listedPrice(SkyBlockItems.rawLore(stack), binOnly = false))
		assertEquals(9_000L, AuctionHelpers.listedPrice(SkyBlockItems.rawLore(stack), binOnly = true))
		assertNull(AuctionHelpers.listedPrice(SkyBlockItems.rawLore(ItemStack.EMPTY), binOnly = false))
	}

	private fun price(line: String, binOnly: Boolean = false): Long? =
		AuctionHelpers.listedPrice(SkyBlockItems.rawLore(ItemFixture.lored(line)), binOnly)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
