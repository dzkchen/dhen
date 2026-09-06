package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.module.Category
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class ItemTooltipTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in ItemTooltip.settings) setting.reset()
		ItemTooltip.resetScroll()
		ItemTooltip.slotChanged(-1)
	}

	@Test
	fun `the module declares the information and scrolling controls`() {
		assertEquals("Item Tooltip", ItemTooltip.name)
		assertEquals(Category.INVENTORY, ItemTooltip.category)
		assertEquals(
			listOf(
				"Item Prices",
				"NPC Sell Price",
				"Bazaar Spread",
				"Round Prices",
				"Current Amount Price",
				"Full Stack Price",
				"Item Quality",
				"Item Age",
				"Hide Gear Score",
				"Hide Vanilla Enchants",
				"Keep Tooltips On Screen",
				"Scrollable Tooltips",
				"Tooltip Scale",
				"Scroll Speed",
				"Scale Speed"
			),
			ItemTooltip.settings.map { it.name }
		)
		assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, ItemTooltip.stackPriceSetting.default)
		assertEquals(GLFW.GLFW_KEY_LEFT_ALT, ItemTooltip.fullStackPriceSetting.default)
		assertEquals(100.0, ItemTooltip.scaleSetting.default)
		assertEquals(3.0, ItemTooltip.scrollSpeedSetting.default)
		assertEquals(3.0, ItemTooltip.scaleSpeedSetting.default)
	}

	private fun shaped(stack: ItemStack): List<Component> {
		val lines = mutableListOf<Component>(Component.literal("§6Item"))
		ItemTooltip.shape(lines, stack)
		return lines
	}

	@Test
	fun `only a grey vanilla enchant line and the gear score line are stripped`() {
		assertTrue(ItemTooltip.stripped("Gear Score: 1284", gearScore = true, greyEnchant = false))
		assertFalse(ItemTooltip.stripped("Gear Score: 1284", gearScore = false, greyEnchant = false))
		assertTrue(ItemTooltip.vanillaEnchant("Aqua Affinity I"))
		assertTrue(ItemTooltip.vanillaEnchant("Depth Strider III"))
		assertFalse(ItemTooltip.vanillaEnchant("Sharpness VII"))
		assertFalse(ItemTooltip.vanillaEnchant("Ultimate Wise V"))
		assertTrue(ItemTooltip.stripped("Aqua Affinity I", gearScore = false, greyEnchant = true))
		assertFalse(ItemTooltip.stripped("Aqua Affinity I", gearScore = false, greyEnchant = false))
	}

	@Test
	fun `the age line pairs how long ago with the moment it was obtained`() {
		val stamp = 1_700_000_000_000L

		assertTrue(ItemTooltip.ageLine(stamp, stamp + 3_600_000L).startsWith("§7Age: §c1h §8("))
		assertTrue(ItemTooltip.ageLine(stamp, stamp + 1_000L).startsWith("§7Age: §c1s §8("))
	}

	@Test
	fun `an item stamped before Hypixel moved to epoch millis still reports an age`() {
		ItemTooltip.itemAgeSetting.on = true
		val legacy = ItemFixture.stack { putString("timestamp", "12/24/20 11:08 PM") }
		val modern = ItemFixture.stack { putLong("timestamp", 1_700_000_000_000L) }
		val unreadable = ItemFixture.stack { putString("timestamp", "25/04/20 16:38") }

		assertTrue(shaped(legacy)[1].string.startsWith("§7Age: §c"))
		assertTrue(shaped(modern)[1].string.startsWith("§7Age: §c"))
		assertEquals(1, shaped(unreadable).size)
	}

	@Test
	fun `shaping drops the flagged lines and puts the age line second`() {
		ItemTooltip.itemAgeSetting.on = true
		ItemTooltip.hideGearScoreSetting.on = true
		ItemTooltip.hideVanillaEnchantsSetting.on = true
		val stack = ItemFixture.stack { putLong("timestamp", System.currentTimeMillis() - 3_600_000L) }
		val lines = mutableListOf<Component>(
			Component.literal("§6Hyperion"),
			Component.literal("§7Damage: §c+300"),
			Component.literal("§7Gear Score: §d1284"),
			Component.literal("§7Aqua Affinity I"),
			Component.literal("§9Ultimate Wise V")
		)

		ItemTooltip.shape(lines, stack)

		assertEquals(
			listOf("§6Hyperion", null, "§7Damage: §c+300", "§9Ultimate Wise V"),
			lines.mapIndexed { index, line -> if (index == 1) null else line.string }
		)
		assertTrue(lines[1].string.startsWith("§7Age: §c1h §8("))
	}

	@Test
	fun `a dungeon item reports its bonus and the floor it dropped on`() {
		assertEquals(
			"§6Quality Bonus: §b+50% §7(§aF7§7)",
			ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 50); putString("dungeon_skill_req", "CATACOMBS:24"); putInt("item_tier", 7) })
		)
	}

	@Test
	fun `a master mode drop is named by its master floor`() {
		assertEquals(
			"§6Quality Bonus: §a+40% §7(§4M2§7)",
			ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 40); putString("dungeon_skill_req", "CATACOMBS:25"); putInt("item_tier", 5) })
		)
	}

	@Test
	fun `a bonus with no dungeon requirement falls back to the tier alone`() {
		assertEquals(
			"§6Quality Bonus: §c+10% §7(§aE§7)",
			ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 10); putInt("item_tier", 4) })
		)
		assertEquals(
			"§6Quality Bonus: §e+20% §7(§bF0§7)",
			ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 20) })
		)
		assertEquals(
			"§6Quality Bonus: §b+60% §7(§bMASTER_CATACOMBS 3§7)",
			ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 60); putString("dungeon_skill_req", "MASTER_CATACOMBS:10"); putInt("item_tier", 3) })
		)
	}

	@Test
	fun `a price line writes the unit price and, held, the whole stack`() {
		assertEquals("§eBazaar Buy: §61,000", ItemTooltip.priceLine("Bazaar Buy", 1000.0, 1))
		assertEquals("§eBazaar Buy: §664,000 §8(64x 1,000)", ItemTooltip.priceLine("Bazaar Buy", 1000.0, 64))
		assertNull(ItemTooltip.priceLine("Bazaar Buy", 0.0, 1))
	}

	@Test
	fun `rounding shortens the millions the way the second source does`() {
		assertEquals("12,345,678", ItemTooltip.coins(12_345_678.0))

		ItemTooltip.roundPricesSetting.value = true

		assertEquals("12.3M", ItemTooltip.coins(12_345_678.0))
		assertEquals("1.2B", ItemTooltip.coins(1_234_567_890.0))
		assertEquals("999,999", ItemTooltip.coins(999_999.0))
	}

	@Test
	fun `the two price keys multiply the stack the way the second source does`() {
		assertEquals(1, ItemTooltip.multiplier(32, stackHeld = false, fullStackHeld = false))
		assertEquals(64, ItemTooltip.multiplier(32, stackHeld = false, fullStackHeld = true))
		assertEquals(32, ItemTooltip.multiplier(32, stackHeld = true, fullStackHeld = false))
		assertEquals(64, ItemTooltip.multiplier(1, stackHeld = true, fullStackHeld = false))
		assertEquals(2048, ItemTooltip.multiplier(32, stackHeld = true, fullStackHeld = true))
		assertEquals(4096, ItemTooltip.multiplier(1, stackHeld = true, fullStackHeld = true))
	}

	@Test
	fun `scrolling moves the tooltip and the modifiers pick the axis`() {
		ItemTooltip.applyScroll(1.0, shift = false, control = false)

		assertEquals(3f, ItemTooltip.scrollY)
		assertEquals(0f, ItemTooltip.scrollX)

		ItemTooltip.applyScroll(1.0, shift = true, control = false)

		assertEquals(-3f, ItemTooltip.scrollX)
		assertEquals(3f, ItemTooltip.scrollY)
	}

	@Test
	fun `holding control resizes the tooltip inside its clamp`() {
		ItemTooltip.applyScroll(1.0, shift = false, control = true)

		assertEquals(1.03f, ItemTooltip.scale(), TOLERANCE)

		repeat(100) { ItemTooltip.applyScroll(1.0, shift = false, control = true) }

		assertEquals(2.0f, ItemTooltip.scale(), TOLERANCE)

		repeat(200) { ItemTooltip.applyScroll(-1.0, shift = false, control = true) }

		assertEquals(0.3f, ItemTooltip.scale(), TOLERANCE)
	}

	@Test
	fun `moving to another slot puts the tooltip back where it started`() {
		ItemTooltip.slotChanged(4)
		ItemTooltip.applyScroll(2.0, shift = false, control = false)

		assertEquals(6f, ItemTooltip.scrollY)

		ItemTooltip.slotChanged(4)

		assertEquals(6f, ItemTooltip.scrollY)

		ItemTooltip.slotChanged(5)

		assertEquals(0f, ItemTooltip.scrollY)
	}

	private fun item(build: CompoundTag.() -> Unit): SkyBlockItem =
		SkyBlockItem.parse(ItemFixture.customData(build))

	private companion object {
		const val TOLERANCE = 0.0001f

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
