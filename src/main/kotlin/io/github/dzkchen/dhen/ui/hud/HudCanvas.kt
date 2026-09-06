package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.RoundedQuad
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

internal class HudCanvas(
	manager: ModuleManager,
	coreElements: List<HudElement>,
	private val persist: () -> Unit
) : HudMetrics {
	private val editor = HudEditor(manager, this, coreElements)
	private val bannerText = Array(MAX_BANNERS) { DhenType.memo() }
	private var font: Font? = null
	private var screenHeight = 0
	private var hovered: HudTarget? = null
	private var labelled: HudTarget? = null
	private var labelledScale = 0.0f
	private var labelledAnchor = HudAnchor.TOP_LEFT
	private var labelledFlags = ""
	private var label = ""

	val dragging: Boolean
		get() = editor.dragging

	fun paint(
		graphics: GuiGraphicsExtractor,
		font: Font,
		width: Int,
		height: Int,
		mouseX: Int,
		mouseY: Int,
		hints: Array<String>
	) {
		this.font = font
		screenHeight = height
		editor.layout(width, height)
		hovered = if (editor.dragging) editor.selected else editor.targetAt(mouseX, mouseY)
		val targets = editor.targets
		drawGuides(graphics, width, height)
		for (index in targets.indices) drawTarget(graphics, font, targets[index])
		val shown = if (targets.isEmpty()) EMPTY_HINTS else hints
		val step = bannerHeight(font) + BANNER_GAP
		for (index in shown.indices) drawBanner(graphics, font, bannerText[index], shown[index], width, BANNER_TOP + index * step)
		if (editor.selected == null) return
		drawBanner(graphics, font, bannerText[shown.size], label(), width, BANNER_TOP + shown.size * step)
	}

	fun press(x: Int, y: Int) {
		editor.press(x, y)
	}

	fun drag(x: Int, y: Int, snap: Boolean) = editor.drag(x, y, snap)

	fun release() {
		if (editor.release()) persist()
	}

	fun reset(x: Int, y: Int) {
		if (editor.reset(x, y)) persist()
	}

	fun rescale(x: Int, y: Int, scroll: Double) {
		if (editor.rescale(x, y, scroll)) persist()
	}

	fun keyed(key: Int): Boolean {
		val changed = when (key) {
			GLFW.GLFW_KEY_LEFT -> editor.nudge(-NUDGE, 0)
			GLFW.GLFW_KEY_RIGHT -> editor.nudge(NUDGE, 0)
			GLFW.GLFW_KEY_UP -> editor.nudge(0, -NUDGE)
			GLFW.GLFW_KEY_DOWN -> editor.nudge(0, NUDGE)
			GLFW.GLFW_KEY_M -> {
				val element = editor.selected?.element ?: return false
				element.inMenus = !element.inMenus
				true
			}
			else -> return false
		}
		if (changed) persist()
		return true
	}

	override fun measure(target: HudTarget) {
		val font = this.font ?: return
		try {
			if (target.rendering) {
				val elementWidth = target.element.width(font)
				val elementHeight = target.element.height(font, screenHeight)
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

	private fun drawGuides(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
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

	private fun drawTarget(graphics: GuiGraphicsExtractor, font: Font, target: HudTarget) {
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

	private fun drawBanner(
		graphics: GuiGraphicsExtractor,
		font: Font,
		memo: TextMemo,
		text: String,
		width: Int,
		top: Int
	) {
		val boxWidth = memo.width(font, text) + 2 * BANNER_PAD_X
		val left = (width - boxWidth) / 2
		val bottom = top + bannerHeight(font)
		GlassGui.roundedFrame(graphics, left, top, left + boxWidth, bottom, RoundedQuad.FULL, GlassGui.surface(), DhenPalette.BORDER)
		memo.text(graphics, font, text, left + BANNER_PAD_X, top + BANNER_PAD_Y, DhenPalette.TEXT_PRIMARY, shadow = true)
	}

	private fun bannerHeight(font: Font): Int = DhenType.lineHeight(font) + 2 * BANNER_PAD_Y

	private fun label(): String {
		val target = editor.selected ?: return ""
		val element = target.element
		val flags = flagsOf(element)
		if (target !== labelled || element.scale != labelledScale || element.anchor != labelledAnchor || flags != labelledFlags) {
			labelled = target
			labelledScale = element.scale
			labelledAnchor = element.anchor
			labelledFlags = flags
			label = "${target.ownerName} - ${element.name}  $labelledAnchor  x$labelledScale$flags"
		}
		return label
	}

	private fun flagsOf(element: HudElement): String = when {
		!element.visible -> NOWHERE
		element.inMenus -> WORLD_AND_MENUS
		else -> WORLD_ONLY
	}

	internal companion object {
		private val LOGGER = LoggerFactory.getLogger(Dhen.MOD_ID)
		private const val PLACEHOLDER_PAD = 2
		private const val GUIDE_THICKNESS = 1
		private const val OUTLINE_RADIUS = 3f
		private const val BANNER_PAD_X = 12
		private const val BANNER_PAD_Y = 4
		private const val BANNER_TOP = 8
		private const val BANNER_GAP = 4
		private const val NUDGE = 1
		private const val MAX_BANNERS = 5
		private const val WORLD_AND_MENUS = "  world + menus"
		private const val WORLD_ONLY = "  world only"
		private const val NOWHERE = "  hidden"

		val HINTS = arrayOf(
			"Drag to move, scroll to scale, arrows to nudge",
			"Alt: no snap   Right-click: reset one   M: show over menus",
			"Esc: close   Reset all: /dhen reset-all   F8 in a menu: edit over it"
		)
		val MENU_HINTS = arrayOf(
			"Drag to move, scroll to scale, arrows to nudge",
			"Alt: no snap   Right-click: reset one   M: show over menus",
			"F8: stop editing this menu"
		)
		val EMPTY_HINTS = arrayOf("No HUD elements are registered")
	}
}
