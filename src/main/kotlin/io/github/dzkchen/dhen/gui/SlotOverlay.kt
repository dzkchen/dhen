package io.github.dzkchen.dhen.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal const val SLOT_BOX = 16

private const val SLOT_EDGE = 1

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
	slotMark(graphics, font, text, right - width, top, scale, color, memo)
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
