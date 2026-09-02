package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.SlotTint
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

object HotbarSlot {
	@JvmStatic
	fun tint(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
		SlotTint.begin()
		try {
			ItemRarityOverlay.drawHotbarSlot(graphics, stack, x, y)
			ItemAbilities.tintHotbarSlot(stack)
		} finally {
			SlotTint.end(graphics, x, y)
		}
	}
}
