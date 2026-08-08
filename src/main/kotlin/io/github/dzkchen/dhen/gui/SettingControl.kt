package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val CONTROL_TEXT_INSET = 2
internal const val CONTROL_INDICATOR = 8
private const val CARET_WIDTH = 1
private const val SWATCH_GAP = 3
private const val CAPTURE_PROMPT = "..."
private const val UNBOUND_LABEL = "None"
private const val PRINTABLE_MIN = 32
private const val PRINTABLE_MAX = 0xFFFF
private const val DELETE_CODE = 127

private val LOG = LoggerFactory.getLogger(Dhen.MOD_ID)

internal enum class ControlPress { NONE, CHANGED, TRACK, FOCUS, INVOKED }

internal enum class ControlKey { IGNORED, CONSUMED, COMMITTED, CANCELLED }

internal sealed class SettingControl(val setting: Setting<*>) {
	abstract fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean)

	open fun press(localX: Int, width: Int): ControlPress = ControlPress.NONE

	open fun drag(localX: Int, width: Int) = Unit

	open fun keyPressed(key: Int, modifiers: Int): ControlKey = ControlKey.IGNORED

	open fun charTyped(codepoint: Int): Boolean = false

	open fun captureMouse(button: Int): ControlKey = ControlKey.IGNORED

	open fun blur(): Boolean = false

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

	override fun press(localX: Int, width: Int): ControlPress {
		boolean.value = !boolean.value
		return ControlPress.CHANGED
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

	override fun press(localX: Int, width: Int): ControlPress {
		number.value = valueAt(localX, width)
		return ControlPress.TRACK
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

	override fun press(localX: Int, width: Int): ControlPress {
		selector.index += 1
		return ControlPress.CHANGED
	}
}

internal abstract class EditableControl(setting: Setting<*>) : SettingControl(setting) {
	protected var editing = false
		private set
	protected var draft = ""
		private set

	protected abstract val maxLength: Int
	protected abstract fun committedText(): String
	protected abstract fun accepts(codepoint: Int): Boolean
	protected abstract fun commit(text: String): Boolean

	protected open fun initialDraft(): String = committedText()

	override fun press(localX: Int, width: Int): ControlPress {
		editing = true
		draft = initialDraft()
		return ControlPress.FOCUS
	}

	override fun charTyped(codepoint: Int): Boolean {
		if (!editing) return false
		if (draft.length < maxLength && accepts(codepoint)) draft += codepoint.toChar()
		return true
	}

	override fun keyPressed(key: Int, modifiers: Int): ControlKey {
		if (!editing) return ControlKey.IGNORED
		return when (key) {
			GLFW.GLFW_KEY_BACKSPACE -> {
				if (draft.isNotEmpty()) draft = draft.substring(0, draft.length - 1)
				ControlKey.CONSUMED
			}
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finish(commit(draft))
			GLFW.GLFW_KEY_ESCAPE -> finish(false)
			else -> ControlKey.CONSUMED
		}
	}

	override fun blur(): Boolean {
		if (!editing) return false
		val changed = commit(draft)
		editing = false
		return changed
	}

	private fun finish(changed: Boolean): ControlKey {
		editing = false
		return if (changed) ControlKey.COMMITTED else ControlKey.CANCELLED
	}

	protected fun editText(): String = if (editing) draft else committedText()

	protected fun drawCaret(graphics: GuiGraphicsExtractor, font: Font, afterX: Int, top: Int) {
		if (editing) FlatGui.fill(graphics, afterX, top, afterX + CARET_WIDTH, top + font.lineHeight, DhenPalette.TEXT_PRIMARY)
	}

	protected fun drawFocusFrame(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		if (editing) FlatGui.border(graphics, x, y, x + width, y + height, DhenPalette.ACCENT)
		else if (hovered) FlatGui.fill(graphics, x, y, x + width, y + height, DhenPalette.SURFACE_INTERACTIVE)
	}
}

internal class TextControl(private val string: StringSetting) : EditableControl(string) {
	override val maxLength: Int get() = string.maxLength

	override fun committedText(): String = string.value

	override fun accepts(codepoint: Int): Boolean = codepoint in PRINTABLE_MIN..PRINTABLE_MAX && codepoint != DELETE_CODE

	override fun commit(text: String): Boolean {
		if (text == string.value) return false
		string.value = text
		return true
	}

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		drawFocusFrame(graphics, x, y, width, height, hovered)
		val top = textTop(font, y, height)
		FlatGui.text(graphics, font, string.name, x + CONTROL_TEXT_INSET, top, DhenPalette.TEXT_SECONDARY)
		val shown = editText()
		val caretReserve = if (editing) CARET_WIDTH else 0
		val valueX = x + width - font.width(shown) - CONTROL_TEXT_INSET - caretReserve
		FlatGui.text(graphics, font, shown, valueX, top, DhenPalette.TEXT_PRIMARY)
		drawCaret(graphics, font, valueX + font.width(shown), top)
	}
}

internal class ColorControl(private val color: ColorSetting) : EditableControl(color) {
	private var cacheValid = false
	private var cachedArgb = 0
	private var cachedHex = ""

	override val maxLength: Int get() = if (color.allowAlpha) 8 else 6

	override fun initialDraft(): String = ""

	override fun committedText(): String {
		val argb = color.value.argb
		if (!cacheValid || argb != cachedArgb) {
			cacheValid = true
			cachedArgb = argb
			cachedHex = hex(color.value, color.allowAlpha)
		}
		return cachedHex
	}

	override fun accepts(codepoint: Int): Boolean = Character.digit(codepoint, 16) >= 0

