package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PetWheelCacheTest {
	private val cache = PetWheelCache()

	@Test
	fun `pets titles accept leading and trailing page counters without case sensitivity`() {
		assertTrue(cache.refresh(Component.literal("Pets"), 1, menu()))
		assertTrue(cache.refresh(Component.literal("§a(2/5) PETS"), 1, menu()))
		assertTrue(cache.refresh(Component.literal("pets (2/5)"), 1, menu()))
		assertFalse(cache.refresh(Component.literal("Pets: \"e\""), 1, menu()))
	}

	@Test
	fun `only player heads in the seven pet columns are retained`() {
		val stacks = menu()
		stacks[10] = pet("Rabbit")
		stacks[16] = pet("Bee")
		stacks[17] = pet("Tiger")
		stacks[18] = pet("Lion")
		stacks[19] = ItemStack(Items.STONE)
		stacks[43] = pet("Dragon")

		cache.refresh(Component.literal("Pets"), 1, stacks)

		assertEquals(3, cache.size)
		assertEquals(10, cache.slotAt(0))
		assertEquals(16, cache.slotAt(1))
		assertEquals(43, cache.slotAt(2))
	}

	@Test
	fun `a short container is scanned only through its available slots`() {
		val stacks = MutableList(12) { ItemStack.EMPTY }
		stacks[10] = pet("Rabbit")

		cache.refresh(Component.literal("Pets"), 1, stacks)

		assertEquals(1, cache.size)
		assertEquals(10, cache.slotAt(0))
	}

	@Test
	fun `the favourites view retains only names beginning with the star marker`() {
		val stacks = menuWithPets(12)
		stacks[10] = pet("§e⭐ Rabbit")
		stacks[11] = pet("Tiger ⭐")
		stacks[12] = pet("⭐ Dragon")

		cache.refresh(Component.literal("Pets"), 1, stacks)
		cache.showFavouritesOnly(true)

		assertEquals(2, cache.size)
		assertEquals(10, cache.slotAt(0))
		assertEquals(12, cache.slotAt(1))
	}

	@Test
	fun `pages expose nine pets and wrap in both directions`() {
		cache.refresh(Component.literal("Pets"), 1, menuWithPets(20))

		assertEquals(3, cache.pages)
		assertEquals(10, cache.slotAt(0))
		assertEquals(21, cache.movePage(1).let { cache.slotAt(0) })
		assertEquals(32, cache.movePage(1).let { cache.slotAt(0) })
		assertEquals(10, cache.movePage(1).let { cache.slotAt(0) })
		assertEquals(32, cache.movePage(-1).let { cache.slotAt(0) })
	}

	@Test
	fun `same-container updates preserve a valid page and clamp one that emptied`() {
		cache.refresh(Component.literal("Pets"), 1, menuWithPets(20))
		cache.movePage(2)

		cache.refresh(Component.literal("Pets"), 1, menuWithPets(19))
		assertEquals(2, cache.page)

		cache.refresh(Component.literal("Pets"), 1, menuWithPets(9))
		assertEquals(0, cache.page)
	}

	@Test
	fun `switching to a smaller favourites view clamps the page`() {
		val stacks = menuWithPets(20)
		stacks[10] = pet("⭐ Rabbit")
		cache.refresh(Component.literal("Pets"), 1, stacks)
		cache.movePage(2)

		cache.showFavouritesOnly(true)

		assertEquals(0, cache.page)
		assertEquals(1, cache.size)
	}

	@Test
	fun `a new Pets container resets the page and vanilla escape hatch`() {
		cache.refresh(Component.literal("Pets"), 1, menuWithPets(20))
		cache.movePage(1)
		cache.showVanilla()

		cache.refresh(Component.literal("Pets"), 2, menuWithPets(20))

		assertEquals(0, cache.page)
		assertTrue(cache.custom)
	}

	@Test
	fun `vanilla escape hatch lasts through updates to the current open`() {
		cache.refresh(Component.literal("Pets"), 1, menuWithPets(2))
		assertTrue(cache.showVanilla())

		cache.refresh(Component.literal("Pets"), 1, menuWithPets(3))

		assertTrue(cache.vanilla)
		assertFalse(cache.custom)
	}

	@Test
	fun `only the matching close tears down the cache`() {
		cache.refresh(Component.literal("Pets"), 3, menuWithPets(2))

		assertFalse(cache.close(2))
		assertTrue(cache.active)
		assertTrue(cache.close(3))
		assertFalse(cache.active)
		assertFalse(cache.showVanilla())
		assertEquals(PetWheelCache.NO_SLOT, cache.slotAt(0))
	}

	private fun menu(): MutableList<ItemStack> = MutableList(MENU_SIZE) { ItemStack.EMPTY }

	private fun menuWithPets(count: Int): MutableList<ItemStack> {
		val stacks = menu()
		var remaining = count
		var index = 10
		while (remaining > 0 && index <= 43) {
			if (index % 9 in 1..7) {
				stacks[index] = pet("Pet $remaining")
				remaining--
			}
			index++
		}
		return stacks
	}

	private fun pet(name: String): ItemStack = ItemStack(Items.PLAYER_HEAD).also {
		it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
	}

	private companion object {
		const val MENU_SIZE = 54

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
