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
	private var chipWidth = 0
	private var glyphWidth = 0
	private var chevronWidth = 0
	private var tooltipText: String? = null
	private var tooltipColumnLeft = 0
	private var tooltipRowTop = 0
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
		drawChip(graphics, mouseX, mouseY)
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
		tooltipText = null
		for (i in visible.indices) {
			val column = visible[i]
			val left = column.contentLeft - scroll.offset
			if (left + COLUMN_WIDTH > 0 && left < width) column.draw(graphics, font, left, mouseX, mouseY)
		}
		drawTooltip(graphics)
	}

	private fun drawTooltip(graphics: GuiGraphicsExtractor) {
		val text = tooltipText ?: return
		val measured = DhenType.width(font, text) + 2 * TOOLTIP_PAD
		val boxWidth = minOf(measured, width - 2 * MARGIN)
		val boxHeight = DhenType.lineHeight(font) + 2 * TOOLTIP_PAD
		val left = ClickGuiShell.tooltipLeft(tooltipColumnLeft, COLUMN_WIDTH, boxWidth, width, TOOLTIP_GAP, MARGIN)
		val top = ClickGuiShell.tooltipTop(tooltipRowTop, boxHeight, height, MARGIN)
		val right = left + boxWidth
		val bottom = top + boxHeight
		val truncated = measured > boxWidth
		GlassGui.roundedFrame(graphics, left, top, right, bottom, TOOLTIP_RADIUS, GlassGui.raised(), DhenPalette.BORDER)
		if (truncated) graphics.enableScissor(left, top, right, bottom)
		DhenType.text(graphics, font, text, left + TOOLTIP_PAD, top + TOOLTIP_PAD, DhenPalette.TEXT_SECONDARY)
		if (truncated) graphics.disableScissor()
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
		if (chipContains(x, y)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				Effects.reduced = !Effects.reduced
				persistCore()
			}
			return true
		}
		val tab = tabAt(x, y)
		if (tab != ClickGuiShell.NONE) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) activeTab = tab
			return true
		}
		if (searchContains(x, y)) return true
		if (activeTab != CLICK_GUI_TAB) return true
		val contentX = x + scroll.offset
		val column = columnAt(contentX, y) ?: return true
		if (y < BODY_TOP) {
			toggleCollapsed(column)
			return true
		}
		val module = column.rowAt(y)
		if (module != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !column.chevronContains(contentX)) manager.toggle(module)
			else {
				if (!expanded.remove(module.name)) expanded.add(module.name)
				column.reclamp()
			}
			return true
		}
		val control = column.controlAt(contentX, y)
		if (control != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressControl(column, control, contentX)
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
		val delta = ((scrollY + scrollX) * SCROLL_STEP).roundToInt()
		if (delta == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val x = mouseX.toInt()
		val y = mouseY.toInt()
		if (y >= BODY_TOP && columnAt(x + scroll.offset, y)?.scrollBy(delta) == true) return true
		val max = maxScroll()
		if (max <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		scroll.scrollTo(scroll.offset - delta, max)
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
		chipWidth = 2 * CHIP_PAD + DhenType.width(font, EFFECTS_LABEL) + CHIP_GAP + 2 * INDICATOR_RADIUS
		glyphWidth = maxOf(DhenType.width(font, EXPAND_GLYPH), DhenType.width(font, COLLAPSE_GLYPH))
		chevronWidth = maxOf(DhenType.width(font, CHEVRON_COLLAPSED), DhenType.width(font, CHEVRON_EXPANDED))
	}

	private fun pressControl(column: Column, control: SettingControl, contentX: Int) {
		val controlsLeft = column.contentLeft + CONTENT_PAD
		when (control.press(contentX - controlsLeft, CONTROLS_WIDTH)) {
			ControlPress.TRACK -> {
				dragged = control
				dragLeft = controlsLeft
			}
			ControlPress.CHANGED -> {
				column.reclamp()
				persistModules()
			}
			ControlPress.FOCUS -> focused = control
			else -> Unit
		}
	}

	private fun toggleCollapsed(column: Column) {
		if (!collapsed.remove(column.category.name)) collapsed.add(column.category.name)
		column.reclamp()
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

	private val columnViewport: Int
		get() = height - FIELD_TOP - MARGIN

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

	private fun chipLeft(): Int = ClickGuiShell.rightAlignedLeft(width, chipWidth, MARGIN)

	private fun chipContains(x: Int, y: Int): Boolean {
		val left = chipLeft()
		return x >= left && x < left + chipWidth && y >= TAB_TOP && y < TAB_TOP + BAR_HEIGHT
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

	private fun drawChip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val left = chipLeft()
		val right = left + chipWidth
		val bottom = TAB_TOP + BAR_HEIGHT
		val fill = if (chipContains(mouseX, mouseY)) GlassGui.interactive() else GlassGui.raised()
		GlassGui.roundedFrame(graphics, left, TAB_TOP, right, bottom, RoundedQuad.FULL, fill, DhenPalette.BORDER)
		val labelColor = if (Effects.reduced) DhenPalette.TEXT_SECONDARY else DhenPalette.TEXT_PRIMARY
		DhenType.text(graphics, font, EFFECTS_LABEL, left + CHIP_PAD, textTop(font, TAB_TOP, BAR_HEIGHT), labelColor)
		val dotX = right - CHIP_PAD - INDICATOR_RADIUS
		val dotY = TAB_TOP + BAR_HEIGHT / 2
		if (Effects.reduced) RoundedGui.circleBorder(graphics, dotX, dotY, INDICATOR_RADIUS, 1f, DhenPalette.BORDER)
		else RoundedGui.circle(graphics, dotX, dotY, INDICATOR_RADIUS, DhenPalette.accent)
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
		private val scroll = ScrollState()

		var contentLeft = 0

		val hidden: Boolean
			get() = query.isNotEmpty() && visibleCount == 0
		val height: Int
			get() = ClickGuiShell.clampedColumnHeight(naturalHeight, HEADER_HEIGHT, columnViewport)

		private val isCollapsed: Boolean
			get() = category.name in collapsed
		private val naturalHeight: Int
			get() = ClickGuiShell.columnHeight(isCollapsed, HEADER_HEIGHT, BODY_PAD, visibleCount, ROW_HEIGHT, settingsHeightAt)
		private val maxScroll: Int
			get() = naturalHeight - height

		fun applyFilter(query: String) {
			var count = 0
			for (i in modules.indices) {
				val module = modules[i]
				if (!ClickGuiSearch.matches(query, module.name, module.description)) continue
				visibleRows[count] = i
				count++
			}
			visibleCount = count
			scroll.refilter(maxScroll)
		}

		fun reclamp() = scroll.reclamp(maxScroll)

		fun scrollBy(delta: Int): Boolean {
			val max = maxScroll
			if (max <= ClickGuiScroll.TOP) return false
			scroll.scrollTo(scroll.offset - delta, max)
			return true
		}

		fun rowAt(y: Int): Module? {
			val localY = bodyLocal(y) ?: return null
			val row = ClickGuiRows.rowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt) ?: return null
			return modules[visibleRows[row]]
		}

		fun chevronContains(contentX: Int): Boolean =
			contentX >= contentLeft + COLUMN_WIDTH - CONTENT_PAD - chevronWidth - CHEVRON_HIT_SLOP

		fun controlAt(contentX: Int, y: Int): SettingControl? {
			if (contentX < contentLeft + CONTENT_PAD || contentX >= contentLeft + CONTENT_PAD + CONTROLS_WIDTH) return null
			val localY = bodyLocal(y) ?: return null
			val row = ClickGuiRows.settingsRowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt) ?: return null
			val bandTop = ClickGuiRows.settingsTop(row, ROW_HEIGHT, settingsHeightAt)
			return renderableAt(visibleRows[row], localY - bandTop - SETTINGS_PAD)
		}

		fun collapseSettings(): Boolean {
			var changed = false
			for (row in 0 until visibleCount) {
				if (expanded.remove(modules[visibleRows[row]].name)) changed = true
			}
			if (changed) reclamp()
			return changed
		}

		private fun bodyLocal(y: Int): Int? {
			if (y < BODY_TOP || y >= FIELD_TOP + height) return null
			return y - BODY_TOP + scroll.offset
		}

		fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, mouseX: Int, mouseY: Int) {
			val right = left + COLUMN_WIDTH
			val collapsedNow = isCollapsed
			val natural = naturalHeight
			val shown = ClickGuiShell.clampedColumnHeight(natural, HEADER_HEIGHT, columnViewport)
			val bottom = FIELD_TOP + shown
			val overColumn = mouseX in left until right
			GlassGui.roundedFrame(graphics, left, FIELD_TOP, right, bottom, COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
			val headerColor = if (overColumn && mouseY in FIELD_TOP until BODY_TOP) GlassGui.interactive() else GlassGui.raised()
			RoundedGui.fill(graphics, left, FIELD_TOP, right, BODY_TOP, COLUMN_RADIUS, headerColor)
			val labelTop = textTop(font, FIELD_TOP, HEADER_HEIGHT)
			DhenType.text(graphics, font, category.displayName, left + CONTENT_PAD, labelTop, DhenPalette.TEXT_PRIMARY)
			val glyph = if (collapsedNow) EXPAND_GLYPH else COLLAPSE_GLYPH
			DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphWidth, labelTop, DhenPalette.TEXT_SECONDARY)
			if (collapsedNow || bottom <= BODY_TOP) return
			FlatGui.fill(graphics, left, BODY_TOP - COLUMN_RADIUS.toInt(), right, BODY_TOP, headerColor)
			FlatGui.fill(graphics, left + HEADER_RULE_INSET, BODY_TOP - 1, right - HEADER_RULE_INSET, BODY_TOP, DhenPalette.accent)
			val max = natural - shown
			val clipped = max > ClickGuiScroll.TOP
			val pointerY = if (overColumn && mouseY in BODY_TOP until bottom) mouseY else NO_POINTER
			if (clipped) graphics.enableScissor(left + 1, BODY_TOP, right - 1, bottom)
			var rowTop = BODY_TOP - scroll.offset
			for (row in 0 until visibleCount) {
				if (rowTop >= bottom) break
				val index = visibleRows[row]
				val areaHeight = settingsHeight(index)
				val nextTop = rowTop + ROW_HEIGHT + areaHeight
				if (nextTop > BODY_TOP) {
					drawRow(graphics, font, modules[index], left, rowTop, pointerY)
					if (areaHeight > 0) drawSettings(graphics, font, index, left, rowTop + ROW_HEIGHT, areaHeight, bottom, mouseX, pointerY)
				}
				rowTop = nextTop
			}
			if (clipped) {
				graphics.disableScissor()
				drawScrollbar(graphics, right, bottom, shown - HEADER_HEIGHT, max)
			}
		}

		private fun drawScrollbar(graphics: GuiGraphicsExtractor, right: Int, bottom: Int, viewport: Int, max: Int) {
			val trackTop = BODY_TOP + SCROLLBAR_INSET
			val trackHeight = bottom - SCROLLBAR_INSET - trackTop
			if (trackHeight <= 0) return
			val thumbHeight = ClickGuiScroll.thumbHeight(trackHeight, viewport, max, SCROLLBAR_MIN_THUMB)
			val thumbTop = ClickGuiScroll.thumbTop(trackTop, trackHeight, thumbHeight, scroll.offset, max)
			val thumbRight = right - SCROLLBAR_INSET
			RoundedGui.pill(graphics, thumbRight - SCROLLBAR_WIDTH, thumbTop, thumbRight, thumbTop + thumbHeight, DhenPalette.accentMuted)
		}

		private fun drawRow(
			graphics: GuiGraphicsExtractor,
			font: Font,
			module: Module,
			left: Int,
			rowTop: Int,
			pointerY: Int
		) {
			val right = left + COLUMN_WIDTH
			val rowBottom = rowTop + ROW_HEIGHT
			val hovered = pointerY in rowTop until rowBottom
			if (hovered) FlatGui.fill(graphics, left + 1, rowTop, right - 1, rowBottom, GlassGui.interactive())
			if (module.enabled) FlatGui.fill(graphics, left + 1, rowTop, left + 1 + ROW_RAIL_WIDTH, rowBottom, DhenPalette.accent)

			val nameColor = when {
				module.enabled -> DhenPalette.accent
				hovered -> DhenPalette.TEXT_PRIMARY
				else -> DhenPalette.TEXT_SECONDARY
			}
			val labelTop = textTop(font, rowTop, ROW_HEIGHT)
			val nameLeft = left + ClickGuiShell.centeredLeft(COLUMN_WIDTH, DhenType.width(font, module.name))
			DhenType.text(graphics, font, module.name, nameLeft, labelTop, nameColor)

			val chevron = if (module.name in expanded) CHEVRON_EXPANDED else CHEVRON_COLLAPSED
			val chevronColor = if (hovered) DhenPalette.TEXT_SECONDARY else DhenPalette.TEXT_DISABLED
			DhenType.text(graphics, font, chevron, right - CONTENT_PAD - chevronWidth, labelTop, chevronColor)

			if (hovered && module.description.isNotEmpty()) {
				tooltipText = module.description
				tooltipColumnLeft = left
				tooltipRowTop = rowTop
			}
		}

		private fun drawSettings(
			graphics: GuiGraphicsExtractor,
			font: Font,
			index: Int,
			left: Int,
			top: Int,
			areaHeight: Int,
			bottom: Int,
			mouseX: Int,
			pointerY: Int
		) {
			FlatGui.fill(graphics, left + 1, top, left + COLUMN_WIDTH - 1, top + areaHeight, GlassGui.canvas())
			val controlsLeft = left + CONTENT_PAD
			val overControls = mouseX in controlsLeft until controlsLeft + CONTROLS_WIDTH
			var y = top + SETTINGS_PAD
			val list = controls[index]
			for (i in list.indices) {
				val control = list[i]
				if (!control.setting.isVisible) continue
				if (y >= bottom) break
				if (y + CONTROL_ROW_HEIGHT > BODY_TOP) {
					val hovered = overControls && pointerY in y until y + CONTROL_ROW_HEIGHT
					control.draw(graphics, font, controlsLeft, y, CONTROLS_WIDTH, CONTROL_ROW_HEIGHT, hovered)
				}
				y += CONTROL_ROW_HEIGHT
			}
		}

		private fun renderableAt(index: Int, localY: Int): SettingControl? {
			if (localY < 0) return null
			val target = localY / CONTROL_ROW_HEIGHT
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

		private fun settingsHeight(index: Int): Int {
			if (modules[index].name !in expanded) return 0
			return 2 * SETTINGS_PAD + renderableCount(index) * CONTROL_ROW_HEIGHT
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
		const val BODY_PAD = 6
		const val ROW_HEIGHT = 13
		const val ROW_RAIL_WIDTH = 2
		const val CHEVRON_HIT_SLOP = 4
		const val SCROLLBAR_WIDTH = 2
		const val SCROLLBAR_INSET = 3
		const val SCROLLBAR_MIN_THUMB = 12
		const val TOOLTIP_PAD = 5
		const val TOOLTIP_GAP = 6
		const val TOOLTIP_RADIUS = 4f
		const val NO_POINTER = Int.MIN_VALUE
		const val SETTINGS_PAD = 3
		const val CONTENT_PAD = 6
		const val CONTROLS_WIDTH = COLUMN_WIDTH - 2 * CONTENT_PAD
		const val TAB_TOP = 6
		const val BAR_HEIGHT = 22
		const val BAR_PAD = 3
		const val TAB_PAD = 10
		const val TAB_GAP = 2
		const val CHIP_PAD = 10
		const val CHIP_GAP = 6
		const val INDICATOR_RADIUS = 3
		const val SEARCH_TOP = TAB_TOP + BAR_HEIGHT + 8
		const val SEARCH_WIDTH = 240
		const val SEARCH_HEIGHT = 22
		const val SEARCH_PAD = 10
		const val SEARCH_MAX_LENGTH = 20
		const val FIELD_TOP = SEARCH_TOP + SEARCH_HEIGHT + 12
		const val BODY_TOP = FIELD_TOP + HEADER_HEIGHT
		const val SCROLL_STEP = 24
		const val SEARCH_PLACEHOLDER = "Search"
		const val NO_MATCH_LABEL = "No matches"
		const val SETTINGS_STUB = "Nothing here yet"
		const val EFFECTS_LABEL = "Effects"
		const val EXPAND_GLYPH = "+"
		const val COLLAPSE_GLYPH = "-"
		const val CHEVRON_COLLAPSED = "›"
		const val CHEVRON_EXPANDED = "⌄"
		val TAB_LABELS = arrayOf("ClickGUI", "Settings")
	}
}
