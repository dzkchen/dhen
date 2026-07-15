package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal class FlatPrimitivesDemoScreen : Screen(Component.literal("Dhen flat primitives")) {
	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		FlatGui.fill(graphics, 0, 0, width, height, DhenPalette.CANVAS)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		if (width <= OUTER_MARGIN * 2 || height <= OUTER_MARGIN * 2) return

		val panelWidth = minOf(PANEL_WIDTH, width - OUTER_MARGIN * 2)
		val panelHeight = minOf(PANEL_HEIGHT, height - OUTER_MARGIN * 2)
		val left = (width - panelWidth) / 2
		val top = (height - panelHeight) / 2
		val right = left + panelWidth
		val bottom = top + panelHeight

		FlatGui.roundedFill(graphics, left, top, right, bottom, PANEL_RADIUS, DhenPalette.SURFACE)
		FlatGui.fill(graphics, left, top + PANEL_RADIUS, left + ACCENT_WIDTH, bottom - PANEL_RADIUS, DhenPalette.ACCENT)
		FlatGui.text(graphics, font, "Dhen", left + CONTENT_LEFT, top + 18, DhenPalette.TEXT_PRIMARY)
		FlatGui.text(graphics, font, "Flat interface foundation", left + CONTENT_LEFT, top + 33, DhenPalette.TEXT_SECONDARY)

		val specimenTop = top + 62
		FlatGui.text(graphics, font, "SURFACE HIERARCHY", left + CONTENT_LEFT, specimenTop, DhenPalette.TEXT_DISABLED)
		val swatchTop = specimenTop + 16
		val swatchWidth = (panelWidth - CONTENT_LEFT - CONTENT_RIGHT - SWATCH_GAP * 2) / 3
		val firstSwatchLeft = left + CONTENT_LEFT
		val secondSwatchLeft = firstSwatchLeft + swatchWidth + SWATCH_GAP
		val thirdSwatchLeft = secondSwatchLeft + swatchWidth + SWATCH_GAP
		FlatGui.roundedFill(
			graphics,
			firstSwatchLeft,
			swatchTop,
			firstSwatchLeft + swatchWidth,
			swatchTop + SWATCH_HEIGHT,
			SWATCH_RADIUS,
			DhenPalette.SURFACE_RAISED
		)
		FlatGui.roundedFill(
			graphics,
			secondSwatchLeft,
			swatchTop,
			secondSwatchLeft + swatchWidth,
			swatchTop + SWATCH_HEIGHT,
			SWATCH_RADIUS,
			DhenPalette.SURFACE_INTERACTIVE
		)
		FlatGui.roundedFill(
			graphics,
			thirdSwatchLeft,
			swatchTop,
			thirdSwatchLeft + swatchWidth,
			swatchTop + SWATCH_HEIGHT,
			SWATCH_RADIUS,
			DhenPalette.ACCENT
		)

		val textTop = bottom - 45
		FlatGui.text(graphics, font, "Primary", left + CONTENT_LEFT, textTop, DhenPalette.TEXT_PRIMARY)
		FlatGui.text(graphics, font, "Secondary", left + CONTENT_LEFT + 70, textTop, DhenPalette.TEXT_SECONDARY)
		FlatGui.text(graphics, font, "Disabled", left + CONTENT_LEFT + 150, textTop, DhenPalette.TEXT_DISABLED)
		FlatGui.text(graphics, font, "Accent", thirdSwatchLeft + 10, swatchTop + 13, DhenPalette.TEXT_ON_ACCENT)
		FlatGui.fill(graphics, left + CONTENT_LEFT, bottom - 22, right - CONTENT_RIGHT, bottom - 21, DhenPalette.BORDER)
		FlatGui.text(graphics, font, "Right Shift opens this flat tier", left + CONTENT_LEFT, bottom - 15, DhenPalette.TEXT_SECONDARY)
	}

	private companion object {
		const val PANEL_WIDTH = 360
		const val PANEL_HEIGHT = 182
		const val PANEL_RADIUS = 4
		const val OUTER_MARGIN = 12
		const val ACCENT_WIDTH = 3
		const val CONTENT_LEFT = 22
		const val CONTENT_RIGHT = 18
		const val SWATCH_GAP = 8
		const val SWATCH_HEIGHT = 34
		const val SWATCH_RADIUS = 3
	}
}
