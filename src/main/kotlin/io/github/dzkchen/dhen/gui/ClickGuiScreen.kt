package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

internal class ClickGuiScreen(
	private val categories: List<Category>,
	private val manager: ModuleManager,
	private val layout: MutableMap<String, PanelState>,
	private val persist: () -> Unit
) : Screen(Component.literal("Dhen")) {
	private val panels = mutableListOf<Panel>()
	private val expanded = mutableSetOf<String>()
	private var dragging: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var dragMoved = false
	private var scrollOffset = 0

	override fun init() {
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
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick)
		}
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
			} else {
				if (!expanded.remove(module.name)) expanded.add(module.name)
				scrollOffset = ClickGuiScroll.clampOffset(scrollOffset, maxScroll())
			}
		}
		return true
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val panel = dragging ?: return super.mouseDragged(event, dragX, dragY)
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

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (dragging != null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val max = maxScroll()
		if (max <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		scrollOffset = ClickGuiScroll.clampOffset(scrollOffset - (scrollY * SCROLL_STEP).roundToInt(), max)
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val panel = dragging ?: return super.mouseReleased(event)
		dragging = null
		if (dragMoved) store(panel)
		return true
	}

	private fun store(panel: Panel) {
		layout[panel.category.name] = panel.state.copy()
		persist()
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
		private val expandedAt = { index: Int -> modules[index].name in expanded }

		val height: Int
			get() = HEADER_HEIGHT + if (state.collapsed) 0 else bodyHeight()
		val toggleLeft: Int
			get() = state.x + PANEL_WIDTH - TOGGLE_WIDTH

		private fun bodyHeight(): Int =
			ClickGuiRows.bodyHeight(modules.size, expandedCount(), ROW_HEIGHT, SETTINGS_HEIGHT)

		private fun expandedCount(): Int {
			var count = 0
			for (i in modules.indices) if (modules[i].name in expanded) count++
			return count
		}

		fun contains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + height

		fun headerContains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + HEADER_HEIGHT

		fun rowAt(px: Int, py: Int): Module? {
			if (state.collapsed) return null
			if (px < state.x || px >= state.x + PANEL_WIDTH) return null
			val index = ClickGuiRows.rowAt(py - (state.y + HEADER_HEIGHT), modules.size, ROW_HEIGHT, SETTINGS_HEIGHT, expandedAt)
				?: return null
			return modules[index]
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
					val module = modules[i]
					drawRow(graphics, font, module, left, right, rowTop, mouseX, mouseY)
					rowTop += ROW_HEIGHT
					if (module.name in expanded) {
						drawSettings(graphics, left, right, rowTop)
						rowTop += SETTINGS_HEIGHT
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

		private fun drawSettings(graphics: GuiGraphicsExtractor, left: Int, right: Int, top: Int) {
			FlatGui.fill(graphics, left + 1, top, right - 1, top + SETTINGS_HEIGHT, DhenPalette.CANVAS)
			FlatGui.fill(graphics, left + 1, top, right - 1, top + 1, DhenPalette.BORDER)
		}
	}

	private companion object {
		const val PANEL_WIDTH = 118
		const val HEADER_HEIGHT = 16
		const val PANEL_HEIGHT = HEADER_HEIGHT + 40
		const val ROW_HEIGHT = 13
		const val ROW_TEXT_OFFSET = 3
		const val SETTINGS_HEIGHT = 22
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
