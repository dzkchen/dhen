package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal class ArcPreviewScreen(private val parent: Screen?) : LiveWorldScreen(Component.literal("Arc preview")) {
	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val centerX = width * 0.5f
		val centerY = height * 0.5f
		val spacing = minOf(90f, width * 0.24f)
		ArcGui.annularSegment(graphics, centerX - spacing, centerY, 18f, 42f, START, QUARTER, DhenPalette.accent)
		ArcGui.annularSegment(graphics, centerX, centerY, 18f, 42f, START, HALF, DhenPalette.accentMuted)
		ArcGui.annularSegment(graphics, centerX + spacing, centerY, 28f, 52f, START, THREE_QUARTERS, DhenPalette.TEXT_PRIMARY)
	}

	override fun onClose() {
		val previous = parent
		if (previous == null) super.onClose() else minecraft.gui.setScreen(previous)
	}

	private companion object {
		const val START = -ArcGui.TAU / 4f
		const val QUARTER = ArcGui.TAU / 4f
		const val HALF = ArcGui.TAU / 2f
		const val THREE_QUARTERS = ArcGui.TAU * 0.75f
	}
}
