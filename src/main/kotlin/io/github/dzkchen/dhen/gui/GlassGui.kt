package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util

internal object GlassGui {
	const val ENTRY_MILLIS = 180L
	const val ENTRY_RISE = 7f
	const val SETTLED = 1f
	private const val SHADOW_LAYERS = 3
	private const val SHEEN_CORNER_CLEARANCE = 0.5f
	private const val VEIL_STRENGTH = 0.9f

	fun canvas(): Int = if (Effects.reduced) DhenPalette.CANVAS else DhenPalette.GLASS_CANVAS

	fun surface(): Int = if (Effects.reduced) DhenPalette.SURFACE else DhenPalette.GLASS_SURFACE

	fun raised(): Int = if (Effects.reduced) DhenPalette.SURFACE_RAISED else DhenPalette.GLASS_SURFACE_RAISED

	fun interactive(): Int =
		if (Effects.reduced) DhenPalette.SURFACE_INTERACTIVE else DhenPalette.GLASS_SURFACE_INTERACTIVE

	fun shadow(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
		if (Effects.reduced) return
		for (layer in SHADOW_LAYERS downTo 1) {
			val color = withAlpha(DhenPalette.GLASS_SHADOW, 1f / layer)
			FlatGui.border(graphics, left - layer, top - layer, right + layer, bottom + layer, color)
		}
	}

	fun frame(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		fill: Int,
		border: Int
	) {
		shadow(graphics, left, top, right, bottom)
		FlatGui.fill(graphics, left, top, right, bottom, fill)
		FlatGui.border(graphics, left, top, right, bottom, border)
		sheen(graphics, left, top, right)
	}

	fun roundedFrame(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		fill: Int,
		border: Int
	) {
		roundedShadow(graphics, left, top, right, bottom, radius)
		RoundedGui.fill(graphics, left, top, right, bottom, radius, fill)
		RoundedGui.border(graphics, left, top, right, bottom, radius, 1f, border)
		roundedSheen(graphics, left, top, right, radius)
	}

	fun roundedShadow(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int, radius: Float) {
		if (Effects.reduced) return
		for (layer in SHADOW_LAYERS downTo 1) {
			val color = withAlpha(DhenPalette.GLASS_SHADOW, 1f / layer)
			RoundedGui.border(graphics, left - layer, top - layer, right + layer, bottom + layer, radius + layer, 1f, color)
		}
	}

	fun roundedSheen(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, radius: Float) {
		if (Effects.reduced) return
		val inset = (radius * SHEEN_CORNER_CLEARANCE).toInt()
		RoundedGui.pill(graphics, left + inset, top + 1, right - inset, top + 2, DhenPalette.GLASS_SHEEN)
	}

	fun sheen(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int) {
		if (Effects.reduced) return
		FlatGui.fill(graphics, left + 1, top + 1, right - 1, top + 2, DhenPalette.GLASS_SHEEN)
	}

	fun scrim(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
		if (Effects.reduced) return
		FlatGui.fill(graphics, 0, 0, width, height, DhenPalette.GLASS_SCRIM)
	}

	fun veil(graphics: GuiGraphicsExtractor, width: Int, height: Int, progress: Float) {
		val color = withAlpha(DhenPalette.GLASS_VEIL, (SETTLED - progress) * VEIL_STRENGTH)
		if (color ushr 24 == 0) return
		FlatGui.fill(graphics, 0, 0, width, height, color)
	}

	fun entryProgress(openedAt: Long): Float =
		if (Effects.reduced) SETTLED else progress(Util.getMillis() - openedAt)

	fun rise(progress: Float): Float = (SETTLED - progress) * ENTRY_RISE

	fun progress(elapsed: Long): Float = when {
		elapsed <= 0L -> 0f
		elapsed >= ENTRY_MILLIS -> SETTLED
		else -> ease(elapsed.toFloat() / ENTRY_MILLIS)
	}

	fun ease(fraction: Float): Float {
		val remaining = SETTLED - fraction
		return SETTLED - remaining * remaining * remaining
	}

	fun withAlpha(color: Int, factor: Float): Int {
		val alpha = ((color ushr 24) * factor).toInt().coerceIn(0, 0xFF)
		return (alpha shl 24) or (color and 0xFFFFFF)
	}
}
