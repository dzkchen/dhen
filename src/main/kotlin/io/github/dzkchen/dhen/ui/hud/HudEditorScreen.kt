package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.FlatGui
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal class HudEditorScreen(
	manager: ModuleManager,
	private val persist: () -> Unit
) : Screen(Component.literal("Dhen HUD Editor")) {
	private val editor = HudEditor(HudEditor.targetsOf(manager), ::measure)
	private var hovered: HudTarget? = null
	private var labelled: HudTarget? = null
	private var labelledScale = 0.0f
	private var labelledAnchor = HudAnchor.TOP_LEFT
	private var label = ""

	override fun init() {
		editor.layout(width, height)
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		editor.layout(width, height)
		hovered = if (editor.dragging) editor.selected else editor.targetAt(mouseX, mouseY)
		val targets = editor.targets
		drawGuides(graphics)
		for (i in targets.indices) drawTarget(graphics, targets[i])
		val step = bannerHeight() + BANNER_GAP
		var top = BANNER_TOP
		drawBanner(graphics, if (targets.isEmpty()) EMPTY_HINT else HINT, top)
		if (targets.isNotEmpty()) {
			top += step
			drawBanner(graphics, MODIFIER_HINT, top)
			top += step
			drawBanner(graphics, ACTION_HINT, top)
		}
		if (editor.selected != null) drawBanner(graphics, label(), top + step)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val x = event.x().toInt()
		val y = event.y().toInt()
		when (event.button()) {
			GLFW.GLFW_MOUSE_BUTTON_LEFT -> editor.press(x, y)
			GLFW.GLFW_MOUSE_BUTTON_RIGHT -> if (editor.reset(x, y)) persist()
			else -> return super.mouseClicked(event, doubleClick)
		}
		return true
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		if (!editor.dragging) return super.mouseDragged(event, dragX, dragY)
		editor.drag(event.x().toInt(), event.y().toInt(), snap = !event.hasAltDown())
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (!editor.dragging) return super.mouseReleased(event)
		if (editor.release()) persist()
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (editor.rescale(mouseX.toInt(), mouseY.toInt(), scrollY)) persist()
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val changed = when (event.key()) {
			GLFW.GLFW_KEY_LEFT -> editor.nudge(-NUDGE, 0)
			GLFW.GLFW_KEY_RIGHT -> editor.nudge(NUDGE, 0)
			GLFW.GLFW_KEY_UP -> editor.nudge(0, -NUDGE)
			GLFW.GLFW_KEY_DOWN -> editor.nudge(0, NUDGE)
			else -> return super.keyPressed(event)
		}
		if (changed) persist()
		return true
	}

	override fun removed() {
		if (editor.release()) persist()
		super.removed()
	}

	private fun measure(target: HudTarget) {
		if (target.rendering && measureElement(target)) return
		target.placeholder = true
		target.contentWidth = font.width(target.element.name) + 2 * PLACEHOLDER_PAD
		target.contentHeight = font.lineHeight + 2 * PLACEHOLDER_PAD
	}

	private fun measureElement(target: HudTarget): Boolean {
		val elementWidth: Int
		val elementHeight: Int
		try {
			elementWidth = target.element.width(font)
			elementHeight = target.element.height(font)
		} catch (throwable: Throwable) {
			target.element.markFailed()
			target.module.reportError(throwable)
			return false
		}
		if (elementWidth <= 0 || elementHeight <= 0) return false
		target.placeholder = false
		target.contentWidth = elementWidth
		target.contentHeight = elementHeight
		return true
	}

	private fun drawGuides(graphics: GuiGraphicsExtractor) {
		if (!editor.dragging) return
		val guideX = editor.guideX
		if (guideX != HudEditor.NO_GUIDE) {
			val left = guideX.coerceIn(0, maxOf(0, width - 1))
			FlatGui.fill(graphics, left, 0, left + 1, height, DhenPalette.ACCENT_MUTED)
		}
		val guideY = editor.guideY
		if (guideY != HudEditor.NO_GUIDE) {
			val top = guideY.coerceIn(0, maxOf(0, height - 1))
			FlatGui.fill(graphics, 0, top, width, top + 1, DhenPalette.ACCENT_MUTED)
		}
	}

	private fun drawTarget(graphics: GuiGraphicsExtractor, target: HudTarget) {
		if (target.placeholder) {
			val pose = graphics.pose()
			pose.pushMatrix()
			pose.translate(target.x.toFloat(), target.y.toFloat())
			pose.scale(target.element.scale, target.element.scale)
			GlassGui.shadow(graphics, 0, 0, target.contentWidth, target.contentHeight)
			FlatGui.fill(graphics, 0, 0, target.contentWidth, target.contentHeight, GlassGui.surface())
			FlatGui.text(
				graphics,
				font,
				target.element.name,
				PLACEHOLDER_PAD,
				PLACEHOLDER_PAD,
				DhenPalette.TEXT_DISABLED,
				shadow = true
			)
			pose.popMatrix()
		}
		val outline = when {
			target === editor.selected -> DhenPalette.ACCENT
			target === hovered -> DhenPalette.TEXT_SECONDARY
			else -> DhenPalette.BORDER
		}
		FlatGui.border(graphics, target.x, target.y, target.x + target.width, target.y + target.height, outline)
	}

	private fun drawBanner(graphics: GuiGraphicsExtractor, text: String, top: Int) {
		val boxWidth = font.width(text) + 2 * BANNER_PAD
		val left = (width - boxWidth) / 2
		val bottom = top + bannerHeight()
		GlassGui.frame(graphics, left, top, left + boxWidth, bottom, GlassGui.surface(), DhenPalette.BORDER)
		FlatGui.text(graphics, font, text, left + BANNER_PAD, top + BANNER_PAD, DhenPalette.TEXT_PRIMARY, shadow = true)
	}

	private fun bannerHeight(): Int = font.lineHeight + 2 * BANNER_PAD

	private fun label(): String {
		val target = editor.selected ?: return ""
		val element = target.element
		if (target !== labelled || element.scale != labelledScale || element.anchor != labelledAnchor) {
			labelled = target
			labelledScale = element.scale
			labelledAnchor = element.anchor
			label = "${target.module.name} - ${element.name}  $labelledAnchor  x$labelledScale"
		}
		return label
	}

	private companion object {
		const val PLACEHOLDER_PAD = 2
		const val BANNER_PAD = 3
		const val BANNER_TOP = 6
		const val BANNER_GAP = 3
		const val NUDGE = 1
		const val HINT = "Drag to move, scroll to scale, arrows to nudge"
		const val MODIFIER_HINT = "Alt: no snap   Right-click: reset one"
		const val ACTION_HINT = "Esc: close   Reset all: /dhen reset-all"
		const val EMPTY_HINT = "No HUD elements are registered"
	}
}
