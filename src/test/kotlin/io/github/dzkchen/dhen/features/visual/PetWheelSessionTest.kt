package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PetWheelSessionTest {
	private val session = PetWheelSession()

	@Test
	fun `ready paging and pickup form one container action flow`() {
		assertTrue(session.refresh(Component.literal("Pets"), 7, menuWithPets(12)))
		assertEquals(9, session.visibleCount)
		session.cache.movePage(1)
		assertEquals(3, session.visibleCount)

		assertTrue(session.accept(2, quickMove = false, now = 1_000L))

		assertEquals(23, session.actionSlot)
		assertEquals(ContainerInput.PICKUP, session.actionInput)
		assertTrue(session.closesAfterAction)
	}

	@Test
	fun `quick move shares the debounce and keeps the container open`() {
		session.refresh(Component.literal("Pets"), 4, menuWithPets(3))

		assertTrue(session.accept(0, quickMove = true, now = 2_000L))
		assertFalse(session.accept(1, quickMove = false, now = 2_299L))
		assertTrue(session.accept(1, quickMove = true, now = 2_300L))

		assertEquals(11, session.actionSlot)
		assertEquals(ContainerInput.QUICK_MOVE, session.actionInput)
		assertFalse(session.closesAfterAction)
	}

	@Test
	fun `a new window resets action timing and a close clears the pending action`() {
		session.refresh(Component.literal("Pets"), 1, menuWithPets(2))
		assertTrue(session.accept(0, quickMove = false, now = 5_000L))

		session.refresh(Component.literal("Pets"), 2, menuWithPets(2))
		assertTrue(session.accept(0, quickMove = false, now = 5_001L))
		assertTrue(session.close(2))
		assertEquals(PetWheelCache.NO_SLOT, session.actionSlot)
	}

	@Test
	fun `empty visible positions never create an action`() {
		session.refresh(Component.literal("Pets"), 3, menuWithPets(1))

		assertFalse(session.accept(1, quickMove = false, now = 1_000L))
		assertEquals(PetWheelCache.NO_SLOT, session.actionSlot)
	}

	@Test
	fun `a live action target must still be the displayed pet stack`() {
		val displayed = pet("Rabbit")

		assertTrue(samePetStack(displayed, displayed.copy()))
		assertFalse(samePetStack(displayed, pet("Tiger")))
		assertFalse(samePetStack(displayed, ItemStack(Items.STONE)))
	}

	private fun menuWithPets(count: Int): MutableList<ItemStack> {
		val stacks = MutableList(MENU_SIZE) { ItemStack.EMPTY }
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
