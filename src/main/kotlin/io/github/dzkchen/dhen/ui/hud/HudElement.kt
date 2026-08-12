package io.github.dzkchen.dhen.ui.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

abstract class HudElement(
	val name: String,
	anchor: HudAnchor = HudAnchor.TOP_LEFT,
	offsetX: Int = 0,
	offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	visible: Boolean = true
) {
	var anchor: HudAnchor = anchor
	var offsetX: Int = offsetX
	var offsetY: Int = offsetY
	var visible: Boolean = visible

	var scale: Float = clampScale(scale)
		set(value) {
			field = clampScale(value)
		}

	private val declaredAnchor = this.anchor
	private val declaredOffsetX = this.offsetX
	private val declaredOffsetY = this.offsetY
	private val declaredScale = this.scale

	var failed: Boolean = false
		private set

	val isActive: Boolean
		get() = visible && !failed

	fun resetLayout(): Boolean {
		if (
			anchor == declaredAnchor &&
			offsetX == declaredOffsetX &&
			offsetY == declaredOffsetY &&
			scale == declaredScale
		) {
			return false
		}
		anchor = declaredAnchor
		offsetX = declaredOffsetX
		offsetY = declaredOffsetY
		scale = declaredScale
		return true
	}

	abstract fun width(font: Font): Int

	abstract fun height(font: Font): Int

	abstract fun render(graphics: GuiGraphicsExtractor, font: Font)

	open fun invalidateMeasurement() = Unit

	internal fun markFailed() {
		failed = true
	}

	companion object {
		const val MIN_SCALE = 0.25f
		const val MAX_SCALE = 4.0f
		const val DEFAULT_SCALE = 1.0f

		private fun clampScale(scale: Float): Float =
			if (scale.isNaN()) DEFAULT_SCALE else scale.coerceIn(MIN_SCALE, MAX_SCALE)
	}
}
