package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.IntUnaryOperator
import kotlin.math.roundToInt

internal class ClickGuiScreen(
	private val categories: List<Category>,
	private val manager: ModuleManager,
	private val layout: MutableMap<String, PanelState>,
	private val persistLayout: () -> Unit,
	private val persistModules: () -> Unit
) : Screen(Component.literal("Dhen")) {
	private val panels = mutableListOf<Panel>()
	private val navigation = mutableListOf<Panel>()
	private val expanded = mutableSetOf<String>()
	private var navCounts = IntArray(0)
	private var query = ""
	private var matchCount = 0
	private var focusedModule: Module? = null
	private var dragging: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var dragMoved = false
	private var controlDrag: Panel? = null
	private var focusPanel: Panel? = null
	private var swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
	private var scrollOffset = 0

	override fun init() {
		blurFocus()
		panels.clear()
		navigation.clear()
		val byCategory = manager.categories
		val perRow = maxOf(1, (width - 2 * MARGIN + COLUMN_GAP) / (PANEL_WIDTH + COLUMN_GAP))
		categories.forEachIndexed { index, category ->
			val state = layout[category.name]?.copy() ?: PanelState(
				x = MARGIN + (index % perRow) * (PANEL_WIDTH + COLUMN_GAP),
				y = FIELD_TOP + (index / perRow) * (PANEL_HEIGHT + COLUMN_GAP),
				collapsed = false
			)
			val panel = Panel(category, state, byCategory[category] ?: emptyList())
			clampToField(panel)
			panels += panel
			navigation += panel
		}
		navCounts = IntArray(panels.size)
		applySearch()
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val contentMouseY = mouseY + scrollOffset
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(0f, -scrollOffset.toFloat())
		for (i in panels.indices) {
			val panel = panels[i]
			if (!panel.hidden) panel.draw(graphics, font, mouseX, contentMouseY)
		}
		pose.popMatrix()
		drawScrollbar(graphics)
		drawSearch(graphics)
		if (!searchContains(mouseX, mouseY)) {
			hoveredModule(mouseX, contentMouseY)?.let { drawTooltip(graphics, it, mouseX, mouseY) }
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val button = event.button()
		focusPanel?.let { armed ->
			val result = armed.captureMouse(button)
			if (result != ControlKey.IGNORED) {
				focusPanel = null
				if (result == ControlKey.COMMITTED) persistModules()
				return true
			}
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick)
		}
		blurFocus()
		val x = event.x().toInt()
		val sy = event.y().toInt()
		if (searchContains(x, sy)) return true
		val cy = sy + scrollOffset
		val panel = panelAt(x, cy) ?: return super.mouseClicked(event, doubleClick)
		bringToFront(panel)
		if (panel.headerContains(x, cy)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || x >= panel.toggleLeft) {
				panel.state.collapsed = !panel.state.collapsed
				refreshFocus()
				scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
				store(panel)
			} else {
				dragging = panel
				dragMoved = false
				dragOffsetX = x - panel.state.x
				dragOffsetY = sy - (panel.state.y - scrollOffset)
			}
			return true
		}
		val module = panel.rowAt(x, cy)
		if (module != null) {
			focusedModule = module
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				manager.toggle(module)
				persistModules()
			} else {
				if (!expanded.remove(module.name)) expanded.add(module.name)
				scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
			}
			return true
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			when (panel.pressControl(x, cy)) {
				ControlPress.TRACK -> controlDrag = panel
				ControlPress.CHANGED -> {
					scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
					persistModules()
				}
				ControlPress.FOCUS -> focusPanel = panel
				ControlPress.INVOKED -> Unit
				ControlPress.NONE -> Unit
			}
		}
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() != swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		focusPanel?.let { panel ->
			when (panel.keyInput(event.key(), event.modifiers())) {
				ControlKey.COMMITTED -> {
					focusPanel = null
					swallowCharKey = event.key()
					persistModules()
					return true
				}
				ControlKey.CANCELLED -> {
					focusPanel = null
					swallowCharKey = event.key()
					return true
				}
				ControlKey.CONSUMED -> return true
				ControlKey.IGNORED -> Unit
			}
		}
		return when (event.key()) {
			GLFW.GLFW_KEY_DOWN -> { moveFocus(1); true }
			GLFW.GLFW_KEY_UP -> { moveFocus(-1); true }
			GLFW.GLFW_KEY_RIGHT -> { setFocusedExpanded(true); true }
			GLFW.GLFW_KEY_LEFT -> { setFocusedExpanded(false); true }
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> toggleFocused() || super.keyPressed(event)
			GLFW.GLFW_KEY_BACKSPACE -> backspaceSearch() || super.keyPressed(event)
			GLFW.GLFW_KEY_ESCAPE -> collapseVisibleSettings() || super.keyPressed(event)
			else -> super.keyPressed(event)
		}
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		if (event.key() == swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (focusPanel?.charInput(codepoint) == true) return true
		if (swallowCharKey != GLFW.GLFW_KEY_UNKNOWN) return true
		if (codepoint !in PRINTABLE_MIN..PRINTABLE_MAX || codepoint == DELETE_CODE) return super.charTyped(event)
		if (query.length < SEARCH_MAX_LENGTH) {
			query += codepoint.toChar()
			applySearch()
		}
		return true
	}

	override fun removed() {
		blurFocus()
		super.removed()
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		dragging?.let { panel ->
			val newX = (event.x().toInt() - dragOffsetX).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
			val screenY = (event.y().toInt() - dragOffsetY).coerceIn(0, maxOf(0, height - panel.height))
			val newY = maxOf(FIELD_TOP, screenY + scrollOffset)
			if (newX != panel.state.x || newY != panel.state.y) {
				panel.state.x = newX
				panel.state.y = newY
				dragMoved = true
			}
			return true
		}
		controlDrag?.let { panel ->
			panel.dragControl(event.x().toInt())
			return true
		}
		return super.mouseDragged(event, dragX, dragY)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (dragging != null || controlDrag != null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val max = maxScroll()
		if (max <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		scrollOffset = ClickGuiScroll.clampOffset(scrollOffset - (scrollY * SCROLL_STEP).roundToInt(), max)
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		dragging?.let { panel ->
			dragging = null
			if (dragMoved) store(panel)
			return true
		}
		if (controlDrag != null) {
			controlDrag = null
			scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
			persistModules()
			return true
		}
		return super.mouseReleased(event)
	}

	private fun store(panel: Panel) {
		layout[panel.category.name] = panel.state.copy()
		persistLayout()
	}

	private fun backspaceSearch(): Boolean {
		if (query.isEmpty()) return false
		query = query.substring(0, query.length - 1)
		applySearch()
		return true
	}

	private fun applySearch() {
		for (i in navigation.indices) navigation[i].applyFilter(query)
		matchCount = refreshNavCounts()
		cancelPointerInteractions()
		refreshFocus()
		scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
	}

	private fun cancelPointerInteractions() {
		val panel = dragging
		if (panel != null && panel.hidden) {
			dragging = null
			if (dragMoved) store(panel)
		}
		if (controlDrag != null) {
			controlDrag = null
			persistModules()
		}
	}

	private fun refreshFocus() {
		val module = focusedModule
		if (module == null || !isNavigable(module)) {
			focusedModule = if (query.isEmpty()) null else firstNavigableModule()
		}
		focusIntoView()
	}

	private fun firstNavigableModule(): Module? {
		for (i in navigation.indices) {
			val panel = navigation[i]
			if (panel.navigableCount > 0) return panel.moduleAtRow(0)
		}
		return null
	}

	private fun isNavigable(module: Module): Boolean {
		for (i in navigation.indices) {
			if (navigation[i].navigableRowOf(module) >= 0) return true
		}
		return false
	}

	private fun moveFocus(delta: Int) {
		val total = refreshNavCounts()
		if (total == 0) {
			focusedModule = null
			return
		}
		val flat = ClickGuiNav.step(total, focusFlat(), delta)
		val panel = navigation[ClickGuiNav.panelOf(navCounts, flat)]
		focusedModule = panel.moduleAtRow(ClickGuiNav.rowOf(navCounts, flat))
		focusIntoView()
	}

	private fun refreshNavCounts(): Int {
		var total = 0
		for (i in navigation.indices) {
			val count = navigation[i].navigableCount
			navCounts[i] = count
			total += count
		}
		return total
	}

	private fun focusFlat(): Int {
		val module = focusedModule ?: return ClickGuiNav.NONE
		for (i in navigation.indices) {
			val row = navigation[i].navigableRowOf(module)
			if (row >= 0) return ClickGuiNav.flatten(navCounts, i, row)
		}
		return ClickGuiNav.NONE
	}

	private fun toggleFocused(): Boolean {
		val module = focusedModule ?: return false
		manager.toggle(module)
		persistModules()
		return true
	}

	private fun setFocusedExpanded(expand: Boolean) {
		val module = focusedModule ?: return
		val changed = if (expand) expanded.add(module.name) else expanded.remove(module.name)
		if (!changed) return
		scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
		focusIntoView()
	}

	private fun collapseVisibleSettings(): Boolean {
		var collapsed = false
		for (i in navigation.indices) {
			if (navigation[i].collapseSettings()) collapsed = true
		}
		if (collapsed) {
			scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
			focusIntoView()
		}
		return collapsed
	}

	private fun focusIntoView() {
		val module = focusedModule ?: return
		for (i in navigation.indices) {
			val panel = navigation[i]
			val row = panel.navigableRowOf(module)
			if (row >= 0) {
				scrollIntoView(panel.rowTopAt(row))
				return
			}
		}
	}

	private fun scrollIntoView(rowTop: Int) {
		val max = maxScroll()
		val target = when {
			rowTop - FIELD_TOP < scrollOffset -> rowTop - FIELD_TOP
			rowTop + ROW_HEIGHT + MARGIN > scrollOffset + height -> rowTop + ROW_HEIGHT + MARGIN - height
			else -> scrollOffset
		}
		scrollOffset = ClickGuiScroll.clampOffset(target, max)
	}

	private fun blurFocus() {
		focusPanel?.let { if (it.blur()) persistModules() }
		focusPanel = null
	}

	private fun clampToField(panel: Panel) {
		panel.state.x = panel.state.x.coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.state.y = panel.state.y.coerceAtLeast(FIELD_TOP)
	}

	private fun contentBottom(): Int {
		var bottom = 0
		for (i in panels.indices) {
			val panel = panels[i]
			if (panel.hidden) continue
			val panelBottom = panel.state.y + panel.height
			if (panelBottom > bottom) bottom = panelBottom
		}
		return bottom
	}

	private fun maxScroll(): Int = ClickGuiScroll.maxScroll(contentBottom(), height, MARGIN)

	private fun panelAt(x: Int, y: Int): Panel? {
		for (i in panels.size - 1 downTo 0) {
			val panel = panels[i]
			if (!panel.hidden && panel.contains(x, y)) return panel
		}
		return null
	}

	private fun searchContains(x: Int, y: Int): Boolean =
		x >= MARGIN && x < MARGIN + SEARCH_WIDTH && y >= MARGIN && y < MARGIN + SEARCH_HEIGHT

	private fun drawSearch(graphics: GuiGraphicsExtractor) {
		val left = MARGIN
		val top = MARGIN
		val right = left + SEARCH_WIDTH
		val bottom = top + SEARCH_HEIGHT
		FlatGui.fill(graphics, left, top, right, bottom, DhenPalette.SURFACE_RAISED)
		FlatGui.border(graphics, left, top, right, bottom, if (query.isEmpty()) DhenPalette.BORDER else DhenPalette.ACCENT)
		val textLeft = left + CONTENT_PAD
		val textTop = top + (SEARCH_HEIGHT - font.lineHeight) / 2
		if (query.isEmpty()) {
			FlatGui.text(graphics, font, SEARCH_PLACEHOLDER, textLeft, textTop, DhenPalette.TEXT_DISABLED)
			return
		}
		FlatGui.text(graphics, font, query, textLeft, textTop, DhenPalette.TEXT_PRIMARY)
		if (focusPanel == null) {
			val caretX = textLeft + font.width(query)
			FlatGui.fill(graphics, caretX, textTop, caretX + 1, textTop + font.lineHeight, DhenPalette.TEXT_PRIMARY)
		}
		if (matchCount == 0) {
			FlatGui.text(graphics, font, NO_MATCH_LABEL, right + CONTENT_PAD, textTop, DhenPalette.TEXT_SECONDARY)
		}
	}

	private fun hoveredModule(x: Int, contentY: Int): Module? =
		panelAt(x, contentY)?.rowAt(x, contentY)

	private fun drawTooltip(graphics: GuiGraphicsExtractor, module: Module, mouseX: Int, mouseY: Int) {
		val hasDescription = module.description.isNotBlank()
		val textWidth = if (hasDescription) maxOf(font.width(module.name), font.width(module.description)) else font.width(module.name)
		val lineCount = if (hasDescription) 2 else 1
		val boxWidth = textWidth + 2 * TOOLTIP_PAD
		val boxHeight = lineCount * font.lineHeight + 2 * TOOLTIP_PAD
		val left = (mouseX + TOOLTIP_OFFSET).coerceIn(0, maxOf(0, width - boxWidth))
		val top = (mouseY + TOOLTIP_OFFSET).coerceIn(0, maxOf(0, height - boxHeight))
		FlatGui.fill(graphics, left, top, left + boxWidth, top + boxHeight, DhenPalette.SURFACE_RAISED)
		FlatGui.border(graphics, left, top, left + boxWidth, top + boxHeight, DhenPalette.BORDER)
		FlatGui.text(graphics, font, module.name, left + TOOLTIP_PAD, top + TOOLTIP_PAD, DhenPalette.TEXT_PRIMARY)
		if (hasDescription) {
			FlatGui.text(graphics, font, module.description, left + TOOLTIP_PAD, top + TOOLTIP_PAD + font.lineHeight, DhenPalette.TEXT_SECONDARY)
		}
	}

	private fun drawScrollbar(graphics: GuiGraphicsExtractor) {
		val contentBottom = contentBottom()
		val max = ClickGuiScroll.maxScroll(contentBottom, height, MARGIN)
		if (max <= 0) return
		val contentHeight = contentBottom + MARGIN
		val trackLeft = width - SCROLLBAR_WIDTH
		val thumbHeight = maxOf(SCROLLBAR_MIN_THUMB, height * height / contentHeight)
		val thumbTop = scrollOffset * (height - thumbHeight) / max
		FlatGui.fill(graphics, trackLeft, 0, width, height, DhenPalette.SURFACE_RAISED)
		FlatGui.fill(graphics, trackLeft, thumbTop, width, thumbTop + thumbHeight, DhenPalette.BORDER)
	}

	private fun bringToFront(panel: Panel) {
		if (panels.lastOrNull() === panel) return
		panels.remove(panel)
		panels += panel
	}

	private inner class Panel(val category: Category, val state: PanelState, val modules: List<Module>) {
		private val controls: List<List<SettingControl?>> = modules.map { module -> module.settings.map(::controlFor) }
		private val visibleRows = IntArray(modules.size) { it }
		private var visibleCount = modules.size
		private val settingsHeightAt = IntUnaryOperator { row -> settingsHeight(visibleRows[row]) }
		private var activeControl: SettingControl? = null
		private var activeLeft = 0
		private var activeWidth = 0
		private var focusedControl: SettingControl? = null

		val height: Int
			get() = HEADER_HEIGHT + if (state.collapsed) 0 else ClickGuiRows.bodyHeight(visibleCount, ROW_HEIGHT, settingsHeightAt)
		val toggleLeft: Int
			get() = state.x + PANEL_WIDTH - TOGGLE_WIDTH
		val hidden: Boolean
			get() = query.isNotEmpty() && visibleCount == 0
		val navigableCount: Int
			get() = if (state.collapsed || hidden) 0 else visibleCount

		fun applyFilter(query: String) {
			var count = 0
			for (i in modules.indices) {
				val module = modules[i]
				if (!ClickGuiSearch.matches(query, module.name, module.description)) continue
				visibleRows[count] = i
				count++
			}
			visibleCount = count
		}

		fun moduleAtRow(row: Int): Module = modules[visibleRows[row]]

		fun navigableRowOf(module: Module): Int {
			if (navigableCount == 0) return -1
			for (row in 0 until visibleCount) {
				if (modules[visibleRows[row]] === module) return row
			}
			return -1
		}

		fun rowTopAt(row: Int): Int =
			state.y + HEADER_HEIGHT + ClickGuiRows.rowTop(row, ROW_HEIGHT, settingsHeightAt)

		fun collapseSettings(): Boolean {
			if (navigableCount == 0) return false
			var collapsed = false
			for (row in 0 until visibleCount) {
				if (expanded.remove(modules[visibleRows[row]].name)) collapsed = true
			}
			return collapsed
		}

		private fun settingsHeight(index: Int): Int {
			if (state.collapsed || modules[index].name !in expanded) return 0
			return 2 * SETTINGS_PAD + renderableCount(index) * CONTROL_HEIGHT
		}

		private fun renderableCount(index: Int): Int {
			val list = controls[index]
			var count = 0
			for (i in list.indices) {
				val control = list[i]
				if (control != null && control.setting.isVisible) count++
			}
			return count
		}

		fun contains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + height

		fun headerContains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + HEADER_HEIGHT

		fun rowAt(px: Int, py: Int): Module? {
			if (state.collapsed) return null
			if (px < state.x || px >= state.x + PANEL_WIDTH) return null
			val row = ClickGuiRows.rowAt(py - (state.y + HEADER_HEIGHT), visibleCount, ROW_HEIGHT, settingsHeightAt)
				?: return null
			return moduleAtRow(row)
		}

		fun pressControl(px: Int, py: Int): ControlPress {
			val control = controlAt(px, py) ?: return ControlPress.NONE
			val left = state.x + CONTENT_PAD
			val width = PANEL_WIDTH - 2 * CONTENT_PAD
			val result = control.press(px - left, width)
			when (result) {
				ControlPress.TRACK -> {
					activeControl = control
					activeLeft = left
					activeWidth = width
				}
				ControlPress.FOCUS -> focusedControl = control
				else -> Unit
			}
			return result
		}

		fun dragControl(px: Int) {
			activeControl?.drag(px - activeLeft, activeWidth)
		}

		fun keyInput(key: Int, modifiers: Int): ControlKey {
			val result = focusedControl?.keyPressed(key, modifiers) ?: ControlKey.IGNORED
			if (result == ControlKey.COMMITTED || result == ControlKey.CANCELLED) focusedControl = null
			return result
		}

		fun charInput(codepoint: Int): Boolean = focusedControl?.charTyped(codepoint) ?: false

		fun captureMouse(button: Int): ControlKey {
			val result = focusedControl?.captureMouse(button) ?: ControlKey.IGNORED
			if (result == ControlKey.COMMITTED || result == ControlKey.CANCELLED) focusedControl = null
			return result
		}

		fun blur(): Boolean {
			val changed = focusedControl?.blur() ?: false
			focusedControl = null
			return changed
		}

		private fun controlAt(px: Int, py: Int): SettingControl? {
			if (state.collapsed) return null
			if (px < state.x + CONTENT_PAD || px >= state.x + PANEL_WIDTH - CONTENT_PAD) return null
			var top = state.y + HEADER_HEIGHT
			for (row in 0 until visibleCount) {
				val index = visibleRows[row]
				top += ROW_HEIGHT
				val areaHeight = settingsHeight(index)
				if (areaHeight > 0) {
					if (py >= top && py < top + areaHeight) return renderableAt(index, py - (top + SETTINGS_PAD))
					top += areaHeight
				}
			}
			return null
		}

		private fun renderableAt(index: Int, localY: Int): SettingControl? {
			if (localY < 0) return null
			val target = localY / CONTROL_HEIGHT
			val list = controls[index]
			var seen = 0
			for (i in list.indices) {
				val control = list[i]
				if (control == null || !control.setting.isVisible) continue
				if (seen == target) return control
				seen++
			}
			return null
		}

		fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
			val left = state.x
			val top = state.y
			val right = left + PANEL_WIDTH
			val headerBottom = top + HEADER_HEIGHT
			val bottom = top + height

			FlatGui.fill(graphics, left, top, right, bottom, DhenPalette.SURFACE)
			val headerColor = if (headerContains(mouseX, mouseY)) DhenPalette.SURFACE_INTERACTIVE else DhenPalette.SURFACE_RAISED
			FlatGui.fill(graphics, left, top, right, headerBottom, headerColor)

			if (!state.collapsed) {
				var rowTop = headerBottom
				for (row in 0 until visibleCount) {
					val index = visibleRows[row]
					drawRow(graphics, font, modules[index], left, right, rowTop, mouseX, mouseY)
					rowTop += ROW_HEIGHT
					val areaHeight = settingsHeight(index)
					if (areaHeight > 0) {
						drawSettings(graphics, font, index, left, right, rowTop, areaHeight, mouseX, mouseY)
						rowTop += areaHeight
					}
				}
			}

			FlatGui.border(graphics, left, top, right, bottom, DhenPalette.BORDER)

			FlatGui.text(graphics, font, category.displayName, left + CONTENT_PAD, top + TEXT_OFFSET, DhenPalette.TEXT_PRIMARY)
			val glyph = if (state.collapsed) "+" else "-"
			FlatGui.text(graphics, font, glyph, right - TOGGLE_WIDTH + TOGGLE_GLYPH_INSET, top + TEXT_OFFSET, DhenPalette.TEXT_SECONDARY)
		}

		private fun drawRow(
			graphics: GuiGraphicsExtractor,
			font: Font,
			module: Module,
			left: Int,
			right: Int,
			rowTop: Int,
			mouseX: Int,
			mouseY: Int
		) {
			val rowBottom = rowTop + ROW_HEIGHT
			val hovered = mouseX in left until right && mouseY in rowTop until rowBottom
			val background = when {
				hovered -> DhenPalette.SURFACE_INTERACTIVE
				module.enabled -> DhenPalette.SURFACE_RAISED
				else -> null
			}
			if (background != null) FlatGui.fill(graphics, left + 1, rowTop, right - 1, rowBottom, background)
			if (module === focusedModule) FlatGui.border(graphics, left + 1, rowTop, right - 1, rowBottom, DhenPalette.ACCENT)

			val nameColor = if (module.enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
			FlatGui.text(graphics, font, module.name, left + CONTENT_PAD, rowTop + ROW_TEXT_OFFSET, nameColor)

			val boxRight = right - CONTENT_PAD
			val boxLeft = boxRight - INDICATOR_SIZE
			val boxTop = rowTop + (ROW_HEIGHT - INDICATOR_SIZE) / 2
			val boxBottom = boxTop + INDICATOR_SIZE
			if (module.enabled) {
				FlatGui.fill(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.ACCENT)
			} else {
				FlatGui.border(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.BORDER)
			}
		}

		private fun drawSettings(
			graphics: GuiGraphicsExtractor,
			font: Font,
			index: Int,
			left: Int,
			right: Int,
			top: Int,
			areaHeight: Int,
			mouseX: Int,
			mouseY: Int
		) {
			FlatGui.fill(graphics, left + 1, top, right - 1, top + areaHeight, DhenPalette.CANVAS)
			FlatGui.fill(graphics, left + 1, top, right - 1, top + 1, DhenPalette.BORDER)
			val contentLeft = left + CONTENT_PAD
			val contentWidth = PANEL_WIDTH - 2 * CONTENT_PAD
			var y = top + SETTINGS_PAD
			val list = controls[index]
			for (i in list.indices) {
				val control = list[i]
				if (control == null || !control.setting.isVisible) continue
				val hovered = mouseX in contentLeft until contentLeft + contentWidth && mouseY in y until y + CONTROL_HEIGHT
				control.draw(graphics, font, contentLeft, y, contentWidth, CONTROL_HEIGHT, hovered)
				y += CONTROL_HEIGHT
			}
		}
	}

	private companion object {
		const val PANEL_WIDTH = 118
		const val HEADER_HEIGHT = 16
		const val PANEL_HEIGHT = HEADER_HEIGHT + 40
		const val ROW_HEIGHT = 13
		const val ROW_TEXT_OFFSET = 3
		const val CONTROL_HEIGHT = 12
		const val SETTINGS_PAD = 3
		const val INDICATOR_SIZE = 8
		const val MARGIN = 8
		const val COLUMN_GAP = 8
		const val SEARCH_WIDTH = 140
		const val SEARCH_HEIGHT = 16
		const val SEARCH_MAX_LENGTH = 20
		const val FIELD_TOP = MARGIN + SEARCH_HEIGHT + COLUMN_GAP
		const val SEARCH_PLACEHOLDER = "Type to search"
		const val NO_MATCH_LABEL = "No matches"
		const val CONTENT_PAD = 6
		const val TEXT_OFFSET = 4
		const val TOGGLE_WIDTH = 14
		const val TOGGLE_GLYPH_INSET = 5
		const val TOOLTIP_PAD = 4
		const val TOOLTIP_OFFSET = 10
		const val SCROLL_STEP = 20
		const val SCROLLBAR_WIDTH = 3
		const val SCROLLBAR_MIN_THUMB = 16
	}
}