	override fun commit(text: String): Boolean {
		val parsed = parseColor(text, color.allowAlpha) ?: return false
		val before = color.value.argb
		color.value = parsed
		return color.value.argb != before
	}

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		drawFocusFrame(graphics, x, y, width, height, hovered)
		val top = textTop(font, y, height)
		FlatGui.text(graphics, font, color.name, x + CONTROL_TEXT_INSET, top, DhenPalette.TEXT_SECONDARY)
		val swatchRight = x + width - CONTROL_TEXT_INSET
		val swatchLeft = swatchRight - CONTROL_INDICATOR
		val swatchTop = y + (height - CONTROL_INDICATOR) / 2
		val swatchBottom = swatchTop + CONTROL_INDICATOR
		FlatGui.fill(graphics, swatchLeft, swatchTop, swatchRight, swatchBottom, color.value.argb)
		FlatGui.border(graphics, swatchLeft, swatchTop, swatchRight, swatchBottom, DhenPalette.BORDER)
		val shown = editText()
		val caretReserve = if (editing) CARET_WIDTH else 0
		val valueX = swatchLeft - SWATCH_GAP - font.width(shown) - caretReserve
		FlatGui.text(graphics, font, shown, valueX, top, DhenPalette.TEXT_PRIMARY)
		drawCaret(graphics, font, valueX + font.width(shown), top)
	}
}

internal class KeybindControl(private val keybind: KeybindSetting) : SettingControl(keybind) {
	private var armed = false
	private var cachedCode = Int.MIN_VALUE
	private var cachedLabel = ""

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		if (armed) FlatGui.border(graphics, x, y, x + width, y + height, DhenPalette.ACCENT)
		else if (hovered) FlatGui.fill(graphics, x, y, x + width, y + height, DhenPalette.SURFACE_INTERACTIVE)
		val top = textTop(font, y, height)
		FlatGui.text(graphics, font, keybind.name, x + CONTROL_TEXT_INSET, top, DhenPalette.TEXT_SECONDARY)
		val shown = if (armed) CAPTURE_PROMPT else keyLabel()
		FlatGui.text(graphics, font, shown, x + width - font.width(shown) - CONTROL_TEXT_INSET, top, DhenPalette.TEXT_PRIMARY)
	}

	override fun press(localX: Int, width: Int): ControlPress {
		armed = true
		return ControlPress.FOCUS
	}

	override fun keyPressed(key: Int, modifiers: Int): ControlKey {
		if (!armed) return ControlKey.IGNORED
		armed = false
		return bind(if (key == GLFW.GLFW_KEY_ESCAPE) GLFW.GLFW_KEY_UNKNOWN else key)
	}

	override fun captureMouse(button: Int): ControlKey {
		if (!armed) return ControlKey.IGNORED
		armed = false
		return bind(button)
	}

	override fun blur(): Boolean {
		armed = false
		return false
	}

	private fun bind(code: Int): ControlKey {
		if (keybind.value == code) return ControlKey.CANCELLED
		keybind.value = code
		return ControlKey.COMMITTED
	}

	private fun keyLabel(): String {
		val code = keybind.value
		if (code != cachedCode) {
			cachedCode = code
			cachedLabel = displayName(code)
		}
		return cachedLabel
	}

	private fun displayName(code: Int): String = when {
		code == GLFW.GLFW_KEY_UNKNOWN -> UNBOUND_LABEL
		code <= GLFW.GLFW_MOUSE_BUTTON_LAST -> InputConstants.Type.MOUSE.getOrCreate(code).displayName.string
		else -> InputConstants.Type.KEYSYM.getOrCreate(code).displayName.string
	}
}

internal class ActionControl(private val action: ActionSetting) : SettingControl(action) {
	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val background = if (hovered) DhenPalette.ACCENT_MUTED else DhenPalette.SURFACE_RAISED
		FlatGui.fill(graphics, x, y, x + width, y + height, background)
		FlatGui.border(graphics, x, y, x + width, y + height, DhenPalette.BORDER)
		val label = action.name
		FlatGui.text(graphics, font, label, x + (width - font.width(label)) / 2, textTop(font, y, height), DhenPalette.TEXT_PRIMARY)
	}

	override fun press(localX: Int, width: Int): ControlPress {
		try {
			action.value.invoke()
		} catch (e: Exception) {
			LOG.warn("Action setting '{}' threw", action.name, e)
		}
		return ControlPress.INVOKED
	}
}

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> CheckboxControl(setting)
	is NumberSetting -> SliderControl(setting)
	is SelectorSetting -> DropdownControl(setting)
	is StringSetting -> TextControl(setting)
	is ColorSetting -> ColorControl(setting)
	is KeybindSetting -> KeybindControl(setting)
	is ActionSetting -> ActionControl(setting)
	else -> null
}

internal fun parseColor(text: String, allowAlpha: Boolean): Color? {
	if (text.length != 6 && !(allowAlpha && text.length == 8)) return null
	val red = text.substring(0, 2).toIntOrNull(16) ?: return null
	val green = text.substring(2, 4).toIntOrNull(16) ?: return null
	val blue = text.substring(4, 6).toIntOrNull(16) ?: return null
	val alpha = if (text.length == 8) text.substring(6, 8).toIntOrNull(16) ?: return null else 0xFF
	return Color.rgba(red, green, blue, alpha)
}

internal fun hex(color: Color, allowAlpha: Boolean): String {
	val body = "%02X%02X%02X".format(color.red, color.green, color.blue)
	return if (allowAlpha) body + "%02X".format(color.alpha) else body
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
