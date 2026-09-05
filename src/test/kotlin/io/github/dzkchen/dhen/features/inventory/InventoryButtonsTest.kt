package io.github.dzkchen.dhen.features.inventory

import net.minecraft.world.inventory.Slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FixedContainer : ContainerOrigin {
	override fun dhenContainerLeft(): Int = LEFT

	override fun dhenContainerTop(): Int = TOP

	override fun dhenContainerWidth(): Int = WIDTH

	override fun dhenContainerHeight(): Int = HEIGHT

	override fun dhenHoveredSlot(): Slot? = null
}

private const val LEFT = 100
private const val TOP = 50
private const val WIDTH = 176
private const val HEIGHT = 222

class InventoryButtonsTest {
	@Test
	fun `a button survives being written down and read back`() {
		val edited = InvButton(0)
		edited.item = "minecraft:stone"
		edited.command = "warp hub"
		edited.title = "Your Skills"
		edited.tooltip = "Skills"
		edited.disabled = true

		val restored = InvButton(0)
		restored.read(edited.row())

		assertEquals("minecraft:stone", restored.item)
		assertEquals("warp hub", restored.command)
		assertEquals("Your Skills", restored.title)
		assertEquals("Skills", restored.tooltip)
		assertTrue(restored.disabled)
	}

	@Test
	fun `a row with the wrong number of fields leaves the button on its default`() {
		val button = InvButton(0)

		button.read("minecraft:stone")

		assertEquals("minecraft:diamond_sword", button.item)
		assertEquals("Skills", button.command)
		assertFalse(button.disabled)
	}

	@Test
	fun `a screen title that is not a pattern is reported rather than silently kept`() {
		val button = InvButton(0)
		assertTrue(button.matches("Your Skills"))

		button.title = "(unclosed"

		assertFalse(button.patternValid)
		assertFalse(button.matches("Your Skills"))
		assertFalse(button.matches("(unclosed"))
	}

	@Test
	fun `reset puts a button back on the default shipped for its own slot`() {
		val button = InvButton(13)
		button.item = "minecraft:stone"
		button.command = "warp hub"

		button.reset()

		assertEquals("minecraft:crafting_table", button.item)
		assertEquals("CraftingTable", button.command)
		assertTrue(button.matches("Craft Item"))
	}

	@Test
	fun `a warp button never reads as the screen you are on`() {
		val island = InvButton(7)

		assertFalse(island.matches("Island"))
		assertFalse(island.matches(""))
		assertEquals("warp island", island.command)
	}

	@Test
	fun `the wardrobe and pet titles match the pages Hypixel numbers`() {
		assertTrue(InvButton(2).matches("Pets"))
		assertTrue(InvButton(2).matches("(1/2) Pets"))
		assertTrue(InvButton(3).matches("Wardrobe (2/3)"))
		assertTrue(InvButton(3).matches("(2/3) Armor Sets"))
		assertTrue(InvButton(12).matches("Co-op Auction House"))
		assertFalse(InvButton(12).matches("Auction View"))
	}

	@Test
	fun `the two rows sit above and below the container and never over its slots`() {
		val container = FixedContainer()
		val none = InventoryButtons.NONE

		assertEquals(0, InventoryButtons.hoveredIndex(container, LEFT + 1, TOP - 20, none))
		assertEquals(6, InventoryButtons.hoveredIndex(container, LEFT + 6 * 25 + 10, TOP - 20, none))
		assertEquals(7, InventoryButtons.hoveredIndex(container, LEFT + 1, TOP + HEIGHT + 10, none))
		assertEquals(13, InventoryButtons.hoveredIndex(container, LEFT + 6 * 25 + 10, TOP + HEIGHT + 10, none))
		assertEquals(none, InventoryButtons.hoveredIndex(container, LEFT + 1, TOP + 100, none))
		assertEquals(none, InventoryButtons.hoveredIndex(container, LEFT - 20, TOP - 20, none))
		assertEquals(none, InventoryButtons.hoveredIndex(container, LEFT + 1, TOP + 5, none))
	}

	@Test
	fun `the button standing out for the open menu can be clicked on the edge it stands out by`() {
		val container = FixedContainer()
		val none = InventoryButtons.NONE
		val topEdge = TOP - 32 + 8 - 4

		assertEquals(none, InventoryButtons.hoveredIndex(container, LEFT + 1, topEdge, none))
		assertEquals(0, InventoryButtons.hoveredIndex(container, LEFT + 1, topEdge, 0))

		val bottomEdge = TOP + HEIGHT - 9 + 4 + 32 - 1

		assertEquals(none, InventoryButtons.hoveredIndex(container, LEFT + 1, bottomEdge, none))
		assertEquals(7, InventoryButtons.hoveredIndex(container, LEFT + 1, bottomEdge, 7))
	}
}
