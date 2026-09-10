package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.price.BazaarOrder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CraftingRulesTest {
	@Test
	fun `supercraft messages preserve the three resets and optional comma amount`() {
		val bulk = CraftingRules.supercrafted("§eYou Supercrafted §r§r§r§aEnchanted Diamond§r§8x1,024§r§e!")!!
		assertEquals("Enchanted Diamond", bulk.groups["item"]!!.value)
		assertEquals("1,024", bulk.groups["amount"]!!.value)
		val spaced = CraftingRules.supercrafted("§eYou Supercrafted §r§r§r§aEnchanted Mithril §r§8x3§r§e!")!!
		assertEquals("Enchanted Mithril", spaced.groups["item"]!!.value)
		val single = CraftingRules.supercrafted("§eYou Supercrafted §r§r§r§fDiamond§r§e!")!!
		assertNull(single.groups["amount"])
		assertNull(CraftingRules.supercrafted("You Supercrafted Enchanted Diamondx1,024!"))
		assertNull(CraftingRules.supercrafted("§eYou Supercrafted §r§aEnchanted Diamond§r§e!"))
	}

	@Test
	fun `quick craft menu boundaries exclude player inventory and unrelated menus`() {
		assertTrue(CraftingRules.quickSlot("Craft Item", 25))
		assertFalse(CraftingRules.quickSlot("Craft Item", 24))
		assertTrue(CraftingRules.quickSlot("Quick Crafting", 10))
		assertTrue(CraftingRules.quickSlot("Quick Crafting", 44))
		assertFalse(CraftingRules.quickSlot("Quick Crafting", 45))
		assertFalse(CraftingRules.quickSlot("Chest", 25))
		assertTrue("Enchanted Diamond" in QUICK_CRAFTABLE)
		assertFalse("Hyperion" in QUICK_CRAFTABLE)
	}

	@Test
	fun `presets preserve entered order and reject malformed amounts`() {
		assertEquals(listOf(64, 4, 8), CraftingRules.presets("64, 4, nope, -1, 0, 4, 8, 99999999999999"))
		assertTrue(CraftingRules.sign(arrayOf("17", "^^^^^^", "Enter amount", "of crafts")))
		assertFalse(CraftingRules.sign(arrayOf("17", "^^^^^^")))
		assertFalse(CraftingRules.sign(arrayOf("17", "^^^^^^", "Enter amount", "of coins")))
	}

	@Test
	fun `maximum guards both integer division zero cases`() {
		assertNull(CraftingRules.maximum(100, 2, 2, 4))
		assertNull(CraftingRules.maximum(100, 2, 64, 1))
		assertNull(CraftingRules.maximum(100, 2, 64, 0))
		assertEquals(20L, CraftingRules.maximum(100, 40, 8, 1))
	}

	@Test
	fun `loss thresholds are strict and bulk only applies at the maximum`() {
		assertFalse(CraftingRules.blocks(-10_000_000.0, 4, 8, 10.0, 5.0))
		assertTrue(CraftingRules.blocks(-10_000_001.0, 4, 8, 10.0, 5.0))
		assertTrue(CraftingRules.blocks(-5_000_001.0, 8, 8, 10.0, 5.0))
		assertFalse(CraftingRules.blocks(-5_000_001.0, 7, 8, 10.0, 5.0))
		assertFalse(CraftingRules.blocks(-15_000_000.0, 7, 8, 20.0, 10.0))
	}

	@Test
	fun `order depth consumes multiple prices and rejects insufficient liquidity`() {
		val orders = listOf(BazaarOrder(10.0, 3, 1), BazaarOrder(12.0, 4, 1))
		assertEquals(54.0, CraftingRules.cost(orders, 5))
		assertNull(CraftingRules.cost(orders, 8))
		assertNull(CraftingRules.cost(orders, 0))
	}
}
