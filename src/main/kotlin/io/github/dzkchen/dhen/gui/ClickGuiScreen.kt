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
	private val expanded = mutableSetOf<String>()
	private var dragging: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var dragMoved = false
	private var controlDrag: Panel? = null
	private var focusPanel: Panel? = null
	private var scrollOffset = 0

	override fun init() {
		blurFocus()
		panels.clear()
		val byCategory = manager.categories
		val perRow = maxOf(1, (width - 2 * MARGIN + COLUMN_GAP) / (PANEL_WIDTH + COLUMN_GAP))
		categories.forEachIndexed { index, category ->
			val state = layout[category.name]?.copy() ?: PanelState(
				x = MARGIN + (index % perRow) * (PANEL_WIDTH + COLUMN_GAP),
				y = MARGIN + (index / perRow) * (PANEL_HEIGHT + COLUMN_GAP),
				collapsed = false
			)
			val panel = Panel(category, state, byCategory[category] ?: emptyList())
			clampToField(panel)
			panels += panel
		}
		scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val contentMouseY = mouseY + scrollOffset
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(0f, -scrollOffset.toFloat())
		for (i in panels.indices) panels[i].draw(graphics, font, mouseX, contentMouseY)
		pose.popMatrix()
		drawScrollbar(graphics)
		hoveredModule(mouseX, contentMouseY)?.let { drawTooltip(graphics, it, mouseX, mouseY) }
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
		val cy = sy + scrollOffset
		val panel = panelAt(x, cy) ?: return super.mouseClicked(event, doubleClick)
		bringToFront(panel)
		if (panel.headerContains(x, cy)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || x >= panel.toggleLeft) {
				panel.state.collapsed = !panel.state.collapsed
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
		focusPanel?.let { panel ->
			when (panel.keyInput(event.key(), event.modifiers())) {
				ControlKey.COMMITTED -> {
					focusPanel = null
					persistModules()
					return true
				}
				ControlKey.CANCELLED -> {
					focusPanel = null
					return true
				}
				ControlKey.CONSUMED -> return true
				ControlKey.IGNORED -> Unit
			}
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean =
		focusPanel?.charInput(event.codepoint()) == true || super.charTyped(event)

	override fun removed() {
		blurFocus()
		super.removed()
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		dragging?.let { panel ->
			val newX = (event.x().toInt() - dragOffsetX).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
			val screenY = (event.y().toInt() - dragOffsetY).coerceIn(0, maxOf(0, height - panel.height))
			val newY = screenY + scrollOffset
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

	private fun blurFocus() {
		focusPanel?.let { if (it.blur()) persistModules() }
		focusPanel = null
	}

	private fun clampToField(panel: Panel) {
		panel.state.x = panel.state.x.coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.state.y = panel.state.y.coerceAtLeast(0)
	}

	private fun contentBottom(): Int {
		var bottom = 0
		for (i in panels.indices) {
			val panelBottom = panels[i].state.y + panels[i].height
			if (panelBottom > bottom) bottom = panelBottom
		}
		return bottom
	}

	private fun maxScroll(): Int = ClickGuiScroll.maxScroll(contentBottom(), height, MARGIN)

	private fun panelAt(x: Int, y: Int): Panel? {
		for (i in panels.size - 1 downTo 0) {
			val panel = panels[i]
			if (panel.contains(x, y)) return panel
		}
		return null
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
		private val settingsHeightAt = IntUnaryOperator { index -> settingsHeight(index) }
		private var activeControl: SettingControl? = null
		private var activeLeft = 0
		private var activeWidth = 0
		private var focusedControl: SettingControl? = null

		val height: Int
			get() = HEADER_HEIGHT + if (state.collapsed) 0 else ClickGuiRows.bodyHeight(modules.size, ROW_HEIGHT, settingsHeightAt)
		val toggleLeft: Int
			get() = state.x + PANEL_WIDTH - TOGGLE_WIDTH

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
			val index = ClickGuiRows.rowAt(py - (state.y + HEADER_HEIGHT), modules.size, ROW_HEIGHT, settingsHeightAt)
				?: return null
			return modules[index]
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
			for (i in modules.indices) {
				top += ROW_HEIGHT
				val areaHeight = settingsHeight(i)
				if (areaHeight > 0) {
					if (py >= top && py < top + areaHeight) return renderableAt(i, py - (top + SETTINGS_PAD))
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
				for (i in modules.indices) {
					drawRow(graphics, font, modules[i], left, right, rowTop, mouseX, mouseY)
					rowTop += ROW_HEIGHT
					val areaHeight = settingsHeight(i)
					if (areaHeight > 0) {
						drawSettings(graphics, font, i, left, right, rowTop, areaHeight, mouseX, mouseY)
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
