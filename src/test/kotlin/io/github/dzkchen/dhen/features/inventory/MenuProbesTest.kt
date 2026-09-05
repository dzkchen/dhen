package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MenuProbesTest {
	@Test
	fun `the shop probe reads the fourth slot back from the last filled one`() {
		val menu = filler(54)
		menu[54 - 5] = shopSign("§7them to this Shop!")
		assertTrue(npcShopOpen(menu))
	}

	@Test
	fun `the buyback wording opens a shop too`() {
		val menu = filler(54)
		menu[54 - 5] = shopSign("§eClick to buyback!")
		assertTrue(npcShopOpen(menu))
	}

	@Test
	fun `the probe only reads the last lore line and keeps its colour codes`() {
		val menu = filler(54)
		menu[54 - 5] = shopSign("§7them to this Shop!", "§7Some other closing line")
		assertFalse(npcShopOpen(menu))
		menu[54 - 5] = shopSign("them to this Shop!")
		assertFalse(npcShopOpen(menu))
	}

	@Test
	fun `a real lore line carries its colour in the style rather than the text`() {
		val menu = filler(54)
		menu[54 - 5] = styledSign()
		assertTrue(npcShopOpen(menu))
	}

	@Test
	fun `a neighbouring slot carrying the shop wording does not open a shop`() {
		val menu = filler(54)
		menu[54 - 4] = shopSign("§7them to this Shop!")
		assertFalse(npcShopOpen(menu))
	}

	@Test
	fun `trailing empty slots do not shift the probe`() {
		val menu = filler(54)
		for (index in 50 until 54) menu[index] = ItemStack.EMPTY
		menu[50 - 5] = shopSign("§7them to this Shop!")
		assertTrue(npcShopOpen(menu))
	}

	@Test
	fun `an empty menu opens nothing`() {
		assertFalse(npcShopOpen(emptyList()))
		assertFalse(bazaarMenuOpen("Nothing", emptyList()))
	}

	@Test
	fun `the go back button opens the bazaar from either of its two slots`() {
		val near = filler(45)
		near[45 - 5] = button("Go Back", "§7To Bazaar")
		assertTrue(bazaarMenuOpen("Nothing", near))
		val far = filler(45)
		far[45 - 6] = button("Go Back", "§7To Bazaar")
		assertTrue(bazaarMenuOpen("Nothing", far))
	}

	@Test
	fun `the buy order quantity button opens the bazaar from slot sixteen`() {
		val menu = filler(45)
		menu[16] = button("Custom Amount", "§8Buy Order Quantity")
		assertTrue(bazaarMenuOpen("Nothing", menu))
		val moved = filler(45)
		moved[17] = button("Custom Amount", "§8Buy Order Quantity")
		assertFalse(bazaarMenuOpen("Nothing", moved))
	}

	@Test
	fun `bazaar menu titles are matched whole`() {
		assertTrue(bazaarMenuOpen("Bazaar ➜ Farming", filler(45)))
		assertTrue(bazaarMenuOpen("Confirm Buy Order", filler(45)))
		assertTrue(bazaarMenuOpen("Your Bazaar Orders", filler(45)))
		assertFalse(bazaarMenuOpen("Confirm Buy Order Later", filler(45)))
		assertFalse(bazaarMenuOpen("Auction House", filler(45)))
	}

	private fun filler(size: Int): MutableList<ItemStack> =
		MutableList(size) { ItemFixture.identified("BLACK_STAINED_GLASS_PANE") }

	private fun shopSign(vararg lore: String): ItemStack = lored(*lore)

	private fun styledSign(): ItemStack = ItemFixture.identified("SHOP_SIGN").also {
		it.set(
			DataComponents.LORE,
			ItemLore(listOf(Component.literal("them to this Shop!").withStyle(ChatFormatting.GRAY)))
		)
	}

	private fun button(name: String, vararg lore: String): ItemStack =
		lored(*lore).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	private fun lored(vararg lines: String): ItemStack =
		ItemFixture.identified("SHOP_SIGN")
			.also { it.set(DataComponents.LORE, ItemLore(lines.map(Component::literal))) }

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
