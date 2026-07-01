package io.github.dzkchen.dhen.platform

import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.runtime.DhenRuntime
import io.github.dzkchen.dhen.runtime.LifecycleState
import io.github.dzkchen.dhen.runtime.ModuleRecord
import io.github.dzkchen.dhen.runtime.PanelLayoutState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ClickGuiScreen(private val parent: Screen?, private val runtime: DhenRuntime) : Screen(Component.literal("Dhen")) {
	private inner class Panel(val category: ModuleCategory, val modules: List<ModuleRecord>) {
		var x = 0
		var y = 0
		var collapsed = false
		var unknownFields: Map<String, Any?> = emptyMap()
		val bodyHeight get() = modules.size * ROW_HEIGHT
	}

	private val panels = mutableListOf<Panel>()
	private var dragging: Panel? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var focusedPanel = 0

	// -1 focuses the panel title bar; 0..n focus module rows.
	private var focusedRow = -1

	override fun init() {
		panels.clear()
		val saved = runtime.panelLayout()
		var cursorX = MARGIN
		var cursorY = MARGIN
		var rowHeight = 0
		for ((category, modules) in runtime.modulesByCategory()) {
			val panel = Panel(category, modules)
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
	}

	override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
		guiGraphics.fill(0, 0, width, height, DIM_BACKGROUND)
		for ((index, panel) in panels.withIndex()) renderPanel(guiGraphics, panel, index == focusedPanel, mouseX, mouseY)
		guiGraphics.text(font, HELP_TEXT, MARGIN, height - 10, HINT_COLOR)
	}

	private fun renderPanel(g: GuiGraphicsExtractor, panel: Panel, focused: Boolean, mouseX: Int, mouseY: Int) {
		val right = panel.x + PANEL_WIDTH
		g.fill(panel.x, panel.y, right, panel.y + TITLE_HEIGHT, if (focused) TITLE_FOCUSED else TITLE_BACKGROUND)
		g.text(font, panel.category.displayName, panel.x + PADDING, panel.y + 3, TEXT_COLOR)
		val marker = if (panel.collapsed) "[+]" else "[-]"
		g.text(font, marker, right - font.width(marker) - PADDING, panel.y + 3, TEXT_COLOR)
		if (focused && focusedRow == -1) drawOutline(g, panel.x, panel.y, right, panel.y + TITLE_HEIGHT)
		if (panel.collapsed) return

		val bodyTop = panel.y + TITLE_HEIGHT
		val bodyBottom = bodyTop + panel.bodyHeight
		g.fill(panel.x, bodyTop, right, bodyBottom, BODY_BACKGROUND)
		g.enableScissor(panel.x, bodyTop, right, bodyBottom)
		for ((rowIndex, record) in panel.modules.withIndex()) {
			val rowTop = bodyTop + rowIndex * ROW_HEIGHT
			val enabled = record.state == LifecycleState.ENABLED
			if (enabled) g.fill(panel.x, rowTop, right, rowTop + ROW_HEIGHT, ROW_ENABLED)
			if (mouseX in panel.x until right && mouseY in rowTop until rowTop + ROW_HEIGHT) {
				g.fill(panel.x, rowTop, right, rowTop + ROW_HEIGHT, ROW_HOVER)
			}
			if (focused && rowIndex == focusedRow) drawOutline(g, panel.x, rowTop, right, rowTop + ROW_HEIGHT)
			val color = if (record.state == LifecycleState.FAILED) FAIL_COLOR else TEXT_COLOR
			g.text(font, record.module.metadata.name, panel.x + PADDING, rowTop + 2, color)
			val badge = stateBadge(record.state)
			g.text(font, badge, right - font.width(badge) - PADDING, rowTop + 2, color)
		}
		g.disableScissor()
	}

	private fun drawOutline(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int) {
		g.fill(x0, y0, x1, y0 + 1, FOCUS_OUTLINE)
		g.fill(x0, y1 - 1, x1, y1, FOCUS_OUTLINE)
		g.fill(x0, y0, x0 + 1, y1, FOCUS_OUTLINE)
		g.fill(x1 - 1, y0, x1, y1, FOCUS_OUTLINE)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
		val mouseX = event.x().toInt()
		val mouseY = event.y().toInt()
		for (index in panels.indices.reversed()) {
			val panel = panels[index]
			val right = panel.x + PANEL_WIDTH
			if (mouseX in panel.x until right && mouseY in panel.y until panel.y + TITLE_HEIGHT) {
				focusedPanel = index
				focusedRow = -1
				if (mouseX >= right - COLLAPSE_HIT) {
					panel.collapsed = !panel.collapsed
					persistLayout()
				} else {
					dragging = panel
					dragOffsetX = mouseX - panel.x
					dragOffsetY = mouseY - panel.y
				}
				return true
			}
			if (!panel.collapsed && mouseX in panel.x until right) {
				val rowIndex = (mouseY - (panel.y + TITLE_HEIGHT)) / ROW_HEIGHT
				if (mouseY >= panel.y + TITLE_HEIGHT && rowIndex in panel.modules.indices) {
					focusedPanel = index
					focusedRow = rowIndex
					runtime.toggleModule(panel.modules[rowIndex].id)
					return true
				}
			}
		}
		return false
	}

	override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
		val panel = dragging ?: return false
		panel.x = (event.x().toInt() - dragOffsetX).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.y = (event.y().toInt() - dragOffsetY).coerceIn(0, maxOf(0, height - TITLE_HEIGHT))
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (dragging == null) return false
		dragging = null
		persistLayout()
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val nudge = event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0
		when (event.key()) {
			GLFW.GLFW_KEY_ESCAPE -> {
				onClose()
				return true
			}
			GLFW.GLFW_KEY_TAB -> {
				changePanel(if (event.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) -1 else 1)
				return true
			}
			GLFW.GLFW_KEY_LEFT -> if (nudge) nudgeFocused(-NUDGE_STEP, 0) else changePanel(-1)
			GLFW.GLFW_KEY_RIGHT -> if (nudge) nudgeFocused(NUDGE_STEP, 0) else changePanel(1)
			GLFW.GLFW_KEY_UP -> if (nudge) nudgeFocused(0, -NUDGE_STEP) else moveRow(-1)
			GLFW.GLFW_KEY_DOWN -> if (nudge) nudgeFocused(0, NUDGE_STEP) else moveRow(1)
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activateFocused()
			else -> return super.keyPressed(event)
		}
		return true
	}

	private fun changePanel(delta: Int) {
		if (panels.isEmpty()) return
		focusedPanel = (focusedPanel + delta + panels.size) % panels.size
		focusedRow = -1
	}

	private fun moveRow(delta: Int) {
		val panel = panels.getOrNull(focusedPanel) ?: return
		if (panel.collapsed) return
		focusedRow = (focusedRow + delta).coerceIn(-1, panel.modules.size - 1)
	}

	private fun activateFocused() {
		val panel = panels.getOrNull(focusedPanel) ?: return
		if (focusedRow == -1) {
			panel.collapsed = !panel.collapsed
			persistLayout()
		} else {
			panel.modules.getOrNull(focusedRow)?.let { runtime.toggleModule(it.id) }
		}
	}

	private fun nudgeFocused(dx: Int, dy: Int) {
		val panel = panels.getOrNull(focusedPanel) ?: return
		panel.x = (panel.x + dx).coerceIn(0, maxOf(0, width - PANEL_WIDTH))
		panel.y = (panel.y + dy).coerceIn(0, maxOf(0, height - TITLE_HEIGHT))
		persistLayout()
	}

	private fun persistLayout() {
		runtime.savePanelLayout(
			panels.associate { it.category.name to PanelLayoutState(it.x, it.y, it.collapsed, it.unknownFields) },
		)
	}

	override fun onClose() {
		if (parent != null) minecraft.setScreenAndShow(parent) else super.onClose()
	}

	private fun stateBadge(state: LifecycleState): String = when (state) {
		LifecycleState.ENABLED -> "[ON]"
		LifecycleState.FAILED -> "[FAIL]"
		else -> "[OFF]"
	}

	private companion object {
		const val MARGIN = 8
		const val PADDING = 4
		const val GUTTER = 6
		const val PANEL_WIDTH = 110
		const val TITLE_HEIGHT = 13
		const val ROW_HEIGHT = 12
		const val COLLAPSE_HIT = 18
		const val NUDGE_STEP = 5

		const val DIM_BACKGROUND = 0x88101018.toInt()
		const val TITLE_BACKGROUND = 0xFF2B2B33.toInt()
		const val TITLE_FOCUSED = 0xFF3A3A47.toInt()
		const val BODY_BACKGROUND = 0xEE181820.toInt()
		const val ROW_ENABLED = 0xFF24303F.toInt()
		const val ROW_HOVER = 0x30FFFFFF
		const val FOCUS_OUTLINE = 0xFFFFFFFF.toInt()
		const val TEXT_COLOR = 0xFFE0E0E0.toInt()
		const val FAIL_COLOR = 0xFFFF6B6B.toInt()
		const val HINT_COLOR = 0xFF888888.toInt()

		const val HELP_TEXT = "Tab/Arrows: focus  Enter: toggle  Ctrl+Arrows: move panel  Esc: close"
	}
}
