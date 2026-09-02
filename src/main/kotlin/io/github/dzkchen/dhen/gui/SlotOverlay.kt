package io.github.dzkchen.dhen.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

internal const val SLOT_BOX = 16

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
	val pose = graphics.pose()
	pose.pushMatrix()
	try {
		pose.translate((right - width).toFloat(), top.toFloat())
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
