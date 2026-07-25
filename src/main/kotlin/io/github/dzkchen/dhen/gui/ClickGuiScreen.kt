package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.module.Category
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

internal class ClickGuiScreen(
	private val categories: List<Category>,
	private val layout: MutableMap<String, PanelState>,
	private val persist: () -> Unit
) : Screen(Component.literal("Dhen")) {
	private val panels = mutableListOf<Panel>()
	private var dragging: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var dragMoved = false
	private var scrollOffset = 0

	override fun init() {
		panels.clear()
		val perRow = maxOf(1, (width - 2 * MARGIN + COLUMN_GAP) / (PANEL_WIDTH + COLUMN_GAP))
		categories.forEachIndexed { index, category ->
			val state = layout[category.name]?.copy() ?: PanelState(
				x = MARGIN + (index % perRow) * (PANEL_WIDTH + COLUMN_GAP),
				y = MARGIN + (index / perRow) * (PANEL_HEIGHT + COLUMN_GAP),
				collapsed = false
			)
			val panel = Panel(category, state)
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
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val button = event.button()
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick)
		}
		val x = event.x().toInt()
		val sy = event.y().toInt()
		val cy = sy + scrollOffset
		val panel = panels.lastOrNull { it.contains(x, cy) } ?: return super.mouseClicked(event, doubleClick)
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

	private class Panel(val category: Category, val state: PanelState) {
		val height: Int
			get() = HEADER_HEIGHT + if (state.collapsed) 0 else BODY_HEIGHT
		val toggleLeft: Int
			get() = state.x + PANEL_WIDTH - TOGGLE_WIDTH

		fun contains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + height

		fun headerContains(px: Int, py: Int): Boolean =
			px >= state.x && px < state.x + PANEL_WIDTH && py >= state.y && py < state.y + HEADER_HEIGHT

		fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
			val left = state.x
			val top = state.y
			val right = left + PANEL_WIDTH
			val headerBottom = top + HEADER_HEIGHT
			val bottom = top + height

			FlatGui.fill(graphics, left, top, right, bottom, DhenPalette.SURFACE)
			val headerColor = if (headerContains(mouseX, mouseY)) DhenPalette.SURFACE_INTERACTIVE else DhenPalette.SURFACE_RAISED
			FlatGui.fill(graphics, left, top, right, headerBottom, headerColor)

			FlatGui.fill(graphics, left, top, right, top + 1, DhenPalette.BORDER)
			FlatGui.fill(graphics, left, bottom - 1, right, bottom, DhenPalette.BORDER)
			FlatGui.fill(graphics, left, top, left + 1, bottom, DhenPalette.BORDER)
			FlatGui.fill(graphics, right - 1, top, right, bottom, DhenPalette.BORDER)

			FlatGui.text(graphics, font, category.displayName, left + CONTENT_PAD, top + TEXT_OFFSET, DhenPalette.TEXT_PRIMARY)
			val glyph = if (state.collapsed) "+" else "-"
			FlatGui.text(graphics, font, glyph, right - TOGGLE_WIDTH + TOGGLE_GLYPH_INSET, top + TEXT_OFFSET, DhenPalette.TEXT_SECONDARY)
		}
	}

	private companion object {
		const val PANEL_WIDTH = 118
		const val HEADER_HEIGHT = 16
		const val BODY_HEIGHT = 46
		const val PANEL_HEIGHT = HEADER_HEIGHT + BODY_HEIGHT
		const val MARGIN = 8
		const val COLUMN_GAP = 8
		const val CONTENT_PAD = 6
		const val TEXT_OFFSET = 4
		const val TOGGLE_WIDTH = 14
		const val TOGGLE_GLYPH_INSET = 5
		const val SCROLL_STEP = 20
		const val SCROLLBAR_WIDTH = 3
		const val SCROLLBAR_MIN_THUMB = 16
	}
}
