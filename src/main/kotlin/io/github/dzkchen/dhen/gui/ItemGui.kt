package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

internal object ItemGui {
	fun stack(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
		graphics.item(stack, x, y)
	}

	fun decorated(graphics: GuiGraphicsExtractor, font: Font, stack: ItemStack, x: Int, y: Int) {
		graphics.item(stack, x, y)
		graphics.itemDecorations(font, stack, x, y)
	}

	fun slot(
		graphics: GuiGraphicsExtractor,
		font: Font,
		stack: ItemStack,
		left: Int,
		top: Int,
		cell: Int,
		backdrop: Int
	) {
		SharpGui.fill(graphics, left, top, left + cell, top + cell, backdrop)
		val inset = (cell - SLOT_BOX) / 2
		decorated(graphics, font, stack, left + inset, top + inset)
	}
}
