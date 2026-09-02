package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.module.Category
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
	fun `an item with no stat bonus gets no quality line`() {
		assertNull(ItemTooltip.qualityLine(item { putString("id", "HYPERION") }))
		assertNull(ItemTooltip.qualityLine(item { putInt("baseStatBoostPercentage", 0) }))
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
