package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.price.PriceTables
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PetMaxLevelTest {
	@Test
	fun `only level 100 and level 200 count as maxed`() {
		assertNull(PetMaxLevel.maxed("§aYour §5Ender Dragon §aleveled up to level §981§a!"))
		assertNull(PetMaxLevel.maxed("§aYour §5Ender Dragon §aleveled up to level §9199§a!"))
		assertEquals(100, PetMaxLevel.maxed("§aYour §5Ender Dragon §aleveled up to level §9100§a!")?.level)
		assertEquals(200, PetMaxLevel.maxed("§aYour §6Golden Dragon §aleveled up to level §9200§a!")?.level)
	}

	@Test
	fun `the display name keeps its rarity colour so the id can be built from it`() {
		assertEquals(
			"§5Ender Dragon",
			PetMaxLevel.maxed("§aYour §5Ender Dragon §aleveled up to level §9100§a!")?.displayName
		)
	}

	@Test
	fun `the id builder turns a coloured display name into Dhen's market id`() {
		assertEquals("PET-FLYING_FISH-LEGENDARY", PetMaxLevel.baseMarketId("§6Flying Fish"))
		assertEquals("PET-FLYING_FISH-LEGENDARY-100", PetMaxLevel.maxedMarketId("§6Flying Fish", 100))
		assertEquals("PET-GOLDEN_DRAGON-LEGENDARY-200", PetMaxLevel.maxedMarketId("§6Golden Dragon", 200))
		assertEquals("PET-MEGALODON-EPIC", PetMaxLevel.baseMarketId("§5Megalodon"))
	}

	@Test
	fun `the maxed id the price table now publishes is the one the alert asks for`() {
		assertEquals("PET-GIRAFFE-EPIC-100", PriceTables.neuMaxedPetId("GIRAFFE;3+100"))
		assertEquals("PET-GOLDEN_DRAGON-LEGENDARY-200", PriceTables.neuMaxedPetId("GOLDEN_DRAGON;4+200"))
		assertEquals("PET-GIRAFFE-EPIC", PriceTables.neuMarketId("GIRAFFE;3+100"))
	}

	@Test
	fun `the candy lines are shared, not rebuilt per frame`() {
		assertSame(PetCandyLore.lines(3), PetCandyLore.lines(3))
		assertEquals(2, PetCandyLore.lines(3).size)
		assertEquals("(3/10) Pet Candy Used", PetCandyLore.lines(3).first().string)
		assertEquals(" ", PetCandyLore.lines(3).last().string)
		assertEquals("(11/10) Pet Candy Used", PetCandyLore.lines(11).first().string)
	}

	@Test
	fun `the candy line goes in above MAX LEVEL, and only when Hypixel left it out`() {
		val hidden = lore("§6Legendary", "", "§cMAX LEVEL", "§7Right-click to add this pet")

		assertEquals(2, PetCandyLore.insertion(hidden))
	}

	@Test
	fun `a pet that already shows its candy is left alone`() {
		val shown = lore("§6Legendary", "§a(3/10) Pet Candy Used", "", "§cMAX LEVEL")

		assertEquals(PetCandyLore.ABSENT, PetCandyLore.insertion(shown))
	}

	@Test
	fun `a pet below max level has nowhere to put the line`() {
		assertEquals(PetCandyLore.ABSENT, PetCandyLore.insertion(lore("§6Legendary", "§7Progress to Level 100")))
	}

	private fun lore(vararg lines: String): List<Component> = lines.map(Component::literal)
}
