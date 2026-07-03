package io.github.dzkchen.dhen.platform

import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.runtime.DhenRuntime
import io.github.dzkchen.dhen.runtime.LifecycleState
import io.github.dzkchen.dhen.runtime.ModuleFilter
import io.github.dzkchen.dhen.runtime.ModuleRecord
import io.github.dzkchen.dhen.runtime.PanelLayoutState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ClickGuiScreen(private val parent: Screen?, private val runtime: DhenRuntime) : Screen(Component.literal("Dhen")) {
	private inner class Entry(val record: ModuleRecord) {
		var expanded = false
		var controls: List<SettingControl> = emptyList()

		val height: Int
			get() = ROW_HEIGHT + when {
				!expanded -> 0
				controls.isEmpty() -> GuiStyle.LINE
				else -> controls.sumOf { it.height + CONTROL_GAP } + CONTROL_GAP
			}

		fun toggleExpanded() {
			expanded = !expanded
			if (expanded && controls.isEmpty()) {
				controls = record.module.metadata.settings.map { schema ->
					controlFor(
						schema,
						ValueSlot(
							get = { runtime.settingValue(record.addonId, schema.id) },
							set = { value -> runtime.updateSetting(record.addonId, schema.id, value).isEmpty() },
						),
					)
				}
			}
		}
	}

	private inner class Panel(val category: ModuleCategory, val entries: List<Entry>) {
		var x = 0
		var y = 0
		var collapsed = false
		var unknownFields: Map<String, Any?> = emptyMap()
		var filtered: List<Entry> = entries

		val visible: Boolean get() = filtered.isNotEmpty()
		val bodyHeight: Int get() = filtered.sumOf { it.height }

		fun refilter() {
			filtered = matchedIds?.let { ids -> entries.filter { it.record.id in ids } } ?: entries
		}
	}

	private class Chip(val filter: ModuleFilter, val label: String) {
		var x = 0
		var y = 0
		var width = 0
	}

	private enum class Zone { SEARCH, FILTERS, PANELS }

	private val panels = mutableListOf<Panel>()
	private var draggingPanel: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var activeControl: SettingControl? = null

	private var query = ""
	private val filters = linkedSetOf<ModuleFilter>()
	private var matchedIds: Set<ModuleId>? = null
	private val chips = ModuleFilter.entries.map { Chip(it, chipLabel(it)) }

	private var zone = Zone.PANELS
	private var focusedFilter = 0
	private var focusedPanel = 0

	// -1 focuses the panel title bar; 0..n walk module rows and their expanded setting controls.
	private var focusedItem = -1
	private var lastFocusedControl: SettingControl? = null

	override fun init() {
		panels.clear()
		val saved = runtime.panelLayout()
		var cursorX = MARGIN
		var cursorY = TOP_MARGIN
		var rowHeight = 0
		for ((category, modules) in runtime.modulesByCategory()) {
			val panel = Panel(category, modules.map { Entry(it) })
			val state = saved[category.name]
			if (state != null) {
				// Clamp like drag/nudge do: a layout saved on a larger window must stay reachable.
				panel.x = state.x.coerceIn(0, maxOf(0, width - PANEL_WIDTH))
				panel.y = state.y.coerceIn(0, maxOf(0, height - TITLE_HEIGHT))
				panel.collapsed = state.collapsed
				panel.unknownFields = state.unknownFields
			} else {
				if (cursorX + PANEL_WIDTH > width - MARGIN) {
					cursorX = MARGIN
					cursorY += rowHeight + GUTTER
					rowHeight = 0
				}
				panel.x = cursorX
				panel.y = cursorY
				cursorX += PANEL_WIDTH + GUTTER
				rowHeight = maxOf(rowHeight, TITLE_HEIGHT + panel.bodyHeight)
			}
			panels += panel
		}
		focusedPanel = focusedPanel.coerceIn(0, maxOf(0, panels.size - 1))
		refreshSearch()
	}

	override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
		guiGraphics.fill(0, 0, width, height, DIM_BACKGROUND)
		layoutChips()
		for ((index, panel) in panels.withIndex()) {
			if (panel.visible) renderPanel(guiGraphics, panel, index == focusedPanel && zone == Zone.PANELS, mouseX, mouseY)
		}
		renderTopBar(guiGraphics, mouseX, mouseY)
		val hint = describedControl(mouseX, mouseY)?.description?.takeIf { it.isNotEmpty() }
			?: hoveredEntry(mouseX, mouseY)?.record?.module?.metadata?.description?.takeIf { it.isNotEmpty() }
		if (hint != null) guiGraphics.text(font, hint, MARGIN, height - 22, GuiStyle.DIM)
		guiGraphics.text(font, HELP_TEXT, MARGIN, height - 10, GuiStyle.DIM)
	}

	private fun renderTopBar(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		g.fill(SEARCH_X, SEARCH_Y, SEARCH_X + SEARCH_WIDTH, SEARCH_Y + SEARCH_HEIGHT, GuiStyle.FIELD_BG)
		val border = if (zone == Zone.SEARCH) GuiStyle.FOCUS else GuiStyle.BORDER
		drawOutline(g, SEARCH_X, SEARCH_Y, SEARCH_X + SEARCH_WIDTH, SEARCH_Y + SEARCH_HEIGHT, border)
		g.enableScissor(SEARCH_X + 2, SEARCH_Y + 1, SEARCH_X + SEARCH_WIDTH - 2, SEARCH_Y + SEARCH_HEIGHT - 1)
		if (query.isEmpty()) {
			g.text(font, "Search (Ctrl+F)", SEARCH_X + 4, SEARCH_Y + 3, GuiStyle.DIM)
		} else {
			g.text(font, query, SEARCH_X + 4, SEARCH_Y + 3, GuiStyle.TEXT)
		}
		if (zone == Zone.SEARCH) {
			val caretX = SEARCH_X + 4 + font.width(query)
			g.fill(caretX, SEARCH_Y + 2, caretX + 1, SEARCH_Y + SEARCH_HEIGHT - 2, GuiStyle.FOCUS)
		}
		g.disableScissor()

		for ((index, chip) in chips.withIndex()) {
			val active = chip.filter in filters
			g.fill(chip.x, chip.y, chip.x + chip.width, chip.y + CHIP_HEIGHT, if (active) CHIP_ACTIVE else CHIP_BACKGROUND)
			g.text(font, chipText(chip), chip.x + 4, chip.y + 3, if (active) GuiStyle.FOCUS else GuiStyle.TEXT)
			if (zone == Zone.FILTERS && index == focusedFilter) {
				drawOutline(g, chip.x, chip.y, chip.x + chip.width, chip.y + CHIP_HEIGHT, GuiStyle.FOCUS)
			}
		}
	}

	private fun renderPanel(g: GuiGraphicsExtractor, panel: Panel, focused: Boolean, mouseX: Int, mouseY: Int) {
		val right = panel.x + PANEL_WIDTH
		g.fill(panel.x, panel.y, right, panel.y + TITLE_HEIGHT, if (focused) TITLE_FOCUSED else TITLE_BACKGROUND)
		g.text(font, panel.category.displayName, panel.x + PADDING, panel.y + 3, GuiStyle.TEXT)
		val marker = if (panel.collapsed) "[+]" else "[-]"
		g.text(font, marker, right - font.width(marker) - PADDING, panel.y + 3, GuiStyle.TEXT)
		if (focused && focusedItem == -1) drawOutline(g, panel.x, panel.y, right, panel.y + TITLE_HEIGHT, GuiStyle.FOCUS)
		if (panel.collapsed) return

		val focusTarget = if (focused && focusedItem >= 0) focusItems(panel).getOrNull(focusedItem) else null
		val focusedControl = focusTarget as? SettingControl
		val bodyTop = panel.y + TITLE_HEIGHT
		val bodyBottom = bodyTop + panel.bodyHeight
		g.fill(panel.x, bodyTop, right, bodyBottom, BODY_BACKGROUND)
		g.enableScissor(panel.x, bodyTop, right, bodyBottom)
		var rowTop = bodyTop
		for (entry in panel.filtered) {
			renderRow(g, panel, entry, rowTop, focusTarget === entry, mouseX, mouseY)
			if (entry.expanded) {
				g.fill(panel.x, rowTop + ROW_HEIGHT, right, rowTop + entry.height, SETTINGS_BACKGROUND)
				if (entry.controls.isEmpty()) {
					g.text(font, "(no settings)", panel.x + PADDING, rowTop + ROW_HEIGHT + 2, GuiStyle.DIM)
				} else {
					var controlY = rowTop + ROW_HEIGHT + CONTROL_GAP
					for (control in entry.controls) {
						control.layout(panel.x + PADDING, controlY, PANEL_WIDTH - 2 * PADDING)
						control.render(g, font, mouseX, mouseY, focusedControl)
						controlY += control.height + CONTROL_GAP
					}
				}
			}
			rowTop += entry.height
		}
		g.disableScissor()
	}

	private fun renderRow(g: GuiGraphicsExtractor, panel: Panel, entry: Entry, rowTop: Int, focused: Boolean, mouseX: Int, mouseY: Int) {
		val right = panel.x + PANEL_WIDTH
		val record = entry.record
		val enabled = record.state == LifecycleState.ENABLED
		if (enabled) g.fill(panel.x, rowTop, right, rowTop + ROW_HEIGHT, ROW_ENABLED)
		if (mouseX in panel.x until right && mouseY in rowTop until rowTop + ROW_HEIGHT) {
			g.fill(panel.x, rowTop, right, rowTop + ROW_HEIGHT, ROW_HOVER)
		}
		if (focused) drawOutline(g, panel.x, rowTop, right, rowTop + ROW_HEIGHT, GuiStyle.FOCUS)
		val color = if (record.state == LifecycleState.FAILED) GuiStyle.ERROR else GuiStyle.TEXT
		g.text(font, record.module.metadata.name, panel.x + PADDING, rowTop + 2, color)
		val badge = stateBadge(record.state)
		var badgeX = right - font.width(badge) - PADDING
		g.text(font, badge, badgeX, rowTop + 2, color)
		val issues = runtime.moduleIssues(record.id)
		if (issues.missingDependency != null) {
			badgeX -= font.width("[D]") + 1
			g.text(font, "[D]", badgeX, rowTop + 2, GuiStyle.ERROR)
		}
		if (issues.conflictWith != null) {
			badgeX -= font.width("[C]") + 1
			g.text(font, "[C]", badgeX, rowTop + 2, CONFLICT_COLOR)
		}
	}

	private fun drawOutline(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
		g.fill(x0, y0, x1, y0 + 1, color)
		g.fill(x0, y1 - 1, x1, y1, color)
		g.fill(x0, y0, x0 + 1, y1, color)
		g.fill(x1 - 1, y0, x1, y1, color)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val handled = handleMouseClicked(event)
		syncFocus()
		return handled
	}

	private fun handleMouseClicked(event: MouseButtonEvent): Boolean {
		val mouseX = event.x().toInt()
		val mouseY = event.y().toInt()
		val button = event.button()
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (mouseX in SEARCH_X until SEARCH_X + SEARCH_WIDTH && mouseY in SEARCH_Y until SEARCH_Y + SEARCH_HEIGHT) {
				zone = Zone.SEARCH
				return true
			}
			for ((index, chip) in chips.withIndex()) {
				if (mouseX in chip.x until chip.x + chip.width && mouseY in chip.y until chip.y + CHIP_HEIGHT) {
					zone = Zone.FILTERS
					focusedFilter = index
					toggleFilter(chip.filter)
					return true
				}
			}
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
		for (index in panels.indices.reversed()) {
			val panel = panels[index]
			if (!panel.visible) continue
			val right = panel.x + PANEL_WIDTH
			if (mouseX in panel.x until right && mouseY in panel.y until panel.y + TITLE_HEIGHT) {
				if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
				zone = Zone.PANELS
				focusedPanel = index
				focusedItem = -1
				if (mouseX >= right - COLLAPSE_HIT) {
					panel.collapsed = !panel.collapsed
					persistLayout()
				} else {
					draggingPanel = panel
					dragOffsetX = mouseX - panel.x
					dragOffsetY = mouseY - panel.y
				}
				return true
			}
			if (panel.collapsed || mouseX !in panel.x until right) continue
			val bodyTop = panel.y + TITLE_HEIGHT
			if (mouseY < bodyTop || mouseY >= bodyTop + panel.bodyHeight) continue
			var rowTop = bodyTop
			for (entry in panel.filtered) {
				if (mouseY < rowTop + entry.height) {
					zone = Zone.PANELS
					focusedPanel = index
					if (mouseY < rowTop + ROW_HEIGHT) {
						focusedItem = focusItems(panel).indexOfFirst { it === entry }
						if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
							runtime.toggleModule(entry.record.id)
							refreshSearch()
						} else {
							entry.toggleExpanded()
						}
					} else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
						for (control in entry.controls) {
							val leaf = control.mouseClicked(mouseX, mouseY, button) ?: continue
							focusedItem = focusItems(panel).indexOfFirst { it === leaf }
							activeControl = leaf
							break
						}
					}
					return true
				}
				rowTop += entry.height
			}
			return true
		}
		return false
	}

	override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
		activeControl?.let {
			it.mouseDragged(event.x().toInt(), event.y().toInt())
			return true
		}
		val panel = draggingPanel ?: return false
		panel.x = (event.x().toInt() - dragOffsetX).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.y = (event.y().toInt() - dragOffsetY).coerceIn(0, maxOf(0, height - TITLE_HEIGHT))
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		activeControl?.let {
			it.mouseReleased()
			activeControl = null
			return true
		}
		if (draggingPanel == null) return false
		draggingPanel = null
		persistLayout()
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val handled = handleKeyPressed(event)
		syncFocus()
		if (handled) return true
		return super.keyPressed(event)
	}

	private fun handleKeyPressed(event: KeyEvent): Boolean {
		focusedControl()?.takeIf { it.grabsKeyboard }?.let { if (it.keyPressed(event)) return true }
		if (event.key() == GLFW.GLFW_KEY_F && event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
			zone = Zone.SEARCH
			return true
		}
		return when (zone) {
			Zone.SEARCH -> searchKeyPressed(event.key())
			Zone.FILTERS -> filterKeyPressed(event.key())
			Zone.PANELS -> panelKeyPressed(event)
		}
	}

	private fun searchKeyPressed(key: Int): Boolean {
		when (key) {
			GLFW.GLFW_KEY_BACKSPACE -> if (query.isNotEmpty()) {
				query = query.dropLast(1)
				refreshSearch()
			}
			GLFW.GLFW_KEY_ESCAPE -> if (query.isNotEmpty()) {
				query = ""
				refreshSearch()
			} else {
				zone = Zone.PANELS
			}
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_DOWN -> focusFirstVisibleRow()
			GLFW.GLFW_KEY_TAB -> zone = Zone.FILTERS
			else -> return false
		}
		return true
	}

	private fun filterKeyPressed(key: Int): Boolean {
		when (key) {
			GLFW.GLFW_KEY_LEFT -> focusedFilter = (focusedFilter - 1 + chips.size) % chips.size
			GLFW.GLFW_KEY_RIGHT -> focusedFilter = (focusedFilter + 1) % chips.size
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> toggleFilter(chips[focusedFilter].filter)
			GLFW.GLFW_KEY_UP -> zone = Zone.SEARCH
			GLFW.GLFW_KEY_DOWN -> focusFirstVisibleRow()
			GLFW.GLFW_KEY_TAB -> zone = Zone.PANELS
			GLFW.GLFW_KEY_ESCAPE -> zone = Zone.PANELS
			else -> return false
		}
		return true
	}

	private fun panelKeyPressed(event: KeyEvent): Boolean {
		focusedControl()?.let { if (it.keyPressed(event)) return true }
		val nudge = event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0
		when (event.key()) {
			GLFW.GLFW_KEY_ESCAPE -> onClose()
			GLFW.GLFW_KEY_TAB -> changePanel(if (event.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) -1 else 1)
			GLFW.GLFW_KEY_LEFT -> if (nudge) nudgeFocused(-NUDGE_STEP, 0) else changePanel(-1)
			GLFW.GLFW_KEY_RIGHT -> if (nudge) nudgeFocused(NUDGE_STEP, 0) else changePanel(1)
			GLFW.GLFW_KEY_UP -> if (nudge) nudgeFocused(0, -NUDGE_STEP) else moveItem(-1)
			GLFW.GLFW_KEY_DOWN -> if (nudge) nudgeFocused(0, NUDGE_STEP) else moveItem(1)
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activateFocused()
			else -> return false
		}
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val text = event.codepointAsString()
		if (zone == Zone.SEARCH) {
			query += text.filter { it.code >= 32 }
			refreshSearch()
			return true
		}
		focusedControl()?.let { control ->
			var consumed = false
			for (chr in text) consumed = control.charTyped(chr) || consumed
			if (consumed) return true
		}
		return super.charTyped(event)
	}

	private fun focusItems(panel: Panel): List<Any> = panel.filtered.flatMap { entry ->
		if (entry.expanded) listOf<Any>(entry) + entry.controls.flatMap { it.focusables() } else listOf<Any>(entry)
	}

	private fun focusedControl(): SettingControl? {
		if (zone != Zone.PANELS || focusedItem < 0) return null
		val panel = panels.getOrNull(focusedPanel) ?: return null
		return focusItems(panel).getOrNull(focusedItem) as? SettingControl
	}

	private fun syncFocus() {
		val current = focusedControl()
		if (lastFocusedControl !== current) {
			lastFocusedControl?.onFocusLost()
			lastFocusedControl = current
		}
	}

	private fun changePanel(delta: Int) {
		if (panels.none { it.visible }) return
		var index = focusedPanel
		repeat(panels.size) {
			index = (index + delta + panels.size) % panels.size
			if (panels[index].visible) {
				focusedPanel = index
				focusedItem = -1
				return
			}
		}
	}

	private fun moveItem(delta: Int) {
		val panel = panels.getOrNull(focusedPanel) ?: return
		if (panel.collapsed) return
		focusedItem = (focusedItem + delta).coerceIn(-1, focusItems(panel).size - 1)
	}

	private fun activateFocused() {
		val panel = panels.getOrNull(focusedPanel) ?: return
		if (focusedItem == -1) {
			panel.collapsed = !panel.collapsed
			persistLayout()
			return
		}
		val item = focusItems(panel).getOrNull(focusedItem) ?: return
		if (item is Entry) {
			runtime.toggleModule(item.record.id)
			refreshSearch()
		}
	}

	private fun nudgeFocused(dx: Int, dy: Int) {
		val panel = panels.getOrNull(focusedPanel) ?: return
		panel.x = (panel.x + dx).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.y = (panel.y + dy).coerceIn(0, maxOf(0, height - TITLE_HEIGHT))
		persistLayout()
	}

	private fun toggleFilter(filter: ModuleFilter) {
		if (!filters.remove(filter)) filters.add(filter)
		refreshSearch()
	}

	private fun refreshSearch() {
		matchedIds = if (query.isEmpty() && filters.isEmpty()) {
			null
		} else {
			runtime.searchModules(query, filters).mapTo(HashSet()) { it.id }
		}
		for (panel in panels) panel.refilter()
		panels.getOrNull(focusedPanel)?.let { panel ->
			if (!panel.visible) changePanel(1)
			focusedItem = focusedItem.coerceIn(-1, focusItems(panels[focusedPanel]).size - 1)
		}
	}

	private fun focusFirstVisibleRow() {
		val index = panels.indexOfFirst { it.visible }
		if (index < 0) return
		zone = Zone.PANELS
		focusedPanel = index
		focusedItem = if (panels[index].filtered.isEmpty() || panels[index].collapsed) -1 else 0
	}

	private fun describedControl(mouseX: Int, mouseY: Int): SettingControl? {
		focusedControl()?.let { return it }
		for (panel in panels.reversed()) {
			if (!panel.visible || panel.collapsed) continue
			for (entry in panel.filtered) {
				if (!entry.expanded) continue
				entry.controls.flatMap { it.focusables() }.firstOrNull { it.contains(mouseX, mouseY) }?.let { return it }
			}
		}
		return null
	}

	private fun hoveredEntry(mouseX: Int, mouseY: Int): Entry? {
		for (panel in panels.reversed()) {
			if (!panel.visible || panel.collapsed || mouseX !in panel.x until panel.x + PANEL_WIDTH) continue
			var rowTop = panel.y + TITLE_HEIGHT
			for (entry in panel.filtered) {
				if (mouseY in rowTop until rowTop + ROW_HEIGHT) return entry
				rowTop += entry.height
			}
		}
		return null
	}

	private fun layoutChips() {
		var chipX = SEARCH_X + SEARCH_WIDTH + GUTTER
		var chipY = SEARCH_Y
		for (chip in chips) {
			chip.width = font.width(chipText(chip)) + 8
			if (chipX + chip.width > width - MARGIN) {
				chipX = MARGIN
				chipY += CHIP_HEIGHT + 2
			}
			chip.x = chipX
			chip.y = chipY
			chipX += chip.width + 4
		}
	}

	private fun chipText(chip: Chip): String = if (chip.filter in filters) "*${chip.label}" else chip.label

	private fun persistLayout() {
		runtime.savePanelLayout(
			panels.associate { it.category.name to PanelLayoutState(it.x, it.y, it.collapsed, it.unknownFields) },
		)
	}

	override fun onClose() {
		syncFocus()
		lastFocusedControl?.onFocusLost()
		if (parent != null) minecraft.setScreenAndShow(parent) else super.onClose()
	}

	private fun stateBadge(state: LifecycleState): String = when (state) {
		LifecycleState.ENABLED -> "[ON]"
		LifecycleState.FAILED -> "[FAIL]"
		else -> "[OFF]"
	}

	private companion object {
		const val MARGIN = 8
		const val TOP_MARGIN = 40
		const val PADDING = 4
		const val GUTTER = 6
		const val PANEL_WIDTH = 132
		const val TITLE_HEIGHT = 13
		const val ROW_HEIGHT = 12
		const val CONTROL_GAP = 2
		const val COLLAPSE_HIT = 18
		const val NUDGE_STEP = 5

		const val SEARCH_X = MARGIN
		const val SEARCH_Y = 4
		const val SEARCH_WIDTH = 120
		const val SEARCH_HEIGHT = 12
		const val CHIP_HEIGHT = 12

		const val DIM_BACKGROUND = 0x88101018.toInt()
		const val TITLE_BACKGROUND = 0xFF2B2B33.toInt()
		const val TITLE_FOCUSED = 0xFF3A3A47.toInt()
		const val BODY_BACKGROUND = 0xEE181820.toInt()
		const val SETTINGS_BACKGROUND = 0xFF10101A.toInt()
		const val ROW_ENABLED = 0xFF24303F.toInt()
		const val ROW_HOVER = 0x30FFFFFF
		const val CHIP_BACKGROUND = 0xFF2B2B33.toInt()
		const val CHIP_ACTIVE = 0xFF3A5A8C.toInt()
		const val CONFLICT_COLOR = 0xFFFFB454.toInt()

		const val HELP_TEXT = "Tab/Arrows: focus  Enter: toggle  RClick: settings  Ctrl+F: search  Ctrl+Arrows: move  Esc: close"

		fun chipLabel(filter: ModuleFilter): String = when (filter) {
			ModuleFilter.INSTALLED_ADDON -> "Installed"
			ModuleFilter.AVAILABLE_ADDON -> "Available"
			ModuleFilter.PENDING_RESTART -> "Pending restart"
			ModuleFilter.ENABLED -> "Enabled"
			ModuleFilter.DISABLED -> "Disabled"
			ModuleFilter.FAILED -> "Failed"
			ModuleFilter.HAS_CONFLICT -> "Conflict"
			ModuleFilter.MISSING_DEPENDENCY -> "Missing dep"
		}
	}
}
