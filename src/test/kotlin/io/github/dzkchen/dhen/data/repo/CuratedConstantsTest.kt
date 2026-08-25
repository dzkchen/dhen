package io.github.dzkchen.dhen.data.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CuratedConstantsTest {
	@Test
	fun `the enchants levelled by use are named, and named in upper case`() {
		assertEquals(
			setOf("ABSORB", "CHAMPION", "COMPACT", "CULTIVATING", "EXPERTISE", "HECATOMB", "TOXOPHILITE"),
			CuratedConstants.stackingEnchants
		)
	}

	@Test
	fun `an enchant nobody caps has no endcap`() {
		assertTrue(CuratedConstants.endcaps("SHARPNESS").isEmpty())
	}

	@Test
	fun `a capped enchant names the level it stops at and the item that takes it further`() {
		val scavenger = CuratedConstants.endcaps("SCAVENGER").single()

		assertEquals(5, scavenger.requiredLevel)
		assertEquals("GOLDEN_BOUNTY", scavenger.endcapItem)
	}

	@Test
	fun `every turbo crop is capped twice, by the gourd and then the enchanted one`() {
		for (crop in CuratedConstants.endcappedEnchants.filter { it.startsWith("TURBO_") }) {
			assertEquals(
				listOf(5 to "TURBO_GOURD", 6 to "ENCHANTED_TURBO_GOURD"),
				CuratedConstants.endcaps(crop).map { it.requiredLevel to it.endcapItem }
			)
		}
	}

	@Test
	fun `every endcapped enchant names a positive level and a non-blank item`() {
		for (enchantment in CuratedConstants.endcappedEnchants) {
			val endcaps = CuratedConstants.endcaps(enchantment)
			assertTrue(endcaps.isNotEmpty(), enchantment)
			for (endcap in endcaps) {
				assertTrue(endcap.requiredLevel > 0, enchantment)
				assertTrue(endcap.endcapItem.isNotBlank(), enchantment)
			}
		}
	}

	@Test
	fun `the tables are keyed the way the fold reads them, in upper case`() {
		assertTrue(CuratedConstants.endcaps("scavenger").isEmpty())
		assertFalse(CuratedConstants.grantedFree("scavenger", 5, "CRYPT_DREADLORD_SWORD"))
		assertFalse("toxophilite" in CuratedConstants.stackingEnchants)
	}

	@Test
	fun `an enchant an item comes with is free only at that item and that level`() {
		assertTrue(CuratedConstants.grantedFree("SCAVENGER", 5, "CRYPT_DREADLORD_SWORD"))
		assertFalse(CuratedConstants.grantedFree("SCAVENGER", 4, "CRYPT_DREADLORD_SWORD"))
		assertFalse(CuratedConstants.grantedFree("SCAVENGER", 5, "HYPERION"))
		assertTrue(CuratedConstants.grantedFree("REPLENISH", 1, "ADVANCED_GARDENING_AXE"))
		assertFalse(CuratedConstants.grantedFree("SHARPNESS", 5, "HYPERION"))
	}

	@Test
	fun `a bundle holds the books its table names, and five when it names none`() {
		assertEquals(5, CuratedConstants.bookBundleAmount("VICIOUS"))
		assertEquals(3, CuratedConstants.bookBundleAmount("REFLECTION"))
		assertEquals(1, CuratedConstants.bookBundleAmount("ULTIMATE_THE_ONE"))
		assertEquals(5, CuratedConstants.bookBundleAmount("SHARPNESS"))
	}

	@Test
	fun `each kuudra prestige tier costs essence, teeth and coins`() {
		for (tier in listOf("HOT", "BURNING", "FIERY", "INFERNAL")) {
			assertEquals(
				setOf("ESSENCE_CRIMSON", "KUUDRA_TEETH", "SKYBLOCK_COIN"),
				CuratedConstants.crimsonPrestigeCost(tier).keys,
				tier
			)
		}
		assertEquals(
			mapOf("ESSENCE_CRIMSON" to 150, "KUUDRA_TEETH" to 10, "SKYBLOCK_COIN" to 2_000_000),
			CuratedConstants.crimsonPrestigeCost("HOT")
		)
		assertTrue(CuratedConstants.crimsonPrestigeCost("").isEmpty())
	}
}
