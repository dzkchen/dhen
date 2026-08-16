package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util

internal object GlassGui {
	val ENTRY_MILLIS: Long get() = DhenTheme.activeOnRenderThread.entryMillis
	val ENTRY_RISE: Float get() = DhenTheme.activeOnRenderThread.entryRise
	val TAB_MILLIS: Long get() = DhenTheme.activeOnRenderThread.tabMillis
	val TAB_SLIDE: Float get() = DhenTheme.activeOnRenderThread.tabSlide
	val TOGGLE_MILLIS: Long get() = DhenTheme.activeOnRenderThread.toggleMillis
	const val SETTLED = 1f
	const val NO_BAND = 0
	const val NO_SHEEN = Int.MIN_VALUE
	private const val SHADOW_LAYERS = 3
	private const val SHEEN_CORNER_CLEARANCE = 0.5f
	private const val FRAME_SHEEN_DROP = 1
	private const val VEIL_STRENGTH = 0.9f

	fun canvas(): Int = if (Effects.reduced) DhenPalette.CANVAS else DhenPalette.GLASS_CANVAS

	fun surface(): Int = if (Effects.reduced) DhenPalette.SURFACE else DhenPalette.GLASS_SURFACE

	fun raised(): Int = if (Effects.reduced) DhenPalette.SURFACE_RAISED else DhenPalette.GLASS_SURFACE_RAISED

	fun interactive(): Int =
		if (Effects.reduced) DhenPalette.SURFACE_INTERACTIVE else DhenPalette.GLASS_SURFACE_INTERACTIVE

	fun raised(hovered: Boolean): Int = if (hovered) interactive() else raised()

	fun roundedFrame(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		fill: Int,
		border: Int,
		bandHeight: Int = NO_BAND
	) {
		roundedShadow(graphics, left, top, right, bottom, radius)
		RoundedGui.frame(graphics, left, top, right, bottom, radius, fill, border)
		roundedSheen(graphics, left, top, right, bottom, radius, bandHeight)
	}

	private fun roundedShadow(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int, radius: Float) {
		if (Effects.reduced) return
		for (layer in SHADOW_LAYERS downTo 1) {
			val color = scaleAlpha(DhenPalette.GLASS_SHADOW, 1f / layer)
			RoundedGui.border(graphics, left - layer, top - layer, right + layer, bottom + layer, radius + layer, RoundedGui.HAIRLINE, color)
		}
	}

	private fun roundedSheen(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		bandHeight: Int
	) {
		if (Effects.reduced) return
		val row = sheenTop(top, bottom, bandHeight)
		if (row == NO_SHEEN) return
		val inset = if (bandHeight > NO_BAND) HAIRLINE_INSET else sheenInset(right - left, bottom - top, radius)
		RoundedGui.pill(graphics, left + inset, row, right - inset, row + 1, DhenPalette.GLASS_SHEEN)
	}

	fun sheenTop(top: Int, bottom: Int, bandHeight: Int): Int {
		val row = top + if (bandHeight > NO_BAND) bandHeight else FRAME_SHEEN_DROP
		return if (row < bottom - HAIRLINE_INSET) row else NO_SHEEN
	}

	fun sheenInset(width: Int, height: Int, radius: Float): Int =
		(RoundedQuad.clamped(width * 0.5f, height * 0.5f, radius) * SHEEN_CORNER_CLEARANCE).toInt()

	fun scrim(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
		if (Effects.reduced) return
		SharpGui.fill(graphics, 0, 0, width, height, DhenPalette.GLASS_SCRIM)
	}

	fun veil(graphics: GuiGraphicsExtractor, width: Int, height: Int, progress: Float) {
		if (Effects.reduced) return
		val tinted = scaleAlpha(DhenPalette.GLASS_VEIL, (SETTLED - progress) * VEIL_STRENGTH)
		if (tinted ushr 24 == 0) return
		SharpGui.fill(graphics, 0, 0, width, height, tinted)
	}

	fun entryProgress(openedAt: Long): Float = tweenSince(0f, SETTLED, openedAt, ENTRY_MILLIS)

	fun tabProgress(switchedAt: Long): Float = tweenSince(0f, SETTLED, switchedAt, TAB_MILLIS)

	fun offset(progress: Float, distance: Float): Float = (SETTLED - progress) * distance

	fun tween(from: Float, target: Float, elapsed: Long, millis: Long): Float = when {
		elapsed <= 0L -> from
		elapsed >= millis -> target
		else -> from + (target - from) * ease(elapsed.toFloat() / millis)
	}

	fun tweenSince(from: Float, target: Float, startedAt: Long, millis: Long): Float =
		if (Effects.reduced) target else tween(from, target, Util.getMillis() - startedAt, millis)

	fun ease(fraction: Float): Float {
		val remaining = SETTLED - fraction
		return SETTLED - remaining * remaining * remaining
	}

	fun scaleAlpha(color: Int, factor: Float): Int {
		val alpha = ((color ushr 24) * factor).toInt().coerceIn(0, 0xFF)
		return (alpha shl 24) or (color and 0xFFFFFF)
	}
}
