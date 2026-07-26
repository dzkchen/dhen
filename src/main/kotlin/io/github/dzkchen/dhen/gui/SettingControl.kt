package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val CONTROL_TEXT_INSET = 2
internal const val CONTROL_INDICATOR = 8

internal sealed class SettingControl(val setting: Setting<*>) {
	abstract fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean)

	open fun press(localX: Int, width: Int): Boolean = false

	open fun drag(localX: Int, width: Int) = Unit

	protected fun textTop(font: Font, y: Int, height: Int): Int = y + (height - font.lineHeight) / 2
}

internal class CheckboxControl(private val boolean: BooleanSetting) : SettingControl(boolean) {
	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		if (hovered) FlatGui.fill(graphics, x, y, x + width, y + height, DhenPalette.SURFACE_INTERACTIVE)
		val color = if (boolean.value) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
		FlatGui.text(graphics, font, boolean.name, x + CONTROL_TEXT_INSET, textTop(font, y, height), color)
		val boxRight = x + width
		val boxLeft = boxRight - CONTROL_INDICATOR
		val boxTop = y + (height - CONTROL_INDICATOR) / 2
		val boxBottom = boxTop + CONTROL_INDICATOR
		if (boolean.value) FlatGui.fill(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.ACCENT)
		else FlatGui.border(graphics, boxLeft, boxTop, boxRight, boxBottom, DhenPalette.BORDER)
	}

	override fun press(localX: Int, width: Int): Boolean {
		boolean.value = !boolean.value
		return false
	}
}

internal class SliderControl(private val number: NumberSetting) : SettingControl(number) {
	private var cachedValue = Double.NaN
	private var cachedText = ""

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		FlatGui.fill(graphics, x, y, x + width, y + height, DhenPalette.SURFACE_RAISED)
		val fillWidth = (fraction() * width).roundToInt()
		FlatGui.fill(graphics, x, y, x + fillWidth, y + height, DhenPalette.ACCENT_MUTED)
		if (hovered) FlatGui.border(graphics, x, y, x + width, y + height, DhenPalette.ACCENT)
		val top = textTop(font, y, height)
		FlatGui.text(graphics, font, number.name, x + CONTROL_TEXT_INSET, top, DhenPalette.TEXT_PRIMARY)
		val value = displayValue()
		FlatGui.text(graphics, font, value, x + width - font.width(value) - CONTROL_TEXT_INSET, top, DhenPalette.TEXT_PRIMARY)
	}

	override fun press(localX: Int, width: Int): Boolean {
		number.value = valueAt(localX, width)
		return true
	}

	override fun drag(localX: Int, width: Int) {
		number.value = valueAt(localX, width)
	}

	private fun fraction(): Double {
		val range = number.max - number.min
		if (range <= 0.0) return 0.0
		return ((number.value - number.min) / range).coerceIn(0.0, 1.0)
	}

	private fun valueAt(localX: Int, width: Int): Double {
		if (width <= 0) return number.min
		val fraction = (localX.toDouble() / width).coerceIn(0.0, 1.0)
		return number.min + fraction * (number.max - number.min)
	}

	private fun displayValue(): String {
		if (number.value != cachedValue) {
			cachedValue = number.value
			cachedText = formatSliderValue(number.value)
		}
		return cachedText
	}
}

internal class DropdownControl(private val selector: SelectorSetting) : SettingControl(selector) {
	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		if (hovered) FlatGui.fill(graphics, x, y, x + width, y + height, DhenPalette.SURFACE_INTERACTIVE)
		val top = textTop(font, y, height)
		FlatGui.text(graphics, font, selector.name, x + CONTROL_TEXT_INSET, top, DhenPalette.TEXT_SECONDARY)
		val value = selector.value
		FlatGui.text(graphics, font, value, x + width - font.width(value) - CONTROL_TEXT_INSET, top, DhenPalette.TEXT_PRIMARY)
	}

	override fun press(localX: Int, width: Int): Boolean {
		selector.index += 1
		return false
	}
}

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> CheckboxControl(setting)
	is NumberSetting -> SliderControl(setting)
	is SelectorSetting -> DropdownControl(setting)
	else -> null
}

internal fun formatSliderValue(value: Double): String {
	val scaled = (value * 100.0).roundToLong()
	if (scaled % 100L == 0L) return (scaled / 100L).toString()
	val magnitude = abs(scaled)
	val whole = magnitude / 100L
	val fraction = magnitude % 100L
	val fractionText = if (fraction % 10L == 0L) (fraction / 10L).toString() else fraction.toString().padStart(2, '0')
	val sign = if (scaled < 0L) "-" else ""
	return "$sign$whole.$fractionText"
}
