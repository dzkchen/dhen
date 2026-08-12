package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.FlatGui
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.RoundedQuad
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
		target.contentWidth = DhenType.width(font, target.element.name) + 2 * PLACEHOLDER_PAD
		target.contentHeight = DhenType.lineHeight(font) + 2 * PLACEHOLDER_PAD
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
			FlatGui.fill(graphics, left, 0, left + 1, height, DhenPalette.accent)
		}
		val guideY = editor.guideY
		if (guideY != HudEditor.NO_GUIDE) {
			val top = guideY.coerceIn(0, maxOf(0, height - 1))
			FlatGui.fill(graphics, 0, top, width, top + 1, DhenPalette.accent)
		}
	}

	private fun drawTarget(graphics: GuiGraphicsExtractor, target: HudTarget) {
		val left = target.x
		val top = target.y
		val right = left + target.width
		val bottom = top + target.height
		val outline = when {
			target === editor.selected -> DhenPalette.accent
			target === hovered -> DhenPalette.TEXT_SECONDARY
			else -> DhenPalette.BORDER
		}
		if (target.placeholder) {
			GlassGui.roundedFrame(graphics, left, top, right, bottom, OUTLINE_RADIUS, GlassGui.surface(), outline)
			val pose = graphics.pose()
			pose.pushMatrix()
			pose.translate(left.toFloat(), top.toFloat())
			pose.scale(target.element.scale, target.element.scale)
			DhenType.text(
				graphics,
				font,
				target.element.name,
				PLACEHOLDER_PAD,
				PLACEHOLDER_PAD,
				DhenPalette.TEXT_DISABLED,
				shadow = true
			)
			pose.popMatrix()
			return
		}
		RoundedGui.border(graphics, left, top, right, bottom, OUTLINE_RADIUS, RoundedGui.HAIRLINE, outline)
	}

	private fun drawBanner(graphics: GuiGraphicsExtractor, text: String, top: Int) {
		val boxWidth = DhenType.width(font, text) + 2 * BANNER_PAD_X
		val left = (width - boxWidth) / 2
		val bottom = top + bannerHeight()
		GlassGui.roundedFrame(graphics, left, top, left + boxWidth, bottom, RoundedQuad.FULL, GlassGui.surface(), DhenPalette.BORDER)
		DhenType.text(graphics, font, text, left + BANNER_PAD_X, top + BANNER_PAD_Y, DhenPalette.TEXT_PRIMARY, shadow = true)
	}

	private fun bannerHeight(): Int = DhenType.lineHeight(font) + 2 * BANNER_PAD_Y

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
		const val OUTLINE_RADIUS = 3f
		const val BANNER_PAD_X = 12
		const val BANNER_PAD_Y = 4
		const val BANNER_TOP = 8
		const val BANNER_GAP = 4
		const val NUDGE = 1
		const val HINT = "Drag to move, scroll to scale, arrows to nudge"
		const val MODIFIER_HINT = "Alt: no snap   Right-click: reset one"
		const val ACTION_HINT = "Esc: close   Reset all: /dhen reset-all"
		const val EMPTY_HINT = "No HUD elements are registered"
	}
}
