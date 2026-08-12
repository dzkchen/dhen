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
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import java.util.function.IntUnaryOperator
import kotlin.math.roundToInt

internal class ClickGuiShellScreen(
	private val categories: List<Category>,
	private val manager: ModuleManager,
	private val collapsed: MutableSet<String>,
	private val persistCore: () -> Unit,
	private val persistModules: () -> Unit,
	private val parent: Screen? = null
) : Screen(Component.literal("Dhen")) {
	private val openedAt = Util.getMillis()
	private val columns = mutableListOf<Column>()
	private val visible = mutableListOf<Column>()
	private val expanded = mutableSetOf<String>()
	private val tabWidths = IntArray(TAB_LABELS.size)
	private val scroll = ScrollState()
	private var query = ""
	private var activeTab = CLICK_GUI_TAB
	private var settled = false
	private var barWidth = 0
	private var glyphWidth = 0
	private var dragged: SettingControl? = null
	private var dragLeft = 0
	private var focused: SettingControl? = null
	private var swallowCharKey = GLFW.GLFW_KEY_UNKNOWN

	override fun init() {
		blurFocus()
		cancelDrag()
		if (columns.isEmpty()) {
			val byCategory = manager.categories
			for (i in categories.indices) {
				val category = categories[i]
				columns += Column(category, byCategory[category] ?: emptyList())
			}
		}
		measureChrome()
		applySearch()
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val outsideWorld = minecraft.level == null
		if (outsideWorld) extractPanorama(graphics, a)
		if (Effects.reduced) {
			if (outsideWorld) extractMenuBackground(graphics)
			return
		}
		extractBlurredBackground(graphics)
		GlassGui.scrim(graphics, width, height)
	}

	override fun onClose() {
		val previous = parent
		if (previous == null) super.onClose() else minecraft.gui.setScreen(previous)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		if (settled) {
			drawContent(graphics, mouseX, mouseY)
			return
		}
		val entry = GlassGui.entryProgress(openedAt)
		if (entry >= GlassGui.SETTLED) {
			settled = true
			drawContent(graphics, mouseX, mouseY)
			return
		}
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(0f, GlassGui.rise(entry))
		drawContent(graphics, mouseX, mouseY)
		pose.popMatrix()
		GlassGui.veil(graphics, width, height, entry)
	}

	private fun drawContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		drawTabs(graphics)
		drawSearch(graphics)
		if (activeTab != CLICK_GUI_TAB) {
			DhenType.text(
				graphics,
				font,
				SETTINGS_STUB,
				ClickGuiShell.centeredLeft(width, DhenType.width(font, SETTINGS_STUB)),
				FIELD_TOP + DhenType.lineHeight(font),
				DhenPalette.TEXT_SECONDARY
			)
			return
		}
		for (i in visible.indices) {
			val column = visible[i]
			val left = column.contentLeft - scroll.offset
			if (left + COLUMN_WIDTH > 0 && left < width) column.draw(graphics, font, left, mouseX, mouseY)
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val button = event.button()
		val armed = focused
		if (armed != null) {
			val result = armed.captureMouse(button)
			if (result != ControlKey.IGNORED) {
				focused = null
				if (result == ControlKey.COMMITTED) persistModules()
				return true
			}
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick)
		}
		blurFocus()
		val x = event.x().toInt()
		val y = event.y().toInt()
		val tab = tabAt(x, y)
		if (tab != ClickGuiShell.NONE) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) activeTab = tab
			return true
		}
		if (searchContains(x, y)) return true
		if (activeTab != CLICK_GUI_TAB) return true
		val contentX = x + scroll.offset
		val column = columnAt(contentX, y) ?: return true
		if (y < FIELD_TOP + HEADER_HEIGHT) {
			toggleCollapsed(column)
			return true
		}
		val module = column.rowAt(y)
		if (module != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) manager.toggle(module)
			else if (!expanded.remove(module.name)) expanded.add(module.name)
			return true
		}
		val control = column.controlAt(contentX, y)
		if (control != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressControl(control, column.contentLeft + CONTENT_PAD, contentX)
		}
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() != swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		val control = focused
		if (control != null) {
			val result = control.keyPressed(event.key(), event.modifiers())
			if (result != ControlKey.IGNORED) {
				if (result != ControlKey.CONSUMED) {
					focused = null
					swallowCharKey = event.key()
					if (result == ControlKey.COMMITTED) persistModules()
				}
				return true
			}
		}
		return when (event.key()) {
			GLFW.GLFW_KEY_BACKSPACE -> backspaceSearch() || super.keyPressed(event)
			GLFW.GLFW_KEY_ESCAPE -> collapseExpandedSettings() || super.keyPressed(event)
			else -> super.keyPressed(event)
		}
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		if (event.key() == swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (focused?.charTyped(codepoint) == true) return true
		if (swallowCharKey != GLFW.GLFW_KEY_UNKNOWN) return true
		if (!isPrintable(codepoint)) return super.charTyped(event)
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
		val control = dragged ?: return super.mouseDragged(event, dragX, dragY)
		control.drag(event.x().toInt() + scroll.offset - dragLeft, CONTROLS_WIDTH)
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (dragged != null || activeTab != CLICK_GUI_TAB) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		}
		val max = maxScroll()
		if (max <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		scroll.scrollTo(scroll.offset - ((scrollY + scrollX) * SCROLL_STEP).roundToInt(), max)
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (dragged == null) return super.mouseReleased(event)
		cancelDrag()
		return true
	}

	private fun measureChrome() {
		for (i in TAB_LABELS.indices) tabWidths[i] = DhenType.width(font, TAB_LABELS[i]) + 2 * TAB_PAD
		barWidth = 2 * BAR_PAD + ClickGuiShell.segmentsWidth(tabWidths, TAB_GAP)
		glyphWidth = maxOf(DhenType.width(font, EXPAND_GLYPH), DhenType.width(font, COLLAPSE_GLYPH))
	}

	private fun pressControl(control: SettingControl, controlsLeft: Int, contentX: Int) {
		when (control.press(contentX - controlsLeft, CONTROLS_WIDTH)) {
			ControlPress.TRACK -> {
				dragged = control
				dragLeft = controlsLeft
			}
			ControlPress.CHANGED -> persistModules()
			ControlPress.FOCUS -> focused = control
			else -> Unit
		}
	}

	private fun toggleCollapsed(column: Column) {
		if (!collapsed.remove(column.category.name)) collapsed.add(column.category.name)
		persistCore()
	}

	private fun backspaceSearch(): Boolean {
		if (query.isEmpty()) return false
		query = query.substring(0, query.length - 1)
		applySearch()
		return true
	}

	private fun collapseExpandedSettings(): Boolean {
		var changed = false
		for (i in columns.indices) {
			if (columns[i].collapseSettings()) changed = true
		}
		return changed
	}

	private fun applySearch() {
		for (i in columns.indices) columns[i].applyFilter(query)
		cancelDrag()
		layout()
	}

	private fun layout() {
		visible.clear()
		for (i in columns.indices) {
			val column = columns[i]
			if (column.hidden) continue
			column.contentLeft = MARGIN + ClickGuiShell.columnLeft(visible.size, COLUMN_WIDTH, COLUMN_GAP)
			visible += column
		}
		scroll.refilter(maxScroll())
	}

	private fun cancelDrag() {
		if (dragged == null) return
		dragged = null
		persistModules()
	}

	private fun blurFocus() {
		val control = focused ?: return
		focused = null
		if (control.blur()) persistModules()
	}

	private fun maxScroll(): Int =
		ClickGuiScroll.maxScroll(MARGIN + ClickGuiShell.fieldWidth(visible.size, COLUMN_WIDTH, COLUMN_GAP), width, MARGIN)

	private fun columnAt(contentX: Int, y: Int): Column? {
		if (y < FIELD_TOP) return null
		val slot = ClickGuiShell.slotAt(contentX - MARGIN, visible.size, COLUMN_WIDTH, COLUMN_GAP)
		if (slot == ClickGuiShell.NONE) return null
		val column = visible[slot]
		return if (y < FIELD_TOP + column.height) column else null
	}

	private fun barLeft(): Int = ClickGuiShell.centeredLeft(width, barWidth)

	private fun searchLeft(): Int = ClickGuiShell.centeredLeft(width, SEARCH_WIDTH)

	private fun tabAt(x: Int, y: Int): Int {
		if (y < TAB_TOP || y >= TAB_TOP + BAR_HEIGHT) return ClickGuiShell.NONE
		return ClickGuiShell.segmentAt(x - (barLeft() + BAR_PAD), tabWidths, TAB_GAP)
	}

	private fun searchContains(x: Int, y: Int): Boolean =
		x >= searchLeft() && x < searchLeft() + SEARCH_WIDTH && y >= SEARCH_TOP && y < SEARCH_TOP + SEARCH_HEIGHT

	private fun drawTabs(graphics: GuiGraphicsExtractor) {
		val barLeft = barLeft()
		val bottom = TAB_TOP + BAR_HEIGHT
		GlassGui.roundedFrame(graphics, barLeft, TAB_TOP, barLeft + barWidth, bottom, RoundedQuad.FULL, GlassGui.raised(), DhenPalette.BORDER)
		val top = textTop(font, TAB_TOP, BAR_HEIGHT)
		var left = barLeft + BAR_PAD
		for (i in TAB_LABELS.indices) {
			val right = left + tabWidths[i]
			val active = i == activeTab
			if (active) RoundedGui.pill(graphics, left, TAB_TOP + BAR_PAD, right, bottom - BAR_PAD, DhenPalette.accent)
			val color = if (active) DhenPalette.textOnAccent else DhenPalette.TEXT_SECONDARY
			DhenType.text(graphics, font, TAB_LABELS[i], left + TAB_PAD, top, color)
			left = right + TAB_GAP
		}
	}

	private fun drawSearch(graphics: GuiGraphicsExtractor) {
		val left = searchLeft()
		val right = left + SEARCH_WIDTH
		val bottom = SEARCH_TOP + SEARCH_HEIGHT
		val outline = if (query.isEmpty()) DhenPalette.BORDER else DhenPalette.accent
		GlassGui.roundedFrame(graphics, left, SEARCH_TOP, right, bottom, RoundedQuad.FULL, GlassGui.surface(), outline)
		val textLeft = left + SEARCH_PAD
		val top = textTop(font, SEARCH_TOP, SEARCH_HEIGHT)
		if (query.isEmpty()) {
			DhenType.text(graphics, font, SEARCH_PLACEHOLDER, textLeft, top, DhenPalette.TEXT_DISABLED)
			return
		}
		DhenType.text(graphics, font, query, textLeft, top, DhenPalette.TEXT_PRIMARY)
		if (focused == null) caret(graphics, font, textLeft + DhenType.width(font, query), top)
		if (visible.isEmpty()) {
			val labelLeft = ClickGuiShell.centeredLeft(width, DhenType.width(font, NO_MATCH_LABEL))
			DhenType.text(graphics, font, NO_MATCH_LABEL, labelLeft, bottom + SEARCH_PAD, DhenPalette.TEXT_SECONDARY)
		}
	}

	private inner class Column(val category: Category, private val modules: List<Module>) {
		private val controls: List<List<SettingControl>> = modules.map { module -> module.settings.mapNotNull(::controlFor) }
		private val visibleRows = IntArray(modules.size) { it }
		private var visibleCount = modules.size
		private val settingsHeightAt = IntUnaryOperator { row -> settingsHeight(visibleRows[row]) }

		var contentLeft = 0

		val hidden: Boolean
			get() = query.isNotEmpty() && visibleCount == 0
		val height: Int
			get() = heightOf(category.name in collapsed)

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

		fun rowAt(y: Int): Module? {
			val row = ClickGuiRows.rowAt(y - (FIELD_TOP + HEADER_HEIGHT), visibleCount, ROW_HEIGHT, settingsHeightAt) ?: return null
			return modules[visibleRows[row]]
		}

		fun controlAt(contentX: Int, y: Int): SettingControl? {
			if (contentX < contentLeft + CONTENT_PAD || contentX >= contentLeft + CONTENT_PAD + CONTROLS_WIDTH) return null
			val localY = y - (FIELD_TOP + HEADER_HEIGHT)
			val row = ClickGuiRows.settingsRowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt) ?: return null
			val bandTop = ClickGuiRows.settingsTop(row, ROW_HEIGHT, settingsHeightAt)
			return renderableAt(visibleRows[row], localY - bandTop - SETTINGS_PAD)
		}

		fun collapseSettings(): Boolean {
			var changed = false
			for (row in 0 until visibleCount) {
				if (expanded.remove(modules[visibleRows[row]].name)) changed = true
			}
			return changed
		}

		fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, mouseX: Int, mouseY: Int) {
			val right = left + COLUMN_WIDTH
			val headerBottom = FIELD_TOP + HEADER_HEIGHT
			val isCollapsed = category.name in collapsed
			GlassGui.roundedFrame(graphics, left, FIELD_TOP, right, FIELD_TOP + heightOf(isCollapsed), COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
			val hovered = mouseX in left until right && mouseY in FIELD_TOP until headerBottom
			val headerColor = if (hovered) GlassGui.interactive() else GlassGui.raised()
			RoundedGui.fill(graphics, left, FIELD_TOP, right, headerBottom, COLUMN_RADIUS, headerColor)
			val labelTop = textTop(font, FIELD_TOP, HEADER_HEIGHT)
			DhenType.text(graphics, font, category.displayName, left + CONTENT_PAD, labelTop, DhenPalette.TEXT_PRIMARY)
			val glyph = if (isCollapsed) EXPAND_GLYPH else COLLAPSE_GLYPH
			DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphWidth, labelTop, DhenPalette.TEXT_SECONDARY)
			if (isCollapsed) return
			FlatGui.fill(graphics, left, headerBottom - COLUMN_RADIUS.toInt(), right, headerBottom, headerColor)
			FlatGui.fill(graphics, left + HEADER_RULE_INSET, headerBottom - 1, right - HEADER_RULE_INSET, headerBottom, DhenPalette.accent)
			val viewportBottom = this@ClickGuiShellScreen.height
			var rowTop = headerBottom
			for (row in 0 until visibleCount) {
				if (rowTop >= viewportBottom) return
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

			val nameColor = if (module.enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
			DhenType.text(graphics, font, module.name, left + CONTENT_PAD, rowTop + ROW_TEXT_OFFSET, nameColor)

			val boxRight = right - CONTENT_PAD
			val boxLeft = boxRight - CONTROL_INDICATOR
			val boxTop = rowTop + (ROW_HEIGHT - CONTROL_INDICATOR) / 2
			val boxBottom = boxTop + CONTROL_INDICATOR
			if (module.enabled) FlatGui.fill(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.accent)
			else FlatGui.border(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.BORDER)
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
			FlatGui.fill(graphics, left + 1, top, right - 1, top + areaHeight, GlassGui.canvas())
			val controlsLeft = left + CONTENT_PAD
			var y = top + SETTINGS_PAD
			val list = controls[index]
			for (i in list.indices) {
				val control = list[i]
				if (!control.setting.isVisible) continue
				val hovered = mouseX in controlsLeft until controlsLeft + CONTROLS_WIDTH && mouseY in y until y + CONTROL_HEIGHT
				control.draw(graphics, font, controlsLeft, y, CONTROLS_WIDTH, CONTROL_HEIGHT, hovered)
				y += CONTROL_HEIGHT
			}
		}

		private fun renderableAt(index: Int, localY: Int): SettingControl? {
			if (localY < 0) return null
			val target = localY / CONTROL_HEIGHT
			val list = controls[index]
			var seen = 0
			for (i in list.indices) {
				val control = list[i]
				if (!control.setting.isVisible) continue
				if (seen == target) return control
				seen++
			}
			return null
		}

		private fun heightOf(isCollapsed: Boolean): Int =
			ClickGuiShell.columnHeight(isCollapsed, HEADER_HEIGHT, BODY_PAD, visibleCount, ROW_HEIGHT, settingsHeightAt)

		private fun settingsHeight(index: Int): Int {
			if (modules[index].name !in expanded) return 0
			return 2 * SETTINGS_PAD + renderableCount(index) * CONTROL_HEIGHT
		}

		private fun renderableCount(index: Int): Int {
			val list = controls[index]
			var count = 0
			for (i in list.indices) {
				if (list[i].setting.isVisible) count++
			}
			return count
		}
	}

	private companion object {
		const val CLICK_GUI_TAB = 0
		const val MARGIN = 8
		const val COLUMN_WIDTH = 118
		const val COLUMN_GAP = 8
		const val COLUMN_RADIUS = 6f
		const val HEADER_HEIGHT = 18
		const val HEADER_RULE_INSET = 4
		const val BODY_PAD = 4
		const val ROW_HEIGHT = 13
		const val ROW_TEXT_OFFSET = 3
		const val CONTROL_HEIGHT = 12
		const val SETTINGS_PAD = 3
		const val CONTENT_PAD = 6
		const val CONTROLS_WIDTH = COLUMN_WIDTH - 2 * CONTENT_PAD
		const val TAB_TOP = 6
		const val BAR_HEIGHT = 22
		const val BAR_PAD = 3
		const val TAB_PAD = 10
		const val TAB_GAP = 2
		const val SEARCH_TOP = TAB_TOP + BAR_HEIGHT + 8
		const val SEARCH_WIDTH = 240
		const val SEARCH_HEIGHT = 22
		const val SEARCH_PAD = 10
		const val SEARCH_MAX_LENGTH = 20
		const val FIELD_TOP = SEARCH_TOP + SEARCH_HEIGHT + 12
		const val SCROLL_STEP = 24
		const val SEARCH_PLACEHOLDER = "Search"
		const val NO_MATCH_LABEL = "No matches"
		const val SETTINGS_STUB = "Nothing here yet"
		const val EXPAND_GLYPH = "+"
		const val COLLAPSE_GLYPH = "-"
		val TAB_LABELS = arrayOf("ClickGUI", "Settings")
	}
}
