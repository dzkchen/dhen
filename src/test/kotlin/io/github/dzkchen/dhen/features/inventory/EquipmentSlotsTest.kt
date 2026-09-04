package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class EquipmentSlotsTest {
	private fun named(item: net.minecraft.world.item.Item, name: String): ItemStack =
		ItemStack(item).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	private fun menu(build: MutableList<ItemStack>.() -> Unit): List<ItemStack> =
		MutableList(45) { ItemStack.EMPTY }.apply(build)

	@Test
	fun `an unfilled equipment slot reads as empty however Hypixel words it`() {
		assertTrue(EquipmentSlots.placeholder("Empty Necklace Slot"))
		assertTrue(EquipmentSlots.placeholder("§7Slot 3"))
		assertTrue(EquipmentSlots.placeholder("  empty cloak  "))
		assertFalse(EquipmentSlots.placeholder("Bat Person Necklace"))
		assertFalse(EquipmentSlots.placeholder("Slotted Belt"))
	}

	@Test
	fun `the column skips the filler panes and keeps the pieces in order`() {
		val stacks = menu {
			for (index in indices) this[index] = ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK))
			this[1] = named(Items.DIAMOND, "Bat Person Necklace")
			this[10] = named(Items.DIAMOND, "Empty Cloak Slot")
			this[19] = named(Items.DIAMOND, "Crystal Belt")
			this[28] = named(Items.DIAMOND, "Slot 4")
			this[13] = named(Items.DIAMOND, "Something In Another Column")
		}
		val pieces = EquipmentSlots.column(stacks, 1)

		assertEquals(4, pieces.size)
		assertEquals("Bat Person Necklace", pieces[0].hoverName.string)
		assertTrue(pieces[1].isEmpty)
		assertEquals("Crystal Belt", pieces[2].hoverName.string)
		assertTrue(pieces[3].isEmpty)
	}

	@Test
	fun `the wardrobe reads the column the lime dye marks and gives up without one`() {
		val selected = menu { this[39] = ItemStack(Items.DYE.pick(DyeColor.LIME)) }
		assertEquals(3, EquipmentSlots.wardrobeColumn(selected))
		assertEquals(-1, EquipmentSlots.wardrobeColumn(menu {}))
		assertEquals(-1, EquipmentSlots.wardrobeColumn(menu { this[3] = ItemStack(Items.DYE.pick(DyeColor.LIME)) }))
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun boot() = ItemFixture.bootstrap()
	}
}
