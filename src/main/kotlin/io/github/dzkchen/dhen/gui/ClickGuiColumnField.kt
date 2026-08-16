package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntSupplier
import java.util.function.IntUnaryOperator

internal class ClickGuiColumnField(
	private val view: ClickGuiState,
	private val viewportWidth: IntSupplier,
	private val viewportHeight: IntSupplier
) {
	private val columns = mutableListOf<ClickGuiColumn>()
	private val visible = mutableListOf<ClickGuiColumn>()
	private val expanded = mutableSetOf<String>()
	private val glyphs = ColumnGlyphs()
	private val tooltip = ClickGuiTooltip()
	private val fieldBottom = IntSupplier { fieldBottomOf(viewportHeight.asInt) }
	private val stack = ScrollingStack(MARGIN, COLUMN_GAP, MARGIN, { visible.size }, viewportWidth, { COLUMN_WIDTH })
	private val navRowsAt = IntUnaryOperator { index -> visible[index].navCount }

	var focus: Module? = null
		private set

	val isEmpty: Boolean
		get() = visible.isEmpty()

	fun build(categories: List<Category>, byCategory: Map<Category, List<Module>>) {
		if (columns.isNotEmpty()) return
		for (i in categories.indices) {
			val category = categories[i]
			val modules = byCategory[category] ?: emptyList()
			columns += ClickGuiColumn(category, modules, view, expanded, glyphs, tooltip, fieldBottom)
		}
	}

	fun measure(font: Font) = glyphs.measure(font)

	fun invalidateMeasurements(font: Font) {
		glyphs.measure(font)
		for (i in columns.indices) columns[i].invalidateMeasurements()
	}

	fun filter(query: String) {
		for (i in columns.indices) columns[i].applyFilter(query)
	}

	fun resync() {
		for (i in columns.indices) columns[i].resync()
	}

	fun refilter() {
		layout()
		stack.refilter()
		refreshFocus()
		revealFocus()
	}

	fun relayout() {
		layout()
		stack.reclamp()
	}

	fun reflowAll() {
		for (i in visible.indices) visible[i].reclamp()
	}

	fun collapseExpandedSettings(): Boolean {
		var changed = false
		for (i in columns.indices) {
			if (columns[i].collapseSettings()) changed = true
		}
		return changed
	}

	fun columnAt(slot: Int): ClickGuiColumn = visible[slot]

	fun slotAt(x: Int, y: Int): Int {
		if (y !in FIELD_TOP..<fieldBottom.asInt) return ClickGuiShell.NONE
		val slot = stack.slotAt(x)
		if (slot == ClickGuiShell.NONE) return ClickGuiShell.NONE
		return if (y < FIELD_TOP + visible[slot].height) slot else ClickGuiShell.NONE
	}

	fun onChevron(column: ClickGuiColumn, x: Int): Boolean = column.chevronContains(x + stack.offset)

	fun toggleCollapsed(column: ClickGuiColumn) {
		column.toggleCollapsed()
		refreshFocus()
	}

	fun scrollBy(x: Int, y: Int, delta: Int): Boolean {
		val slot = slotAt(x, y)
		val overBody = slot != ClickGuiShell.NONE && y >= FIELD_TOP + HEADER_HEIGHT
		return (overBody && visible[slot].scrollBy(delta)) || stack.scrollBy(delta)
	}

	fun controlAt(hit: ControlHit, x: Int, y: Int): SettingControl? {
		val slot = slotAt(x, y)
		if (slot == ClickGuiShell.NONE) return null
		return controlAt(hit, visible[slot], x, y)
	}

	fun controlAt(hit: ControlHit, column: ClickGuiColumn, x: Int, y: Int): SettingControl? {
		val left = column.contentLeft + CONTENT_PAD - stack.offset
		if (x < left || x >= left + CONTROLS_WIDTH) return null
		val row = column.settingsRowAt(y)
		if (row == ClickGuiShell.NONE) return null
		return column.bodyOf(row).hit(hit, column, left, column.bodyTop(row), CONTROLS_WIDTH, y)
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		tooltip.clear()
		val width = viewportWidth.asInt
		for (i in visible.indices) {
			val column = visible[i]
			val left = column.contentLeft - stack.offset
			if (left + COLUMN_WIDTH > 0 && left < width) column.draw(graphics, font, left, mouseX, mouseY, focus)
		}
		tooltip.draw(graphics, font, width, viewportHeight.asInt)
	}

	fun focusOn(module: Module) {
		focus = module
	}

	fun moveFocus(delta: Int): Boolean {
		val flat = ClickGuiNav.step(focusFlat(), delta, ClickGuiNav.total(visible.size, navRowsAt))
		val slot = ClickGuiNav.columnOf(flat, visible.size, navRowsAt)
		if (slot == ClickGuiShell.NONE) focus = null
		else focusAt(slot, flat - ClickGuiNav.flatOf(slot, 0, navRowsAt))
		return true
	}

	fun jumpColumn(delta: Int): Boolean {
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return moveFocus(delta)
		val next = ClickGuiNav.columnStep(slot, delta, visible.size, navRowsAt)
		if (next == ClickGuiShell.NONE) return true
		focusAt(next, minOf(visible[slot].rowOf(focus), visible[next].navCount - 1))
		return true
	}

	fun toggleFocusedSettings(): Boolean {
		val module = focus ?: return false
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return false
		val column = visible[slot]
		val row = column.rowOf(module)
		column.toggleSettings(module)
		revealFocus(slot, row)
		return true
	}

	private fun layout() {
		visible.clear()
		var left = MARGIN
		for (i in columns.indices) {
			val column = columns[i]
			if (column.hidden) continue
			column.contentLeft = left
			left += COLUMN_WIDTH + COLUMN_GAP
			visible += column
		}
	}

	private fun focusAt(slot: Int, row: Int) {
		focus = visible[slot].moduleAt(row)
		revealFocus(slot, row)
	}

	private fun refreshFocus() {
		if (focus == null || focusSlot() != ClickGuiShell.NONE) return
		val slot = ClickGuiNav.columnOf(0, visible.size, navRowsAt)
		if (slot == ClickGuiShell.NONE) focus = null else focusAt(slot, 0)
	}

	private fun focusSlot(): Int {
		val module = focus ?: return ClickGuiShell.NONE
		for (i in visible.indices) {
			if (visible[i].rowOf(module) != ClickGuiShell.NONE) return i
		}
		return ClickGuiShell.NONE
	}

	private fun focusFlat(): Int {
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return ClickGuiShell.NONE
		return ClickGuiNav.flatOf(slot, visible[slot].rowOf(focus), navRowsAt)
	}

	private fun revealFocus(slot: Int, row: Int) {
		stack.reveal(slot)
		visible[slot].revealRow(row)
	}

	private fun revealFocus() {
		val slot = focusSlot()
		if (slot != ClickGuiShell.NONE) revealFocus(slot, visible[slot].rowOf(focus))
	}
}
