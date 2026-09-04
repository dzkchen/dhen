package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.textTop
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

internal object BackpackPreview {
	private const val CELL = 18
	private const val COLUMNS = 9
	private const val SIDE_PAD = 8
	private const val HEADER_HEIGHT = 18
	private const val FOOT_PAD = 6
	private const val PANEL_WIDTH = COLUMNS * CELL + SIDE_PAD * 2
	private const val CURSOR_GAP = 8
	private const val CURSOR_LIFT = 16
	private const val PANEL_RADIUS = 4f

	private val nameMemo = DhenType.memo()

	fun draw(graphics: GuiGraphicsExtractor, name: String, items: List<ItemStack>, mouseX: Int, mouseY: Int) {
		if (items.isEmpty()) return
		val rows = Math.ceilDiv(items.size, COLUMNS)
		val height = HEADER_HEIGHT + rows * CELL + FOOT_PAD
		val left = if (mouseX + CURSOR_GAP + PANEL_WIDTH >= graphics.guiWidth()) {
			mouseX - CURSOR_GAP - PANEL_WIDTH
		} else {
			mouseX + CURSOR_GAP
		}
		val top = (mouseY - CURSOR_LIFT).coerceIn(0, maxOf(0, graphics.guiHeight() - height))
		GlassGui.roundedFrame(
			graphics,
			left,
			top,
			left + PANEL_WIDTH,
			top + height,
			PANEL_RADIUS,
			GlassGui.raised(),
			DhenPalette.BORDER
		)
		val font = Minecraft.getInstance().font
		nameMemo.text(graphics, font, name, left + SIDE_PAD, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_PRIMARY)
		for (index in items.indices) {
			val x = left + SIDE_PAD + index % COLUMNS * CELL
			val y = top + HEADER_HEIGHT + index / COLUMNS * CELL
			ItemGui.slot(graphics, font, items[index], x, y, CELL, DhenPalette.SURFACE)
		}
	}
}
