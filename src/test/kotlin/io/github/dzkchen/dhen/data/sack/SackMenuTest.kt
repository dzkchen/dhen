package io.github.dzkchen.dhen.data.sack

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SackMenuTest {
	@Test
	fun `only the sack menus answer to the sack title`() {
		assertTrue(SackMenu.isSack("Fishing Sack"))
		assertTrue(SackMenu.isSack("Enchanted Agronomy Sack"))
		assertTrue(SackMenu.isSack("Gold Trophy Fishing Sack"))
		assertFalse(SackMenu.isSack("Sack of Sacks"))
		assertFalse(SackMenu.isSack("Your Bags"))
		assertTrue(SackMenu.isSackOfSacks("Sack of Sacks"))
	}

	@Test
	fun `a stored line reads its amount and its shortened capacity`() {
		val rows = read("Fishing Sack", stack("CLOWNFISH", "§7Stored: §e28,183§7/60.5k"))

		assertEquals(1, rows.size)
		assertEquals(28_183L, rows[0].stored)
		assertEquals(60_500L, rows[0].capacity)
		assertFalse(rows[0].full)
	}

	@Test
	fun `the green stored colour is what marks a sack slot full`() {
		val full = stack("CLOWNFISH", "§7Stored: §a60,500§7/60.5k")

		assertTrue(read("Fishing Sack", full)[0].full)
	}

	@Test
	fun `a rune row keeps each level apart and totals them`() {
		val rune = stack(
			"MAGIC_FIND_RUNE",
			"§7Rune levels:",
			"§fI§7: §e12§7/4.9k",
			"§aII§7: §e5§7/4.9k",
			"§9III§7: §e2§7/4.9k"
		)

		val rows = read("Runes Sack", rune)

		assertEquals(1, rows.size)
		assertEquals(listOf(12L, 5L, 2L), rows[0].parts?.toList())
		assertEquals(19L, rows[0].stored)
	}

	@Test
	fun `gemstone quality counts normalise through the rough flawed fine multipliers`() {
		val gem = named(
			"JADE_GEM",
			"§f❁ Jade Gemstones",
			" §fRough: §e100 §8(100)",
			" §aFlawed: §e2 §8(160)",
			" §9Fine: §e1 §8(6,400)"
		)

		val rows = read("Gemstones Sack", gem)

		assertEquals(1, rows.size)
		assertEquals(listOf(100L, 2L, 1L), rows[0].parts?.toList())
		assertEquals(100L + 2L * 80L + 1L * 6_400L, rows[0].stored)
		assertEquals(listOf("ROUGH_JADE_GEM", "FLAWED_JADE_GEM", "FINE_JADE_GEM"), rows[0].partIds?.toList())
	}

	@Test
	fun `a filtered gemstone stack reports only the quality the lore named`() {
		val filter = ItemFixture.identified("FILTER").also {
			it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§a▶ Flawed"))))
		}
		val stacks = ArrayList<ItemStack>()
		repeat(FILTER_SLOT) { stacks += ItemStack.EMPTY }
		stacks += filter
		stacks[0] = named("JADE_GEM", "§a❁ Flawed Jade Gemstone", " §7Amount: §a5,968")

		val rows = ArrayList<SackRow>()
		SackMenu.read("Gemstones Sack", stacks, rows)

		assertEquals(1, rows.size)
		assertEquals(listOf(UNREPORTED, 5_968L, UNREPORTED), rows[0].parts?.toList())
		assertEquals(5_968L * 80L, rows[0].stored)
	}

	private fun read(title: String, vararg stacks: ItemStack): List<SackRow> {
		val rows = ArrayList<SackRow>()
		SackMenu.read(title, stacks.toList(), rows)
		return rows
	}

	private fun stack(id: String, vararg lore: String): ItemStack =
		ItemFixture.identified(id).also { it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal))) }

	private fun named(id: String, name: String, vararg lore: String): ItemStack =
		stack(id, *lore).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	private companion object {
		const val FILTER_SLOT = 41

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
