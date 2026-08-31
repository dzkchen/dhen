package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.RoundedQuad
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

internal class HudEditorScreen(
	manager: ModuleManager,
	private val persist: () -> Unit,
	coreElements: List<HudElement> = emptyList(),
	private val opened: () -> Unit = {},
	private val closed: () -> Unit = {}
) : LiveWorldScreen(Component.literal("Dhen HUD Editor")) {
	private val editor = HudEditor(manager, ::measure, coreElements)
	private var hovered: HudTarget? = null
	private var labelled: HudTarget? = null
	private var labelledScale = 0.0f
	private var labelledAnchor = HudAnchor.TOP_LEFT
	private var label = ""
	private val bannerText = Array(HINTS.size + 1) { DhenType.memo() }

	override fun init() {
		opened()
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
		val hints = if (targets.isEmpty()) EMPTY_HINTS else HINTS
		for (i in hints.indices) drawBanner(graphics, bannerText[i], hints[i], BANNER_TOP + i * step)
		if (editor.selected != null) drawBanner(graphics, bannerText.last(), label(), BANNER_TOP + hints.size * step)
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
		if (!editor.dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(event)
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
		closed()
		super.removed()
	}

	private fun measure(target: HudTarget) {
		try {
			if (target.rendering) {
				val elementWidth = target.element.width(font)
				val elementHeight = target.element.height(font, height)
				if (elementWidth > 0 && elementHeight > 0) {
					target.placeholder = false
					target.contentWidth = elementWidth
					target.contentHeight = elementHeight
					return
				}
			}
		} catch (throwable: Throwable) {
			target.element.markFailed()
			val module = target.module
			if (module == null) LOGGER.error("Core HUD element '{}' failed in the editor", target.element.name, throwable)
			else module.reportError(throwable)
		}
		target.placeholder = true
		target.contentWidth = DhenType.width(font, target.element.name) + 2 * PLACEHOLDER_PAD
		target.contentHeight = DhenType.lineHeight(font) + 2 * PLACEHOLDER_PAD
	}

	private fun drawGuides(graphics: GuiGraphicsExtractor) {
		if (!editor.dragging) return
		val guideX = editor.guideX
		if (guideX != HudEditor.NO_GUIDE) {
			val left = HudLayout.clamp(guideX, GUIDE_THICKNESS, width)
			SharpGui.fill(graphics, left, 0, left + GUIDE_THICKNESS, height, DhenPalette.accent)
		}
		val guideY = editor.guideY
		if (guideY != HudEditor.NO_GUIDE) {
			val top = HudLayout.clamp(guideY, GUIDE_THICKNESS, height)
			SharpGui.fill(graphics, 0, top, width, top + GUIDE_THICKNESS, DhenPalette.accent)
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

	private fun drawBanner(graphics: GuiGraphicsExtractor, memo: TextMemo, text: String, top: Int) {
		val boxWidth = memo.width(font, text) + 2 * BANNER_PAD_X
		val left = (width - boxWidth) / 2
		val bottom = top + bannerHeight()
		GlassGui.roundedFrame(graphics, left, top, left + boxWidth, bottom, RoundedQuad.FULL, GlassGui.surface(), DhenPalette.BORDER)
		memo.text(graphics, font, text, left + BANNER_PAD_X, top + BANNER_PAD_Y, DhenPalette.TEXT_PRIMARY, shadow = true)
	}

	private fun bannerHeight(): Int = DhenType.lineHeight(font) + 2 * BANNER_PAD_Y

	private fun label(): String {
		val target = editor.selected ?: return ""
		val element = target.element
		if (target !== labelled || element.scale != labelledScale || element.anchor != labelledAnchor) {
			labelled = target
			labelledScale = element.scale
			labelledAnchor = element.anchor
			label = "${target.ownerName} - ${element.name}  $labelledAnchor  x$labelledScale"
		}
		return label
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(Dhen.MOD_ID)
		const val PLACEHOLDER_PAD = 2
		const val GUIDE_THICKNESS = 1
		const val OUTLINE_RADIUS = 3f
		const val BANNER_PAD_X = 12
		const val BANNER_PAD_Y = 4
		const val BANNER_TOP = 8
		const val BANNER_GAP = 4
		const val NUDGE = 1
		val HINTS = arrayOf(
			"Drag to move, scroll to scale, arrows to nudge",
			"Alt: no snap   Right-click: reset one",
			"Esc: close   Reset all: /dhen reset-all"
		)
		val EMPTY_HINTS = arrayOf("No HUD elements are registered")
	}
}

internal fun editingHud(): Boolean = Minecraft.getInstance().gui.screen() is HudEditorScreen
