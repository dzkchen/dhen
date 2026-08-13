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
	private val view: ClickGuiState,
	private val persistCore: () -> Unit,
	private val persistModules: () -> Unit,
	private val parent: Screen? = null
) : Screen(Component.literal("Dhen")) {
	private val openedAt = Util.getMillis()
	private val columns = mutableListOf<Column>()
	private val visible = mutableListOf<Column>()
	private val expanded = mutableSetOf<String>()
	private val tabWidths = IntArray(TAB_LABELS.size)
	private val prefCards: List<PrefCard> = ClientPrefs.sections.map(::PrefCard)
	private val tabLefts = IntArray(TAB_LABELS.size)
	private val columnField = ScrollingStack(MARGIN, COLUMN_GAP, MARGIN, { visible.size }, { width }, { COLUMN_WIDTH })
	private val prefs = ScrollingStack(FIELD_TOP, SECTION_GAP, MARGIN, { prefCards.size }, { height }, { index -> prefCards[index].height })
	private val navRowsAt = IntUnaryOperator { index -> visible[index].navCount }
	private var navFocus: Module? = null
	private var query = ""
	private var activeTab = FEATURES_TAB
	private var previousTab = FEATURES_TAB
	private var tabSwitchedAt = 0L
	private var tabSettled = true
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
		prefs.reclamp()
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
		settled = entry >= GlassGui.SETTLED
		shifted(graphics, 0f, GlassGui.offset(entry, GlassGui.ENTRY_RISE)) { drawContent(graphics, mouseX, mouseY) }
		if (!settled) GlassGui.veil(graphics, width, height, entry)
	}

	private fun drawContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		if (tabSettled) {
			drawTabs(graphics, GlassGui.SETTLED)
			drawTabBody(graphics, mouseX, mouseY)
			return
		}
		val switch = GlassGui.tabProgress(tabSwitchedAt)
		tabSettled = switch >= GlassGui.SETTLED
		drawTabs(graphics, switch)
		shifted(graphics, GlassGui.offset(switch, tabTravel()), 0f) { drawTabBody(graphics, mouseX, mouseY) }
	}

	private fun tabTravel(): Float =
		if (activeTab > previousTab) GlassGui.TAB_SLIDE else -GlassGui.TAB_SLIDE

	private inline fun shifted(graphics: GuiGraphicsExtractor, dx: Float, dy: Float, body: () -> Unit) {
		if (dx == 0f && dy == 0f) {
			body()
			return
		}
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(dx, dy)
		body()
		pose.popMatrix()
	}

	private fun drawTabBody(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		if (activeTab != FEATURES_TAB) {
			drawClientPrefs(graphics, mouseX, mouseY)
			return
		}
		drawSearch(graphics)
		tooltipText = null
		drawColumns(graphics, mouseX, mouseY)
		drawTooltip(graphics)
	}

	private fun drawColumns(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		for (i in visible.indices) {
			val column = visible[i]
			val left = column.contentLeft - columnField.offset
			if (left + COLUMN_WIDTH > 0 && left < width) {
				column.draw(graphics, font, left, mouseX, mouseY)
			}
		}
	}

	private fun drawClientPrefs(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val left = panelLeft()
		val max = prefs.max()
		val clipped = max > ClickGuiScroll.TOP
		val bottom = fieldBottom
		if (clipped) graphics.enableScissor(0, FIELD_TOP, width, bottom)
		var top = FIELD_TOP - prefs.offset
		for (i in prefCards.indices) {
			val card = prefCards[i]
			if (top >= bottom) break
			if (top + card.height > FIELD_TOP) card.draw(graphics, font, left, top, bottom, mouseX, mouseY)
			top += card.height + SECTION_GAP
		}
		if (!clipped) return
		graphics.disableScissor()
		drawScrollbar(graphics, left + PANEL_WIDTH, FIELD_TOP, columnViewport, prefs.offset, max)
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
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) switchTab(tab)
			return true
		}
		if (activeTab == FEATURES_TAB && searchContains(x, y)) return true
		if (activeTab != FEATURES_TAB) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressPrefControl(x, y)
			return true
		}
		val slot = columnSlotAt(x, y)
		if (slot == ClickGuiShell.NONE) return true
		val column = visible[slot]
		if (y < FIELD_TOP + HEADER_HEIGHT) {
			toggleHeader(column)
			return true
		}
		val contentX = x + columnField.offset
		val module = column.rowAt(y)
		if (module != null) {
			navFocus = module
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
		if (activeTab == FEATURES_TAB && clickGuiKey(event.key())) return true
		return super.keyPressed(event)
	}

	private fun clickGuiKey(key: Int): Boolean = when (key) {
		GLFW.GLFW_KEY_BACKSPACE -> backspaceSearch()
		GLFW.GLFW_KEY_ESCAPE -> collapseExpandedSettings()
		GLFW.GLFW_KEY_UP -> moveFocus(-1)
		GLFW.GLFW_KEY_DOWN -> moveFocus(1)
		GLFW.GLFW_KEY_LEFT -> jumpColumn(-1)
		GLFW.GLFW_KEY_RIGHT -> jumpColumn(1)
		GLFW.GLFW_KEY_TAB -> toggleExpandFocus()
		GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> toggleFocus()
		else -> false
	}

	private fun switchTab(tab: Int) {
		if (tab == activeTab) return
		previousTab = activeTab
		activeTab = tab
		tabSwitchedAt = Util.getMillis()
		tabSettled = false
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		if (event.key() == swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (focused?.charTyped(codepoint) == true) return true
		if (swallowCharKey != GLFW.GLFW_KEY_UNKNOWN) return true
		if (activeTab != FEATURES_TAB || !isPrintable(codepoint)) return super.charTyped(event)
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
		if (activeTab != FEATURES_TAB) {
			return prefs.scrollBy(delta) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		}
		val x = mouseX.toInt()
		val y = mouseY.toInt()
		val slot = columnSlotAt(x, y)
		if (slot != ClickGuiShell.NONE && y >= FIELD_TOP + HEADER_HEIGHT && visible[slot].scrollBy(delta)) return true
		if (columnField.scrollBy(delta)) return true
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (dragged == null) return super.mouseReleased(event)
		cancelDrag()
		return true
	}

	private fun measureChrome() {
		for (i in TAB_LABELS.indices) tabWidths[i] = DhenType.width(font, TAB_LABELS[i]) + 2 * TAB_PAD
		barWidth = 2 * BAR_PAD + ClickGuiShell.segmentsWidth(tabWidths, TAB_GAP)
		for (i in TAB_LABELS.indices) tabLefts[i] = BAR_PAD + ClickGuiShell.segmentStart(i, tabWidths, TAB_GAP)
		glyphWidth = maxOf(DhenType.width(font, EXPAND_GLYPH), DhenType.width(font, COLLAPSE_GLYPH))
		chevronWidth = maxOf(DhenType.width(font, CHEVRON_COLLAPSED), DhenType.width(font, CHEVRON_EXPANDED))
	}

	private fun pressRowControl(column: Column, control: SettingControl, contentX: Int) {
		val controlsLeft = column.contentLeft + CONTENT_PAD
		val press = pressControl(control, contentX - controlsLeft, controlsLeft - columnField.offset, CONTROLS_WIDTH, clientOwned = false)
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
		if (y !in FIELD_TOP..<fieldBottom) return null
		val index = prefs.slotAt(y)
		if (index == ClickGuiShell.NONE) return null
		val card = prefCards[index]
		val localY = y - prefs.originOf(index)
		val row = ClickGuiShell.sectionRowAt(localY, HEADER_HEIGHT + SECTION_PAD, card.count, CONTROL_ROW_HEIGHT)
		return if (row == ClickGuiShell.NONE) null else card.control(row)
	}

	private fun persistArmed() {
		if (armedClient) {
			persistClient()
			return
		}
		reflowAll()
		persistModules()
	}

	private fun persistClient() {
		ClientPrefs.sync()
		relayout()
		persistCore()
	}

	private fun toggleHeader(column: Column) {
		view.toggle(column.category.name)
		column.reclamp()
		refreshFocus()
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
		columnField.refilter()
		refreshFocus()
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

	private fun relayout() {
		layout()
		columnField.reclamp()
	}

	private fun moveFocus(delta: Int): Boolean {
		val flat = ClickGuiNav.step(focusFlat(), delta, ClickGuiNav.total(visible.size, navRowsAt))
		val slot = ClickGuiNav.columnOf(flat, visible.size, navRowsAt)
		if (slot == ClickGuiShell.NONE) navFocus = null
		else focusAt(slot, flat - ClickGuiNav.flatOf(slot, 0, navRowsAt))
		return true
	}

	private fun jumpColumn(delta: Int): Boolean {
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return moveFocus(delta)
		val next = ClickGuiNav.columnStep(slot, delta, visible.size, navRowsAt)
		if (next == ClickGuiShell.NONE) return true
		focusAt(next, minOf(visible[slot].rowOf(navFocus), visible[next].navCount - 1))
		return true
	}

	private fun toggleFocus(): Boolean {
		val module = navFocus ?: return false
		manager.toggle(module)
		return true
	}

	private fun toggleExpandFocus(): Boolean {
		val module = navFocus ?: return false
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return false
		val row = visible[slot].rowOf(module)
		if (!expanded.remove(module.name)) expanded.add(module.name)
		visible[slot].reclamp()
		revealFocus(slot, row)
		return true
	}

	private fun focusAt(slot: Int, row: Int) {
		navFocus = visible[slot].moduleAt(row)
		revealFocus(slot, row)
	}

	private fun refreshFocus() {
		if (navFocus == null || focusSlot() != ClickGuiShell.NONE) return
		val slot = ClickGuiNav.columnOf(0, visible.size, navRowsAt)
		navFocus = if (slot == ClickGuiShell.NONE) null else visible[slot].moduleAt(0)
	}

	private fun focusSlot(): Int {
		val module = navFocus ?: return ClickGuiShell.NONE
		for (i in visible.indices) {
			if (visible[i].rowOf(module) != ClickGuiShell.NONE) return i
		}
		return ClickGuiShell.NONE
	}

	private fun focusFlat(): Int {
		val slot = focusSlot()
		if (slot == ClickGuiShell.NONE) return ClickGuiShell.NONE
		return ClickGuiNav.flatOf(slot, visible[slot].rowOf(navFocus), navRowsAt)
	}

	private fun revealFocus(slot: Int, row: Int) {
		columnField.reveal(slot)
		visible[slot].revealRow(row)
	}

	private fun reflowAll() {
		for (i in visible.indices) visible[i].reclamp()
	}

	private fun cancelDrag() {
		if (dragged == null) return
		dragged = null
		persistArmed()
	}

	private fun blurFocus() {
		val control = focused ?: return
		focused = null
		if (control.blur()) persistArmed()
	}

	private val fieldBottom: Int
		get() = height - MARGIN

	private val columnViewport: Int
		get() = fieldBottom - FIELD_TOP

	private fun columnSlotAt(x: Int, y: Int): Int {
		if (y !in FIELD_TOP..<fieldBottom) return ClickGuiShell.NONE
		val slot = columnField.slotAt(x)
		if (slot == ClickGuiShell.NONE) return ClickGuiShell.NONE
		return if (y < FIELD_TOP + visible[slot].height) slot else ClickGuiShell.NONE
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

	private fun drawTabs(graphics: GuiGraphicsExtractor, switch: Float) {
		val barLeft = barLeft()
		val bottom = TAB_TOP + BAR_HEIGHT
		GlassGui.roundedFrame(graphics, barLeft, TAB_TOP, barLeft + barWidth, bottom, RoundedQuad.FULL, GlassGui.raised(), DhenPalette.BORDER)
		val pillLeft = barLeft + RoundedQuad.between(tabLefts[previousTab], tabLefts[activeTab], switch)
		val pillWidth = RoundedQuad.between(tabWidths[previousTab], tabWidths[activeTab], switch)
		RoundedGui.pill(graphics, pillLeft, TAB_TOP + BAR_PAD, pillLeft + pillWidth, bottom - BAR_PAD, DhenPalette.accent)
		val top = textTop(font, TAB_TOP, BAR_HEIGHT)
		for (i in TAB_LABELS.indices) {
			val color = DhenPalette.mix(tabLabelColor(i, previousTab), tabLabelColor(i, activeTab), switch)
			DhenType.text(graphics, font, TAB_LABELS[i], barLeft + tabLefts[i] + TAB_PAD, top, color)
		}
	}

	private fun tabLabelColor(tab: Int, active: Int): Int =
		if (tab == active) DhenPalette.accentForeground else DhenPalette.TEXT_SECONDARY

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
		val navCount: Int
			get() = if (isCollapsed) 0 else visibleCount
		val height: Int
			get() = shownHeight(naturalHeight)
		val naturalHeight: Int
			get() = ClickGuiShell.columnHeight(isCollapsed, HEADER_HEIGHT, BODY_PAD, visibleCount, ROW_HEIGHT, settingsHeightAt)

		private val isCollapsed: Boolean
			get() = view.isBodyHidden(category.name)
		private val maxScroll: Int
			get() = naturalHeight.let { it - shownHeight(it) }

		private fun shownHeight(natural: Int): Int =
			ClickGuiShell.clampedColumnHeight(natural, HEADER_HEIGHT, columnViewport)

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

		fun moduleAt(row: Int): Module = modules[visibleRows[row]]

		fun rowOf(module: Module?): Int {
			if (module == null || isCollapsed) return ClickGuiShell.NONE
			for (row in 0 until visibleCount) {
				if (modules[visibleRows[row]] === module) return row
			}
			return ClickGuiShell.NONE
		}

		private fun rowTop(row: Int): Int = ClickGuiRows.rowTop(row, ROW_HEIGHT, settingsHeightAt)

		private fun rowExtent(row: Int): Int = ROW_HEIGHT + settingsHeight(visibleRows[row])

		fun revealRow(row: Int) = scroll.reveal(rowTop(row), rowExtent(row), height - HEADER_HEIGHT, maxScroll)

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
			val bodyTop = FIELD_TOP + HEADER_HEIGHT
			if (y < bodyTop || y >= FIELD_TOP + height) return null
			return y - bodyTop + scroll.offset
		}

		fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, mouseX: Int, mouseY: Int) {
			val right = left + COLUMN_WIDTH
			val collapsedNow = isCollapsed
			val top = FIELD_TOP
			val natural = naturalHeight
			val shown = shownHeight(natural)
			val bodyTop = top + HEADER_HEIGHT
			val bottom = top + shown
			val overColumn = mouseX in left until right
			GlassGui.roundedFrame(graphics, left, top, right, bottom, COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
			val headerColor = if (overColumn && mouseY in top until bodyTop) GlassGui.interactive() else GlassGui.raised()
			drawHeaderBand(graphics, font, left, right, top, category.displayName, headerColor)
			val glyph = if (collapsedNow) EXPAND_GLYPH else COLLAPSE_GLYPH
			DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphWidth, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_SECONDARY)
			if (collapsedNow || bottom <= bodyTop) return
			drawHeaderRule(graphics, left, right, bodyTop, headerColor)
			val max = natural - shown
			val clipped = max > ClickGuiScroll.TOP
			val visibleTop = maxOf(bodyTop, FIELD_TOP)
			val visibleBottom = minOf(bottom, fieldBottom)
			val pointerY = if (overColumn && mouseY in visibleTop until visibleBottom) mouseY else NO_POINTER
			if (clipped) graphics.enableScissor(left + 1, bodyTop, right - 1, bottom)
			var rowTop = bodyTop - scroll.offset
			for (row in 0 until visibleCount) {
				if (rowTop >= visibleBottom) break
				val index = visibleRows[row]
				val areaHeight = settingsHeight(index)
				val nextTop = rowTop + ROW_HEIGHT + areaHeight
				if (nextTop > visibleTop) {
					drawRow(graphics, font, modules[index], left, rowTop, pointerY)
					if (areaHeight > 0) {
						drawSettings(graphics, font, index, left, rowTop + ROW_HEIGHT, areaHeight, visibleTop, visibleBottom, mouseX, pointerY)
					}
				}
				rowTop = nextTop
			}
			if (clipped) {
				graphics.disableScissor()
				drawScrollbar(graphics, right, bodyTop, shown - HEADER_HEIGHT, scroll.offset, max)
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
			val navigated = module === navFocus
			val active = hovered || navigated
			if (active) FlatGui.fill(graphics, left + 1, rowTop, right - 1, rowBottom, GlassGui.interactive())
			if (module.enabled) FlatGui.fill(graphics, left + 1, rowTop, left + 1 + ROW_RAIL_WIDTH, rowBottom, DhenPalette.accent)
			if (navigated) {
				RoundedGui.border(graphics, left + 1, rowTop, right - 1, rowBottom, FOCUS_RADIUS, RoundedGui.HAIRLINE, DhenPalette.accent)
			}

			val nameColor = when {
				module.enabled -> DhenPalette.accent
				active -> DhenPalette.TEXT_PRIMARY
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
			visibleTop: Int,
			visibleBottom: Int,
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
				if (y >= visibleBottom) break
				if (y + CONTROL_ROW_HEIGHT > visibleTop) {
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
		const val FEATURES_TAB = 0
		const val MARGIN = 8
		const val COLUMN_WIDTH = 118
		const val COLUMN_GAP = 8
		const val COLUMN_RADIUS = 6f
		const val HEADER_HEIGHT = 18
		const val HEADER_RULE_INSET = 4
		const val BODY_PAD = 6
		const val ROW_HEIGHT = 13
		const val ROW_RAIL_WIDTH = 2
		const val FOCUS_RADIUS = 3f
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
		const val CHROME_BOTTOM = TAB_TOP + BAR_HEIGHT
		const val SEARCH_TOP = CHROME_BOTTOM + 8
		const val SEARCH_WIDTH = 240
		const val SEARCH_HEIGHT = 22
		const val SEARCH_PAD = 10
		const val SEARCH_MAX_LENGTH = 20
		const val FIELD_TOP = SEARCH_TOP + SEARCH_HEIGHT + 12
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
		val TAB_LABELS = arrayOf("Features", "Settings")
	}
}
