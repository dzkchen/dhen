package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.module.Category
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnvilHelperTest {
	@BeforeEach
	fun reset() = AnvilHelper.entered("Bazaar", emptyList())

	@Test
	fun `the module carries the whole feature on its own row`() {
		assertEquals("Anvil Combine Helper", AnvilHelper.name)
		assertEquals(Category.INVENTORY, AnvilHelper.category)
		assertTrue(AnvilHelper.settings.isEmpty())
	}

	@Test
	fun `only a menu named exactly Anvil arms the helper`() {
		AnvilHelper.entered("Anvil", chest())
		assertTrue(AnvilHelper.inAnvil)
		AnvilHelper.entered("Anvil Recipe", chest())
		assertFalse(AnvilHelper.inAnvil)
	}

	@Test
	fun `the two input ids are read from slots 29 and 33`() {
		AnvilHelper.entered("Anvil", chest(left = "HYPERION", right = "ASPECT_OF_THE_END"))
		assertEquals("HYPERION", AnvilHelper.leftId)
		assertEquals("ASPECT_OF_THE_END", AnvilHelper.rightId)
	}

	@Test
	fun `a lone input names the item to look for`() {
		AnvilHelper.entered("Anvil", chest(left = "ENCHANTED_BOOK"))
		assertEquals("ENCHANTED_BOOK", AnvilHelper.wanted())
		AnvilHelper.entered("Anvil", chest(right = "ENCHANTED_BOOK"))
		assertEquals("ENCHANTED_BOOK", AnvilHelper.wanted())
	}

	@Test
	fun `two inputs and no input both ask for nothing`() {
		AnvilHelper.entered("Anvil", chest(left = "HYPERION", right = "HYPERION"))
		assertEquals("", AnvilHelper.wanted())
		AnvilHelper.entered("Anvil", chest())
		assertEquals("", AnvilHelper.wanted())
	}

	@Test
	fun `leaving the anvil forgets both inputs`() {
		AnvilHelper.entered("Anvil", chest(left = "HYPERION"))
		AnvilHelper.entered("Your Backpack", chest(left = "HYPERION"))
		assertFalse(AnvilHelper.inAnvil)
		assertEquals("", AnvilHelper.wanted())
	}

	private fun chest(left: String? = null, right: String? = null): List<ItemStack> =
		List(CHEST_SIZE) {
			when (it) {
				AnvilHelper.LEFT_SLOT -> if (left == null) ItemStack.EMPTY else ItemFixture.identified(left)
				AnvilHelper.RIGHT_SLOT -> if (right == null) ItemStack.EMPTY else ItemFixture.identified(right)
				else -> ItemStack.EMPTY
			}
		}

	private companion object {
		const val CHEST_SIZE = 54

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
