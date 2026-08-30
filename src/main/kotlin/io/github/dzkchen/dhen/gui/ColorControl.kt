package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

private const val SWATCH_SIZE = 8
private const val SWATCH_RADIUS = 2f
private const val SWATCH_GAP = 4
internal const val PICKER_PAD = 4
internal const val PICKER_SQUARE_HEIGHT = 46
internal const val PICKER_SQUARE_TOP = CONTROL_ROW_HEIGHT + PICKER_PAD
private const val PICKER_RADIUS = 4f
private const val PICKER_STRIP_WIDTH = 8
private const val PICKER_STRIP_GAP = 4
private const val PICKER_ALPHA_HEIGHT = 8
internal const val PICKER_ALPHA_TOP = PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT + PICKER_STRIP_GAP
private const val PICKER_HUE_SEGMENTS = 6
private const val MARKER_RADIUS = 3
private const val MARKER_THICKNESS = 3
private const val CHECKER_CELL = 4
private const val SQUARE_REGION = 0
private const val HUE_REGION = 1
private const val ALPHA_REGION = 2

private val OPAQUE_BLACK = Color.rgba(0, 0, 0).argb
private val HUE_STOPS = IntArray(PICKER_HUE_SEGMENTS + 1) { Color.hsv(it.toFloat() / PICKER_HUE_SEGMENTS, 1f, 1f).argb }

internal class ColorControl(private val color: ColorSetting) : EditableControl(color) {
	private var cachedArgb = color.value.argb
	private var cachedHex = hex(color.value, color.allowAlpha)
	private var open = false
	private var tracking = SQUARE_REGION
	private var pickerArgb = color.value.argb
	private var hue = color.value.hue
	private var saturation = color.value.saturation
	private var brightness = color.value.brightness

	override val maxLength: Int get() = if (color.allowAlpha) 8 else 6

	override val height: Int
		get() = if (open) panelBottom() else rowHeight

	override val expanded: Boolean
		get() = open

	override fun collapse(): Boolean {
		if (!open) return false
		open = false
		return true
	}

	override fun initialDraft(): String = ""

	override fun committedText(): String {
		val argb = color.value.argb
		if (argb != cachedArgb) {
			cachedArgb = argb
			cachedHex = hex(color.value, color.allowAlpha)
		}
		return cachedHex
	}

	override fun accepts(codepoint: Int): Boolean = isPrintable(codepoint) && Character.digit(codepoint, 16) >= 0

	override fun commit(text: String): Boolean {
		val parsed = parseColor(text, color.allowAlpha) ?: return false
		val before = color.value.argb
		color.value = parsed
		return color.value.argb != before
	}

