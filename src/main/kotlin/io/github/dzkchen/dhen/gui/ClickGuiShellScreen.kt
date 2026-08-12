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
	private val prefScroll = ScrollState()
	private val prefCards: List<PrefCard> = ClientPrefs.sections.map(::PrefCard)
	private val prefCardHeightAt = IntUnaryOperator { index -> prefCards[index].height }
	private var query = ""
	private var activeTab = CLICK_GUI_TAB
	private var settled = false
	private var barWidth = 0
	private var glyphWidth = 0
	private var chevronWidth = 0
	private var tooltipText: String? = null
	private var tooltipColumnLeft = 0
	private var tooltipRowTop = 0
	private var dragged: SettingControl? = null
	private var dragLeft = 0
	private var dragWidth = 0
	private var focused: SettingControl? = null
	private var armedClient = false
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
		prefScroll.reclamp(maxPrefScroll())
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
		if (activeTab != CLICK_GUI_TAB) {
			drawClientPrefs(graphics, mouseX, mouseY)
			return
		}
		drawSearch(graphics)
		tooltipText = null
		for (i in visible.indices) {
			val column = visible[i]
			val left = column.contentLeft - scroll.offset
			if (left + COLUMN_WIDTH > 0 && left < width) column.draw(graphics, font, left, mouseX, mouseY)
		}
		drawTooltip(graphics)
	}

	private fun drawClientPrefs(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val left = panelLeft()
		val max = maxPrefScroll()
		val clipped = max > ClickGuiScroll.TOP
		val bottom = FIELD_TOP + columnViewport
		if (clipped) graphics.enableScissor(0, FIELD_TOP, width, bottom)
		var top = FIELD_TOP - prefScroll.offset
		for (i in prefCards.indices) {
			val card = prefCards[i]
			if (top >= bottom) break
			if (top + card.height > FIELD_TOP) card.draw(graphics, font, left, top, bottom, mouseX, mouseY)
			top += card.height + SECTION_GAP
		}
		if (!clipped) return
		graphics.disableScissor()
		drawScrollbar(graphics, left + PANEL_WIDTH, FIELD_TOP, columnViewport, prefScroll.offset, max)
	}

	private fun drawScrollbar(
		graphics: GuiGraphicsExtractor,
		right: Int,
		areaTop: Int,
		areaHeight: Int,
		offset: Int,
		max: Int
	) {
		val trackTop = areaTop + SCROLLBAR_INSET
		val trackHeight = areaHeight - 2 * SCROLLBAR_INSET
		if (trackHeight <= 0) return
		val thumbHeight = ClickGuiScroll.thumbHeight(trackHeight, areaHeight, max, SCROLLBAR_MIN_THUMB)
		val thumbTop = ClickGuiScroll.thumbTop(trackTop, trackHeight, thumbHeight, offset, max)
		val thumbRight = right - SCROLLBAR_INSET
		RoundedGui.pill(graphics, thumbRight - SCROLLBAR_WIDTH, thumbTop, thumbRight, thumbTop + thumbHeight, DhenPalette.accentMuted)
	}

	private fun maxPrefScroll(): Int =
		ClickGuiScroll.maxScroll(FIELD_TOP + ClickGuiShell.spanTotal(prefCards.size, prefCardHeightAt, SECTION_GAP), height, MARGIN)

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
				if (result == ControlKey.COMMITTED) persistArmed()
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
		if (activeTab == CLICK_GUI_TAB && searchContains(x, y)) return true
		if (activeTab != CLICK_GUI_TAB) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressPrefControl(x, y)
			return true
		}
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
			pressRowControl(column, control, contentX)
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
					if (result == ControlKey.COMMITTED) persistArmed()
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
		if (activeTab != CLICK_GUI_TAB || !isPrintable(codepoint)) return super.charTyped(event)
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
		control.drag(event.x().toInt() - dragLeft, dragWidth)
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (dragged != null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val delta = ((scrollY + scrollX) * SCROLL_STEP).roundToInt()
		if (delta == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		if (activeTab != CLICK_GUI_TAB) {
			val max = maxPrefScroll()
			if (max <= ClickGuiScroll.TOP) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
			prefScroll.scrollTo(prefScroll.offset - delta, max)
			return true
		}
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
		glyphWidth = maxOf(DhenType.width(font, EXPAND_GLYPH), DhenType.width(font, COLLAPSE_GLYPH))
		chevronWidth = maxOf(DhenType.width(font, CHEVRON_COLLAPSED), DhenType.width(font, CHEVRON_EXPANDED))
	}

	private fun pressRowControl(column: Column, control: SettingControl, contentX: Int) {
		val controlsLeft = column.contentLeft + CONTENT_PAD
		val press = pressControl(control, contentX - controlsLeft, controlsLeft - scroll.offset, CONTROLS_WIDTH, clientOwned = false)
		if (press == ControlPress.CHANGED) {
			column.reclamp()
			persistModules()
		}
	}

	private fun pressPrefControl(x: Int, y: Int) {
		val control = prefControlAt(x, y) ?: return
		val controlsLeft = panelLeft() + CONTENT_PAD
		val press = pressControl(control, x - controlsLeft, controlsLeft, PANEL_CONTROLS_WIDTH, clientOwned = true)
		if (press == ControlPress.CHANGED) persistClient()
	}

	private fun pressControl(
		control: SettingControl,
		localX: Int,
		screenLeft: Int,
		width: Int,
		clientOwned: Boolean
	): ControlPress {
		val result = control.press(localX, width)
		when (result) {
			ControlPress.TRACK -> {
				dragged = control
				dragLeft = screenLeft
				dragWidth = width
				armedClient = clientOwned
			}
			ControlPress.FOCUS -> {
				focused = control
				armedClient = clientOwned
			}
			else -> Unit
		}
		return result
	}

	private fun prefControlAt(x: Int, y: Int): SettingControl? {
		val controlsLeft = panelLeft() + CONTENT_PAD
		if (x < controlsLeft || x >= controlsLeft + PANEL_CONTROLS_WIDTH) return null
		if (y < FIELD_TOP || y >= FIELD_TOP + columnViewport) return null
		val panelY = y - FIELD_TOP + prefScroll.offset
		val index = ClickGuiShell.spanAt(panelY, prefCards.size, prefCardHeightAt, SECTION_GAP)
		if (index == ClickGuiShell.NONE) return null
		val card = prefCards[index]
		val localY = panelY - ClickGuiShell.spanStart(index, prefCardHeightAt, SECTION_GAP)
		val row = ClickGuiShell.sectionRowAt(localY, HEADER_HEIGHT + SECTION_PAD, card.count, CONTROL_ROW_HEIGHT)
		return if (row == ClickGuiShell.NONE) null else card.control(row)
	}

	private fun persistArmed() {
		if (armedClient) persistClient() else persistModules()
	}

	private fun persistClient() {
		ClientPrefs.sync()
		persistCore()
	}

	private fun toggleCollapsed(column: Column) {
		if (!collapsed.remove(column.category.name)) collapsed.add(column.category.name)
		column.reclamp()
		persistCore()
	}

	private fun backspaceSearch(): Boolean {
		if (activeTab != CLICK_GUI_TAB || query.isEmpty()) return false
		query = query.substring(0, query.length - 1)
		applySearch()
		return true
	}

	private fun collapseExpandedSettings(): Boolean {
		if (activeTab != CLICK_GUI_TAB) return false
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
		val control = dragged ?: return
		dragged = null
		persistArmed()
	}

	private fun blurFocus() {
		val control = focused ?: return
		focused = null
		if (control.blur()) persistArmed()
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

	private fun panelLeft(): Int = ClickGuiShell.centeredLeft(width, PANEL_WIDTH)

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

	private fun drawHeaderBand(graphics: GuiGraphicsExtractor, font: Font, left: Int, right: Int, top: Int, title: String, fill: Int) {
		RoundedGui.fill(graphics, left, top, right, top + HEADER_HEIGHT, COLUMN_RADIUS, fill)
		DhenType.text(graphics, font, title, left + CONTENT_PAD, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_PRIMARY)
	}

	private fun drawHeaderRule(graphics: GuiGraphicsExtractor, left: Int, right: Int, bottom: Int, fill: Int) {
		FlatGui.fill(graphics, left, bottom - COLUMN_RADIUS.toInt(), right, bottom, fill)
		FlatGui.fill(graphics, left + HEADER_RULE_INSET, bottom - 1, right - HEADER_RULE_INSET, bottom, DhenPalette.accent)
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

	private inner class PrefCard(section: PrefSection) {
		private val title = section.title
		private val controls: List<SettingControl> = section.settings.mapNotNull(::controlFor)

		val count: Int
			get() {
				var visible = 0
				for (i in controls.indices) {
					if (controls[i].setting.isVisible) visible++
				}
				return visible
			}
		val height: Int
			get() {
				val shown = count
				return HEADER_HEIGHT + 2 * SECTION_PAD + if (shown == 0) EMPTY_SECTION_HEIGHT else shown * CONTROL_ROW_HEIGHT
			}

		fun control(row: Int): SettingControl? {
			var seen = 0
			for (i in controls.indices) {
				val control = controls[i]
				if (!control.setting.isVisible) continue
				if (seen == row) return control
				seen++
			}
			return null
		}

		fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, top: Int, bottom: Int, mouseX: Int, mouseY: Int) {
			val right = left + PANEL_WIDTH
			val headerBottom = top + HEADER_HEIGHT
			val fill = GlassGui.raised()
			GlassGui.roundedFrame(graphics, left, top, right, top + height, COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
			drawHeaderBand(graphics, font, left, right, top, title, fill)
			drawHeaderRule(graphics, left, right, headerBottom, fill)
			val contentLeft = left + CONTENT_PAD
			var y = headerBottom + SECTION_PAD
			if (count == 0) {
				DhenType.text(graphics, font, EMPTY_SECTION_LABEL, contentLeft, textTop(font, y, EMPTY_SECTION_HEIGHT), DhenPalette.TEXT_DISABLED)
				return
			}
			val overControls = mouseX in contentLeft until contentLeft + PANEL_CONTROLS_WIDTH
			for (i in controls.indices) {
				val control = controls[i]
				if (!control.setting.isVisible) continue
				if (y >= bottom) break
				val hovered = overControls && mouseY in y until y + CONTROL_ROW_HEIGHT
				control.draw(graphics, font, contentLeft, y, PANEL_CONTROLS_WIDTH, CONTROL_ROW_HEIGHT, hovered)
				y += CONTROL_ROW_HEIGHT
			}
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
			drawHeaderBand(graphics, font, left, right, FIELD_TOP, category.displayName, headerColor)
			val glyph = if (collapsedNow) EXPAND_GLYPH else COLLAPSE_GLYPH
			DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphWidth, textTop(font, FIELD_TOP, HEADER_HEIGHT), DhenPalette.TEXT_SECONDARY)
			if (collapsedNow || bottom <= BODY_TOP) return
			drawHeaderRule(graphics, left, right, BODY_TOP, headerColor)
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
				drawScrollbar(graphics, right, BODY_TOP, shown - HEADER_HEIGHT, scroll.offset, max)
			}
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
		const val SEARCH_TOP = TAB_TOP + BAR_HEIGHT + 8
		const val SEARCH_WIDTH = 240
		const val SEARCH_HEIGHT = 22
		const val SEARCH_PAD = 10
		const val SEARCH_MAX_LENGTH = 20
		const val FIELD_TOP = SEARCH_TOP + SEARCH_HEIGHT + 12
		const val BODY_TOP = FIELD_TOP + HEADER_HEIGHT
		const val SCROLL_STEP = 24
		const val PANEL_WIDTH = SEARCH_WIDTH
		const val PANEL_CONTROLS_WIDTH = PANEL_WIDTH - 2 * CONTENT_PAD
		const val SECTION_GAP = 6
		const val SECTION_PAD = 4
		const val EMPTY_SECTION_HEIGHT = ROW_HEIGHT
		const val SEARCH_PLACEHOLDER = "Search"
		const val NO_MATCH_LABEL = "No matches"
		const val EMPTY_SECTION_LABEL = "Nothing here yet"
		const val EXPAND_GLYPH = "+"
		const val COLLAPSE_GLYPH = "-"
		const val CHEVRON_COLLAPSED = "›"
		const val CHEVRON_EXPANDED = "⌄"
		val TAB_LABELS = arrayOf("ClickGUI", "Settings")
	}
}
