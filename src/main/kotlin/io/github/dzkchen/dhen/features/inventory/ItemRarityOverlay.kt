package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.gui.slotOutline
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack

object ItemRarityOverlay : Module(
	name = "Item Rarity",
	category = Category.INVENTORY,
	description = "Tints inventory and hotbar slots with the rarity of the item sitting in them."
) {
	internal val drawOnHotbarSetting = BooleanSetting(
		"Draw on Hotbar",
		default = true,
		description = "Also tints the nine hotbar slots, not just open containers."
	)

	internal val opacitySetting = NumberSetting(
		"Rarity Opacity",
		DEFAULT_OPACITY,
		MIN_OPACITY,
		MAX_OPACITY,
		description = "How strong the tint is, as a percentage."
	)

	internal val styleSetting = SelectorSetting(
		"Rarity Style",
		FILLED,
		listOf(FILLED, OUTLINE, FILLED_OUTLINE, CIRCLE),
		description = "The shape the tint is painted in."
	)

	internal val colorStyleSetting = SelectorSetting(
		"Color Style",
		DEFAULT_COLORS,
		listOf(DEFAULT_COLORS, HYPIXEL_COLORS),
		description = "Default follows the rarity's chat colour; Hypixel follows the brighter menu palette."
	)

	init {
		for (setting in listOf(drawOnHotbarSetting, opacitySetting, styleSetting, colorStyleSetting)) {
			registerSetting(setting)
		}

		on<SlotRenderEvent.Pre> { event ->
			if (InventorySearch.matches(event.slot.index, event.slot.item)) return@on
			draw(event.graphics, event.slot.item, event.slot.x, event.slot.y)
		}
	}

	internal fun drawHotbarSlot(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
		if (!enabled || !drawOnHotbarSetting.on) return
		try {
			draw(graphics, stack, x, y)
		} catch (throwable: Throwable) {
			reportError(throwable)
		}
	}

	internal fun tint(rarity: ItemRarity): Int =
		if (colorStyleSetting.value == HYPIXEL_COLORS) hypixelTint(rarity) else legacyTint(rarity)

	private fun draw(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
		if (!SkyBlockLocation.inSkyBlock || stack.isEmpty) return
		val rarity = SkyBlockItems.rarity(stack)
		if (rarity == ItemRarity.NONE) return
		val solid = tint(rarity)
		val faded = ARGB.color((opacitySetting.amount * ALPHA_FULL / PERCENT).toInt(), solid)
		when (styleSetting.value) {
			OUTLINE -> slotOutline(graphics, x, y, faded)
			FILLED_OUTLINE -> {
				SlotTint.claim(faded, TINT_PRIORITY)
				slotOutline(graphics, x, y, ARGB.opaque(solid))
			}

			CIRCLE -> RoundedGui.circle(graphics, x + SLOT_BOX / 2, y + SLOT_BOX / 2, SLOT_BOX / 2, faded)
			else -> SlotTint.claim(faded, TINT_PRIORITY)
		}
	}

	private fun hypixelTint(rarity: ItemRarity): Int = when (rarity) {
		ItemRarity.COMMON -> DhenPalette.HYPIXEL_COMMON
		ItemRarity.UNCOMMON -> DhenPalette.HYPIXEL_UNCOMMON
		ItemRarity.RARE -> DhenPalette.HYPIXEL_RARE
		ItemRarity.EPIC -> DhenPalette.HYPIXEL_EPIC
		ItemRarity.LEGENDARY -> DhenPalette.HYPIXEL_LEGENDARY
		ItemRarity.MYTHIC -> DhenPalette.HYPIXEL_MYTHIC
		ItemRarity.DIVINE, ItemRarity.SUPREME -> DhenPalette.HYPIXEL_DIVINE
		ItemRarity.ULTIMATE, ItemRarity.VERY_SPECIAL -> DhenPalette.HYPIXEL_ULTIMATE
		ItemRarity.SPECIAL -> DhenPalette.HYPIXEL_SPECIAL
		ItemRarity.NONE -> legacyTint(rarity)
	}

	private fun legacyTint(rarity: ItemRarity): Int = legacyColor(rarity.baseColor)

	internal const val FILLED = "Filled"
	internal const val OUTLINE = "Outline"
	internal const val FILLED_OUTLINE = "Filled Outline"
	internal const val CIRCLE = "Circle"
	internal const val DEFAULT_COLORS = "Default"
	internal const val HYPIXEL_COLORS = "Hypixel"

	private const val DEFAULT_OPACITY = 30.0
	private const val MIN_OPACITY = 10.0
	private const val MAX_OPACITY = 100.0
	private const val PERCENT = 100.0
	private const val ALPHA_FULL = 255.0
	private const val TINT_PRIORITY = 0
}
