package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.mixin.AbstractSignEditScreenAccessor
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen

internal class SupercraftPresets : HudElement("Supercraft Presets", HudAnchor.TOP_LEFT, 100, 100, inMenus = true) {
	private var source: String? = null
	private var values: List<Int> = emptyList()
	private var labels: List<String> = emptyList()
	private var memos = emptyArray<io.github.dzkchen.dhen.gui.TextMemo>()
	private val title = DhenType.memo()
	private var pointerX = 0
	private var pointerY = 0

	fun pointer(x: Int, y: Int) {
		pointerX = x
		pointerY = y
	}

	fun refresh() {
		val text = CraftingHelpers.amounts.value
		if (text == source) return
		source = text
		values = CraftingRules.presets(text)
		labels = values.map(Int::toString)
		memos = Array(values.size) { DhenType.memo() }
	}

	private fun sign(): AbstractSignEditScreenAccessor? {
		if (!CraftingHelpers.presetsEnabled.on) return null
		val screen = Minecraft.getInstance().gui.screen() as? AbstractSignEditScreen ?: return null
		val access = screen as AbstractSignEditScreenAccessor
		return if (CraftingRules.sign(access.dhenMessages())) access else null
	}

	override val hasContent: Boolean get() = editingHud() || sign() != null && values.isNotEmpty()

	override fun width(font: Font): Int = COLUMNS * CELL_WIDTH + PADDING * 2

	override fun height(font: Font): Int = HEADER + ((maxOf(values.size, 1) + COLUMNS - 1) / COLUMNS) * ROW_HEIGHT + PADDING

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		RoundedGui.pill(graphics, 0, 0, width(font), height(font), DhenPalette.SURFACE)
		title.text(graphics, font, "Supercraft Presets", PADDING, PADDING, DhenPalette.TEXT_SECONDARY)
		for (index in values.indices) {
			val x = PADDING + index % COLUMNS * CELL_WIDTH
			val y = HEADER + index / COLUMNS * ROW_HEIGHT
			RoundedGui.pill(graphics, x, y, x + CELL_WIDTH - GAP, y + ROW_HEIGHT - GAP, DhenPalette.SURFACE_RAISED)
			memos[index].text(graphics, font, labels[index], x + PADDING, y + PADDING, DhenPalette.SLOT_GOLD)
		}
	}

	fun clicked(): Boolean {
		val sign = sign() ?: return false
		val client = Minecraft.getInstance()
		val font = client.font
		val width = (width(font) * scale).toInt()
		val height = (height(font) * scale).toInt()
		val x = ((pointerX - placeX(client.window.guiScaledWidth, width)) / scale).toInt() - PADDING
		val y = ((pointerY - placeY(client.window.guiScaledHeight, height)) / scale).toInt() - HEADER
		if (x < 0 || y < 0 || x >= COLUMNS * CELL_WIDTH) return false
		val index = y / ROW_HEIGHT * COLUMNS + x / CELL_WIDTH
		if (index !in values.indices || x % CELL_WIDTH >= CELL_WIDTH - GAP || y % ROW_HEIGHT >= ROW_HEIGHT - GAP) return false
		val cursor = sign.dhenLine()
		try {
			sign.dhenSetLine(0)
			sign.dhenSetMessage(values[index].toString())
		} finally {
			sign.dhenSetLine(cursor)
		}
		return true
	}

	private companion object {
		const val COLUMNS = 4
		const val CELL_WIDTH = 44
		const val ROW_HEIGHT = 22
		const val HEADER = 20
		const val PADDING = 5
		const val GAP = 3
	}
}
