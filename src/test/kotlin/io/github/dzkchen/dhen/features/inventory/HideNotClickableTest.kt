package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HideNotClickableTest {
	@BeforeEach
	fun reset() = HideNotClickable.opened("Bazaar", emptyList())

	@Test
	fun `the module carries the six reference controls on the inventory card`() {
		assertEquals(Category.INVENTORY, HideNotClickable.category)
		assertEquals(
			listOf(
				"Protect Rarely Sold Items",
				"Block Clicks",
				"Transparency",
				"Bypass With Key",
				"Green Line",
				"Configure Blocked Slots"
			),
			HideNotClickable.settings.map { it.name }
		)
	}

	@Test
	fun `a menu nothing knows about hides nothing`() {
		HideNotClickable.opened("Your Island", emptyList())
		assertEquals("", reason(ItemFixture.identified("HYPERION")))
	}

	@Test
	fun `the trade menu answers before the auction rules do`() {
		HideNotClickable.opened("You    ", emptyList())
		assertEquals("Sacks cannot be traded!", reason(sack()))
		assertEquals("Soulbound items cannot be traded!", reason(lored("* Soulbound *")))
	}

	@Test
	fun `the storage rule answers before the salvage rule and both before the bags`() {
		HideNotClickable.opened("Ender Chest (1/9)", emptyList())
		assertEquals("Bags cannot be put into the storage!", reason(named("BASKET_OF_SEEDS", "Basket of Seeds")))
		HideNotClickable.opened("Salvage Items", emptyList())
		assertEquals("This item should not be salvaged! (Legendary)", reason(lored("LEGENDARY DUNGEON")))
	}

	@Test
	fun `the salvage list is matched on the end of the name`() {
		HideNotClickable.opened("Salvage Item", emptyList())
		assertEquals("", reason(named("BOUNCY_HELMET", "Ancient Bouncy Helmet")))
		assertEquals("This item cannot be salvaged!", reason(named("HYPERION", "Hyperion")))
	}

	@Test
	fun `every bag names the SkyBlock menu before it names its own rule`() {
		val menu = ItemFixture.identified("SKYBLOCK_MENU")
		HideNotClickable.opened("Potion Bag", emptyList())
		assertEquals("The SkyBlock Menu cannot be put into the potion bag!", reason(menu))
		HideNotClickable.opened("Fishing Bag", emptyList())
		assertEquals("The SkyBlock Menu cannot be put into the fishing bag!", reason(menu))
		HideNotClickable.opened("Sack of Sacks", emptyList())
		assertEquals("", reason(menu))
	}

	@Test
	fun `the potion bag takes potions and water bottles and nothing else`() {
		HideNotClickable.opened("Potion Bag", emptyList())
		assertEquals("", reason(named("POTION", "Speed Potion")))
		assertEquals("", reason(named("WATER_BOTTLE", "Water Bottle")))
		assertEquals("This item is not a potion!", reason(named("HYPERION", "Hyperion")))
	}

	@Test
	fun `the seed basket reads the SkyBlock id and not the name`() {
		HideNotClickable.opened("Basket of Seeds", emptyList())
		assertEquals("", reason(ItemFixture.identified("INK_SACK-3")))
		assertEquals("This item is not a seed!", reason(ItemFixture.identified("WHEAT")))
	}

	@Test
	fun `the equipment menu keeps bold-lore wearables and blocks everything else`() {
		HideNotClickable.opened("Stats & Equipment", emptyList())
		assertEquals("", reason(wearable()))
		assertEquals("This item cannot be put into your equipment!", reason(named("HYPERION", "Hyperion")))
		assertEquals(
			"The SkyBlock Menu cannot be put into your equipment!",
			reason(ItemFixture.identified("SKYBLOCK_MENU"))
		)
	}

	@Test
	fun `an unnamed item in a fossil menu is still not a fossil`() {
		HideNotClickable.opened("Research Center", emptyList())
		assertEquals("", reason(ItemFixture.vanilla()))
		assertEquals("", reason(ItemFixture.identified("HELIX")))
		assertEquals("", reason(ItemFixture.identified("CLAW_FOSSIL")))
		assertEquals("Not a fossil!", reason(ItemFixture.identified("HYPERION")))
	}

	@Test
	fun `a clickable item in a green-line menu is marked and a blocked one is not`() {
		HideNotClickable.opened("Birdfeeder", emptyList())
		assertEquals("", reason(ItemFixture.identified("YOGI_BERRY")))
		assertTrue(HideNotClickable.greenLine)
		assertEquals("Not bird food!", reason(ItemFixture.identified("HYPERION")))
	}

	private fun reason(stack: ItemStack): String = HideNotClickable.reasonFor(SkyBlockItems.of(stack), stack)

	private fun sack(): ItemStack = named("MEDIUM_MINING_SACK", "Medium Mining Sack")

	private fun wearable(): ItemStack = ItemFixture.identified("MOLTEN_NECKLACE").also {
		it.set(
			DataComponents.LORE,
			ItemLore(
				listOf(
					Component.literal("Health").withStyle(ChatFormatting.BOLD),
					Component.literal("EPIC NECKLACE")
				)
			)
		)
	}

	private fun lored(vararg lines: String): ItemStack =
		ItemFixture.identified("HYPERION")
			.also { it.set(DataComponents.LORE, ItemLore(lines.map(Component::literal))) }

	private fun named(id: String, name: String): ItemStack =
		ItemFixture.identified(id).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
