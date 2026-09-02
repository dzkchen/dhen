package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

abstract class HudElement(
	val name: String,
	var anchor: HudAnchor = HudAnchor.TOP_LEFT,
	var offsetX: Int = 0,
	var offsetY: Int = 0,
	scale: Float = DEFAULT_SCALE,
	var visible: Boolean = true,
	var background: Boolean = false
) {
	var scale: Float = clampScale(scale)
		set(value) {
			field = clampScale(value)
		}

	private val declaredAnchor = this.anchor
	private val declaredOffsetX = this.offsetX
	private val declaredOffsetY = this.offsetY
	private val declaredScale = this.scale
	private val declaredVisible = this.visible
	private val declaredBackground = this.background

	var failed: Boolean = false
		private set

	val isActive: Boolean
		get() = visible && !failed

	internal open val hasContent: Boolean
		get() = true

	internal open val listed: Boolean
		get() = true

	protected val textInk: Int
		get() = if (background) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_ON_WORLD

	fun resetToDeclared(): Boolean {
		if (
			anchor == declaredAnchor &&
			offsetX == declaredOffsetX &&
			offsetY == declaredOffsetY &&
			scale == declaredScale &&
			visible == declaredVisible &&
			background == declaredBackground &&
			!failed
		) {
			return false
		}
		anchor = declaredAnchor
		offsetX = declaredOffsetX
		offsetY = declaredOffsetY
		scale = declaredScale
		visible = declaredVisible
		background = declaredBackground
		clearFailure()
		return true
	}

	abstract fun width(font: Font): Int

	abstract fun height(font: Font): Int

	internal open fun height(font: Font, screenHeight: Int): Int = height(font)

	abstract fun render(graphics: GuiGraphicsExtractor, font: Font)

	internal open fun placeX(screenWidth: Int, width: Int): Int =
		HudLayout.placeOnScreen(anchor.horizontal, screenWidth, width, offsetX)

	internal open fun placeY(screenHeight: Int, height: Int): Int =
		HudLayout.placeOnScreen(anchor.vertical, screenHeight, height, offsetY)

	internal open fun offsetXFor(anchor: HudAnchor, screenWidth: Int, width: Int, position: Int): Int =
		HudLayout.offsetFor(anchor.horizontal, screenWidth, width, position)

	internal open fun offsetYFor(anchor: HudAnchor, screenHeight: Int, height: Int, position: Int): Int =
		HudLayout.offsetFor(anchor.vertical, screenHeight, height, position)

	open fun invalidateMeasurement() = Unit

	internal fun markFailed() {
		failed = true
	}

	internal fun clearFailure() {
		failed = false
	}

	companion object {
		const val MIN_SCALE = 0.25f
		const val MAX_SCALE = 4.0f
		const val DEFAULT_SCALE = 1.0f

		private fun clampScale(scale: Float): Float =
			if (scale.isNaN()) DEFAULT_SCALE else scale.coerceIn(MIN_SCALE, MAX_SCALE)
	}
}
