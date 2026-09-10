package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WardrobeModelTest {
	@Test
	fun `gray pages preserve stale items until all item rows are empty`() {
		val model = WardrobeModel()
		val page = page()
		page[0] = ItemFixture.identified("ARMOR")
		model.read(0, 3, 1, page)
		for (index in 36..44) page[index] = ItemStack(Items.DYE.gray())
		assertFalse(model.read(0, 3, 1, page))
		assertFalse(model.empty(0))
		page[0] = ItemStack.EMPTY
		assertTrue(model.read(0, 3, 1, page))
		assertTrue(model.empty(0))
	}

	@Test
	fun `red selectors lock all later pages and reading filters never changes records`() {
		val model = WardrobeModel()
		val page = page()
		for (index in 40..44) page[index] = ItemStack(Items.DYE.red())
		model.read(0, 3, 1, page)
		assertFalse(model.locked[3])
		for (index in 4 until WARDROBE_SETS) assertTrue(model.locked[index])
		assertFalse(model.known[20])
		model.visible(20, hideLocked = false, hideEmpty = false, onlyFavorites = false)
		assertFalse(model.known[20])
	}

	@Test
	fun `page latch rejects unrelated and unloaded updates and clears on departure`() {
		val model = WardrobeModel()
		model.waiting = true
		model.waitingPage = 1
		model.read(0, 3, 1, page())
		assertTrue(model.waiting)
		val gray = page()
		for (index in 36..44) gray[index] = ItemStack(Items.DYE.gray())
		model.read(1, 3, 2, gray)
		assertTrue(model.waiting)
		model.read(1, 3, 2, page())
		assertFalse(model.waiting)
		model.waiting = true
		model.leave()
		assertFalse(model.waiting)
		assertEquals(-1, model.window)
	}

	@Test
	fun `favorites filter keeps equipped set and composes with empty and locked filters`() {
		val model = WardrobeModel()
		model.equipped = 2
		assertTrue(model.visible(2, hideLocked = false, hideEmpty = false, onlyFavorites = true))
		assertFalse(model.visible(1, hideLocked = false, hideEmpty = false, onlyFavorites = true))
		model.favorites[1] = true
		assertTrue(model.visible(1, hideLocked = false, hideEmpty = false, onlyFavorites = true))
		assertFalse(model.visible(1, hideLocked = true, hideEmpty = false, onlyFavorites = true))
		assertFalse(model.visible(1, hideLocked = false, hideEmpty = true, onlyFavorites = true))
	}

	private fun page(): MutableList<ItemStack> = MutableList(54) { if (it in 36..44) ItemStack(Items.DYE.lime()) else ItemStack.EMPTY }

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
