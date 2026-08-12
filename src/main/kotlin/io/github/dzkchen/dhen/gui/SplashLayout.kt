package io.github.dzkchen.dhen.gui

import kotlin.math.ceil

internal object SplashLayout {
	const val MARK_WIDTH = 75
	const val MARK_HEIGHT = 24

	const val FADE_OUT_MILLIS = 1000f
	const val FADE_IN_MILLIS = 500f
	const val HANDOVER = 2f

	private const val MARK_WIDTH_PERCENT = 30
	private const val BAR_TOP_PERCENT = 72
	private const val MIN_BAR_HEIGHT = 2
	private const val SMOOTHING = 0.95f
	private const val FADE_IN_FLOOR = 0.15f
	private const val OPAQUE = 0xFF

	fun animation(start: Long, now: Long, span: Float): Float =
		if (start > -1L) (now - start) / span else -1f

	fun markScale(guiWidth: Int): Int =
		(((guiWidth * MARK_WIDTH_PERCENT / 100) + MARK_WIDTH / 2) / MARK_WIDTH).coerceAtLeast(1)

	fun barHeight(markScale: Int): Int = markScale.coerceAtLeast(MIN_BAR_HEIGHT)

	fun barTop(guiHeight: Int): Int = guiHeight * BAR_TOP_PERCENT / 100

	fun centered(available: Int, extent: Int): Int = (available - extent) / 2

	fun handedOver(fadeOut: Float): Boolean = fadeOut >= HANDOVER

	fun revealsUnderlay(fadeOut: Float, fadeIn: Float, fadesIn: Boolean, reduced: Boolean): Boolean =
		!reduced && (fadeOut >= 1f || (fadesIn && fadeIn < 1f))

	fun canvasAlpha(fadeOut: Float, fadeIn: Float, fadesIn: Boolean, reduced: Boolean): Int =
		ceil(opacity(fadeOut, fadeIn, fadesIn, reduced, FADE_IN_FLOOR) * OPAQUE).toInt()

	fun contentOpacity(fadeOut: Float, fadeIn: Float, fadesIn: Boolean, reduced: Boolean): Float =
		opacity(fadeOut, fadeIn, fadesIn, reduced, 0f)

	private fun opacity(fadeOut: Float, fadeIn: Float, fadesIn: Boolean, reduced: Boolean, floor: Float): Float {
		if (reduced) return 1f
		if (fadeOut >= 1f) return fadeOutOpacity(fadeOut)
		if (fadesIn) return fadeIn.coerceIn(floor, 1f)
		return 1f
	}

	fun barOpacity(fadeOut: Float, reduced: Boolean): Float {
		if (reduced) return 1f
		if (fadeOut >= 1f) return 0f
		return 1f - fadeOut.coerceAtLeast(0f)
	}

	fun advanceProgress(current: Float, actual: Float, reduced: Boolean): Float {
		val target = actual.coerceIn(0f, 1f)
		if (reduced || target < current) return target
		return (current * SMOOTHING + target * (1f - SMOOTHING)).coerceIn(0f, 1f)
	}

	fun filledWidth(barWidth: Int, progress: Float): Int =
		(barWidth * progress.coerceIn(0f, 1f)).toInt().coerceIn(0, barWidth)

	fun withAlpha(color: Int, alpha: Int): Int =
		(alpha.coerceIn(0, OPAQUE) shl 24) or (color and 0xFFFFFF)

	private fun fadeOutOpacity(fadeOut: Float): Float = 1f - (fadeOut - 1f).coerceIn(0f, 1f)
}
