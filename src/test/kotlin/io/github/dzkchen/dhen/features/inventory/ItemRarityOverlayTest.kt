package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.module.Category
import net.minecraft.network.chat.TextColor
import net.minecraft.util.ARGB
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemRarityOverlayTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in ItemRarityOverlay.settings) setting.reset()
	}

	@Test
	fun `the module declares the four controls the source has`() {
		assertEquals("Item Rarity", ItemRarityOverlay.name)
		assertEquals(Category.INVENTORY, ItemRarityOverlay.category)
		assertEquals(
			listOf("Draw on Hotbar", "Rarity Opacity", "Rarity Style", "Color Style"),
			ItemRarityOverlay.settings.map { it.name }
		)
		assertTrue(ItemRarityOverlay.drawOnHotbarSetting.default)
		assertEquals(30.0, ItemRarityOverlay.opacitySetting.default)
		assertEquals(10.0, ItemRarityOverlay.opacitySetting.min)
		assertEquals(100.0, ItemRarityOverlay.opacitySetting.max)
		assertEquals(
			listOf("Filled", "Outline", "Filled Outline", "Circle"),
			ItemRarityOverlay.styleSetting.options
		)
		assertEquals(listOf("Default", "Hypixel"), ItemRarityOverlay.colorStyleSetting.options)
	}

	@Test
	fun `the default colour style follows the rarity's own chat colour`() {
		for (rarity in ItemRarity.entries) {
			assertEquals(
				ARGB.opaque(TextColor.fromLegacyFormat(rarity.baseColor)!!.value),
				ItemRarityOverlay.tint(rarity)
			)
		}
	}

	@Test
	fun `the Hypixel colour style follows the menu palette`() {
		ItemRarityOverlay.colorStyleSetting.value = "Hypixel"

		assertEquals(DhenPalette.HYPIXEL_COMMON, ItemRarityOverlay.tint(ItemRarity.COMMON))
		assertEquals(DhenPalette.HYPIXEL_LEGENDARY, ItemRarityOverlay.tint(ItemRarity.LEGENDARY))
		assertEquals(ItemRarityOverlay.tint(ItemRarity.DIVINE), ItemRarityOverlay.tint(ItemRarity.SUPREME))
		assertEquals(ItemRarityOverlay.tint(ItemRarity.ULTIMATE), ItemRarityOverlay.tint(ItemRarity.VERY_SPECIAL))
		assertEquals(
			ARGB.opaque(TextColor.fromLegacyFormat(ItemRarity.NONE.baseColor)!!.value),
			ItemRarityOverlay.tint(ItemRarity.NONE)
		)
	}
}
