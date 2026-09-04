package io.github.dzkchen.dhen.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

internal const val SLOT_BOX = 16

internal const val SLOT_TOP_LEFT = 0
internal const val SLOT_TOP_RIGHT = 1
internal const val SLOT_BOTTOM_LEFT = 2
internal const val SLOT_BOTTOM_RIGHT = 3
internal const val SLOT_CORNERS = 4

private const val SLOT_EDGE = 1
private const val SLOT_BASELINE_LIFT = 2

internal object SlotTint {
	private var open = false
	private var priority = 0
	private var color = 0

	fun claim(color: Int, priority: Int) {
		if (!open) return
		if (this.color != 0 && priority <= this.priority) return
		this.priority = priority
		this.color = color
	}

	internal fun begin() {
		open = true
		priority = 0
		color = 0
	}

	internal fun take(): Int {
		open = false
		val won = color
		color = 0
		return won
	}

	internal fun end(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
		val won = take()
		if (won != 0) SharpGui.fill(graphics, x, y, x + SLOT_BOX, y + SLOT_BOX, won)
	}
}

internal fun slotOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
	SharpGui.fill(graphics, x, y, x + SLOT_BOX, y + SLOT_EDGE, color)
	SharpGui.fill(graphics, x, y + SLOT_BOX - SLOT_EDGE, x + SLOT_BOX, y + SLOT_BOX, color)
	SharpGui.fill(graphics, x, y + SLOT_EDGE, x + SLOT_EDGE, y + SLOT_BOX - SLOT_EDGE, color)
	SharpGui.fill(graphics, x + SLOT_BOX - SLOT_EDGE, y + SLOT_EDGE, x + SLOT_BOX, y + SLOT_BOX - SLOT_EDGE, color)
}

internal fun slotText(
	graphics: GuiGraphicsExtractor,
	text: String,
	right: Int,
	top: Int,
	scale: Float,
	color: Int,
	memo: TextMemo? = null
) {
	val font = Minecraft.getInstance().font
	val width = memo?.width(font, text) ?: DhenType.width(font, text)
	slotMark(graphics, font, text, right - (width * scale).roundToInt(), top, scale, color, memo)
}

internal fun slotCornerText(
	graphics: GuiGraphicsExtractor,
	text: String,
	x: Int,
	y: Int,
	corner: Int,
	color: Int
) {
	val font = Minecraft.getInstance().font
	val width = DhenType.width(font, text)
	if (width <= 0) return
	val scale = if (width > SLOT_BOX) SLOT_BOX.toFloat() / width else 1f
	val right = corner == SLOT_TOP_RIGHT || corner == SLOT_BOTTOM_RIGHT
	val bottom = corner == SLOT_BOTTOM_LEFT || corner == SLOT_BOTTOM_RIGHT
	val left = if (right) x + SLOT_BOX - (width * scale).roundToInt() else x
	val top = if (bottom) {
		y + SLOT_BOX - ((DhenType.lineHeight(font) - SLOT_BASELINE_LIFT) * scale).roundToInt()
	} else {
		y
	}
	slotMark(graphics, font, text, left, top, scale, color)
}

internal fun slotCenteredText(
	graphics: GuiGraphicsExtractor,
	text: String,
	centerX: Int,
	centerY: Int,
	scale: Float,
	color: Int
) {
	val font = Minecraft.getInstance().font
	val left = centerX - (DhenType.width(font, text) * scale / 2).roundToInt()
	val top = centerY - (DhenType.lineHeight(font) * scale / 2).roundToInt()
	slotMark(graphics, font, text, left, top, scale, color)
}

internal fun slotMark(
	graphics: GuiGraphicsExtractor,
	font: Font,
	text: String,
	left: Int,
	top: Int,
	scale: Float,
	color: Int,
	memo: TextMemo? = null
) {
	val pose = graphics.pose()
	pose.pushMatrix()
	try {
		pose.translate(left.toFloat(), top.toFloat())
		pose.scale(scale, scale)
		if (memo == null) {
			DhenType.text(graphics, font, text, 0, 0, color, true)
		} else {
			memo.text(graphics, font, text, 0, 0, color, true)
		}
	} finally {
		pose.popMatrix()
	}
}
