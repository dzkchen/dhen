package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.item.PetInfo
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PetHelpersTest {
	private val tooltip = PetExpTooltip()

	@Test
	fun `pet experience tooltip uses the source thresholds and level markers`() {
		val bingo = pet("BINGO", "COMMON", 2_812_392.5)
		val dragon = pet("GOLDEN_DRAGON", "LEGENDARY", 25_353_229.0)

		assertEquals("Progress to Level 100: 50.00%", tooltip.lines(bingo, dragonEgg = true, maxLevel = 100)[0].string)
		assertTrue(tooltip.lines(dragon, dragonEgg = true, maxLevel = 200)[0].string.startsWith("Progress to Level 100:"))
		assertTrue(tooltip.lines(dragon, dragonEgg = false, maxLevel = 200)[0].string.startsWith("Progress to Level 200:"))
		assertTrue(tooltip.lines(pet("GOLDEN_DRAGON", "LEGENDARY", 210_255_385.0), false, 200).isEmpty())
	}

	@Test
	fun `pet experience lines are inserted after either vanilla progress marker`() {
		assertEquals(3, tooltip.insertionIndex(listOf(Component.literal("Name"), Component.literal("MAX LEVEL"))))
		assertEquals(4, tooltip.insertionIndex(listOf(Component.literal("Name"), Component.literal("Progress to Level 83"))))
		assertEquals(-1, tooltip.insertionIndex(listOf(Component.literal("Name"))))
	}

	@Test
	fun `George chooses the cheapest allowed rarity and totals only priced pets`() {
		val helper = GeorgeHelper()
		val prices = mapOf(
			"PET-BLACK_CAT-LEGENDARY" to 12_000_000.0,
			"PET-BLACK_CAT-EPIC" to 8_000_000.0,
			"PET-JELLYFISH-EPIC" to 4_000_000.0
		)
		val lore = listOf(
			Component.literal("  Legendary Black Cat"),
			Component.literal("  Epic Jellyfish"),
			Component.literal("Not a wanted pet")
		)

		val requested = helper.buildLines(lore, false, prices::get)
		val otherTiers = helper.buildLines(lore, true, prices::get)

		assertTrue(requested[1].contains("Legendary Black Cat") && requested[1].contains("12,000,000"))
		assertTrue(otherTiers[1].contains("Epic Black Cat") && otherTiers[1].contains("8,000,000"))
		assertEquals("§7Total Cost: §612,000,000 coins", otherTiers.last())
	}

	@Test
	fun `an unavailable adjacent rarity never beats a real price`() {
		val lines = GeorgeHelper().buildLines(
			listOf(Component.literal("Rare Frost Wisp")),
			true
		) { id -> if (id == "PET-FROST_WISP-UNCOMMON") 5_000_000.0 else null }

		assertTrue(lines[1].contains("Uncommon Frost Wisp"))
		assertTrue(lines[1].contains("5,000,000"))
	}

	private fun pet(type: String, tier: String, exp: Double): PetInfo = PetInfo(type, tier, exp, null, 0, null)
}