	override fun onMeasure(font: Font, width: Int): Int =
		measurePill(font, width, committedText(), trailing = SWATCH_GAP + SWATCH_SIZE)

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		sync()
		val value = color.value
		val swatchRight = pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			editText(), DhenPalette.TEXT_PRIMARY, editing = editing, active = editing || open
		)
		val swatchLeft = swatchRight - SWATCH_SIZE
		val swatchTop = widgetTop(y) + (WIDGET_HEIGHT - SWATCH_SIZE) / 2
		val swatchBottom = swatchTop + SWATCH_SIZE
		if (value.alpha != OPAQUE_ALPHA) {
			checkerboard(
				graphics,
				swatchLeft + HAIRLINE_INSET,
				swatchTop + HAIRLINE_INSET,
				swatchRight - HAIRLINE_INSET,
				swatchBottom - HAIRLINE_INSET
			)
		}
		RoundedGui.frame(graphics, swatchLeft, swatchTop, swatchRight, swatchBottom, SWATCH_RADIUS, value.argb, DhenPalette.BORDER)
		if (open) drawPanel(graphics, x, y, width, value)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		if (localY < rowHeight) {
			if (localX < swatchHit(width)) return super.onPress(localX, localY, width)
			open = !open
			return ControlPress.RESIZED
		}
		sync()
		tracking = regionAt(localX, localY, width)
		if (tracking == ClickGuiShell.NONE) return ControlPress.IGNORED
		onDrag(localX, localY, width)
		return ControlPress.TRACK
	}

	override fun onDrag(localX: Int, localY: Int, width: Int) {
		var level = color.value.alpha
		when (tracking) {
			HUE_REGION -> hue = fractionOf(localY - squareTop(), PICKER_SQUARE_HEIGHT)
			ALPHA_REGION -> level = channelOf(fractionOf(localX - PICKER_PAD, width - 2 * PICKER_PAD))
			SQUARE_REGION -> {
				saturation = fractionOf(localX - PICKER_PAD, pickerSquareRight(width) - PICKER_PAD)
				brightness = 1f - fractionOf(localY - squareTop(), PICKER_SQUARE_HEIGHT)
			}
			else -> return
		}
		color.value = Color.hsv(hue, saturation, brightness, level)
		pickerArgb = color.value.argb
	}

	private fun squareTop(): Int = PICKER_SQUARE_TOP + rowGrowth

	private fun alphaTop(): Int = PICKER_ALPHA_TOP + rowGrowth

	private fun panelBottom(): Int =
		PICKER_PAD + if (color.allowAlpha) alphaTop() + PICKER_ALPHA_HEIGHT else squareTop() + PICKER_SQUARE_HEIGHT

	private fun regionAt(localX: Int, localY: Int, width: Int): Int = when {
		localY < squareTop() -> ClickGuiShell.NONE
		color.allowAlpha && localY >= alphaTop() ->
			if (localY < alphaTop() + PICKER_ALPHA_HEIGHT) ALPHA_REGION else ClickGuiShell.NONE
		localY >= squareTop() + PICKER_SQUARE_HEIGHT -> ClickGuiShell.NONE
		localX >= pickerStripLeft(width) -> HUE_REGION
		else -> SQUARE_REGION
	}

	private fun sync() {
		val value = color.value
		if (value.argb == pickerArgb) return
		pickerArgb = value.argb
		brightness = value.brightness
		if (brightness == 0f) return
		saturation = value.saturation
		if (saturation > 0f) hue = value.hue
	}

	private fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, value: Color) {
		RoundedGui.frame(graphics, x, y + rowHeight, x + width, y + panelBottom(), PICKER_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		val left = x + PICKER_PAD
		val right = x + pickerSquareRight(width)
		val top = y + squareTop()
		val tint = Color.hsv(hue, 1f, 1f)
		GradientGui.quad(graphics, left, top, right, top + PICKER_SQUARE_HEIGHT, Color.hsv(hue, 0f, 1f).argb, tint.argb, OPAQUE_BLACK, OPAQUE_BLACK)
		val opaque = value.opaque().argb
		val markerX = left + (saturation * (right - left)).roundToInt()
		val markerY = top + ((1f - brightness) * PICKER_SQUARE_HEIGHT).roundToInt()
		RoundedGui.circle(graphics, markerX, markerY, MARKER_RADIUS, inkOn(opaque))
		RoundedGui.circle(graphics, markerX, markerY, MARKER_RADIUS - 1, opaque)
		drawHueStrip(graphics, x + pickerStripLeft(width), top, tint.argb)
		if (color.allowAlpha) drawAlphaStrip(graphics, left, y + alphaTop(), x + width - PICKER_PAD, value.alpha)
	}

	private fun drawHueStrip(graphics: GuiGraphicsExtractor, left: Int, top: Int, tint: Int) {
		val right = left + PICKER_STRIP_WIDTH
		for (segment in 0 until PICKER_HUE_SEGMENTS) {
			GradientGui.vertical(
				graphics,
				left, top + PICKER_SQUARE_HEIGHT * segment / PICKER_HUE_SEGMENTS, right, top + PICKER_SQUARE_HEIGHT * (segment + 1) / PICKER_HUE_SEGMENTS,
				HUE_STOPS[segment],
				HUE_STOPS[segment + 1]
			)
		}
		val markerTop = (top + (hue * PICKER_SQUARE_HEIGHT).roundToInt()).coerceAtMost(top + PICKER_SQUARE_HEIGHT - MARKER_THICKNESS)
		RoundedGui.pillBorder(graphics, left, markerTop, right, markerTop + MARKER_THICKNESS, RoundedGui.HAIRLINE, inkOn(tint))
	}

	private fun drawAlphaStrip(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, level: Int) {
		val bottom = top + PICKER_ALPHA_HEIGHT
		checkerboard(graphics, left, top, right, bottom)
		val solid = Color.hsv(hue, saturation, brightness)
		GradientGui.horizontal(graphics, left, top, right, bottom, solid.rgb, solid.argb)
		val markerLeft = (left + level * (right - left) / OPAQUE_ALPHA).coerceAtMost(right - MARKER_THICKNESS)
		RoundedGui.pillBorder(graphics, markerLeft, top, markerLeft + MARKER_THICKNESS, bottom, RoundedGui.HAIRLINE, inkOn(solid.argb))
	}
}

private fun swatchHit(width: Int): Int = width - PILL_PAD - SWATCH_GAP - SWATCH_SIZE

private fun pickerSquareRight(width: Int): Int = pickerStripLeft(width) - PICKER_STRIP_GAP

internal fun pickerStripLeft(width: Int): Int = width - PICKER_PAD - PICKER_STRIP_WIDTH

private fun fractionOf(offset: Int, span: Int): Float =
	if (span <= 0) 0f else (offset.toFloat() / span).coerceIn(0f, 1f)

private fun channelOf(fraction: Float): Int = (fraction * OPAQUE_ALPHA).roundToInt()

private fun inkOn(color: Int): Int =
	if (DhenPalette.contrast(color, DhenPalette.TEXT_PRIMARY) >= DhenPalette.contrast(color, DhenPalette.TEXT_ON_ACCENT))
		DhenPalette.TEXT_PRIMARY
	else DhenPalette.TEXT_ON_ACCENT

private fun checkerboard(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
	SharpGui.fill(graphics, left, top, right, bottom, DhenPalette.BORDER)
	var cellTop = top
	var offset = 0
	while (cellTop < bottom) {
		var cellLeft = left + offset
		while (cellLeft < right) {
			SharpGui.fill(graphics, cellLeft, cellTop, minOf(cellLeft + CHECKER_CELL, right), minOf(cellTop + CHECKER_CELL, bottom), DhenPalette.TEXT_DISABLED)
			cellLeft += 2 * CHECKER_CELL
		}
		cellTop += CHECKER_CELL
		offset = CHECKER_CELL - offset
	}
}

private fun parseColor(text: String, allowAlpha: Boolean): Color? {
	if (text.length != 6 && !(allowAlpha && text.length == 8)) return null
	val red = text.substring(0, 2).toIntOrNull(16) ?: return null
	val green = text.substring(2, 4).toIntOrNull(16) ?: return null
	val blue = text.substring(4, 6).toIntOrNull(16) ?: return null
	val alpha = if (text.length == 8) text.substring(6, 8).toIntOrNull(16) ?: return null else 0xFF
	return Color.rgba(red, green, blue, alpha)
}

private fun hex(color: Color, allowAlpha: Boolean): String {
	val body = "%02X%02X%02X".format(color.red, color.green, color.blue)
	return if (allowAlpha) body + "%02X".format(color.alpha) else body
}
