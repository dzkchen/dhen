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
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.roundToLong

internal const val CONTROL_TEXT_INSET = 2
internal const val PILL_MIN_WIDTH = 26
private const val WIDGET_HEIGHT = 14
private const val WIDGET_PAD = 3
internal const val CONTROL_ROW_HEIGHT = WIDGET_HEIGHT + 2 * WIDGET_PAD
internal const val PILL_CAP = WIDGET_HEIGHT / 2
internal const val PILL_PAD = PILL_CAP + 1
private const val PILL_GAP = 4
private const val OPAQUE_ALPHA = 0xFF
private const val CARET_WIDTH = 1
private const val SWATCH_SIZE = 8
private const val SWATCH_RADIUS = 2f
private const val SWATCH_GAP = 4
private const val TOGGLE_WIDTH = 24
private const val TOGGLE_KNOB_RADIUS = 5
private const val TOGGLE_KNOB_INSET = WIDGET_HEIGHT / 2
private const val SLIDER_LABEL_INSET = 1
private const val SLIDER_TRACK_INSET = 3
private const val SLIDER_TRACK_HEIGHT = 3
private const val SLIDER_KNOB_RADIUS = 3
private const val CAPTURE_PROMPT = "..."
private const val UNBOUND_LABEL = "None"
private const val DROPDOWN_GLYPH = "⌄"
internal const val PRINTABLE_MIN = 32
internal const val PRINTABLE_MAX = 0xFFFF
internal const val DELETE_CODE = 127

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

}

internal class ToggleControl(private val boolean: BooleanSetting) : SettingControl(boolean) {
	private var slide = if (boolean.value) GlassGui.SETTLED else 0f
	private var slideTarget = slide
	private var slideFrom = slide
	private var slideAt = 0L

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val on = boolean.value
		val progress = slide()
		DhenType.text(graphics, font, boolean.name, x + CONTROL_TEXT_INSET, textTop(font, y, height), DhenPalette.label(on || hovered))
		val right = x + width
		val left = right - TOGGLE_WIDTH
		val top = widgetTop(y, height)
		val bottom = top + WIDGET_HEIGHT
		val lit = GlassGui.scaleAlpha(DhenPalette.accent, progress)
		if (lit ushr 24 != OPAQUE_ALPHA) RoundedGui.pill(graphics, left, top, right, bottom, GlassGui.raised(hovered))
		RoundedGui.pill(graphics, left, top, right, bottom, lit)
		RoundedGui.pillBorder(graphics, left, top, right, bottom, RoundedGui.HAIRLINE, DhenPalette.BORDER)
		RoundedGui.circle(
			graphics,
			RoundedQuad.between(left + TOGGLE_KNOB_INSET, right - TOGGLE_KNOB_INSET, progress),
			top + WIDGET_HEIGHT / 2,
			TOGGLE_KNOB_RADIUS,
			DhenPalette.label(on)
		)
	}

	override fun press(localX: Int, width: Int): ControlPress {
		boolean.value = !boolean.value
		return ControlPress.CHANGED
	}

	private fun slide(): Float {
		val target = if (boolean.value) GlassGui.SETTLED else 0f
		if (target != slideTarget) {
			slideTarget = target
			slideFrom = slide
			slideAt = Util.getMillis()
		}
		if (slide == target) return slide
		slide = GlassGui.tweenSince(slideFrom, target, slideAt, GlassGui.TOGGLE_MILLIS)
		return slide
	}
}

internal class SliderControl(private val number: NumberSetting) : SettingControl(number) {
	private var cachedValue = Double.NaN
	private var cachedText = ""

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val labelTop = y + SLIDER_LABEL_INSET
		DhenType.text(graphics, font, number.name, x + CONTROL_TEXT_INSET, labelTop, DhenPalette.label(hovered))
		val value = displayValue()
		DhenType.text(graphics, font, value, x + width - DhenType.width(font, value) - CONTROL_TEXT_INSET, labelTop, DhenPalette.TEXT_PRIMARY)
		val right = x + width
		val bottom = y + height - SLIDER_TRACK_INSET
		val top = bottom - SLIDER_TRACK_HEIGHT
		val edge = RoundedGui.capsuleTrack(graphics, x, top, right, bottom, fraction().toFloat(), GlassGui.raised(hovered), DhenPalette.accent)
		val knobX = edge.coerceAtMost(right - SLIDER_KNOB_RADIUS).coerceAtLeast(x + SLIDER_KNOB_RADIUS)
		RoundedGui.circle(graphics, knobX, (top + bottom) / 2, SLIDER_KNOB_RADIUS, DhenPalette.TEXT_PRIMARY)
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
		val value = selector.value
		val valueWidth = DhenType.width(font, value)
		val glyphWidth = DhenType.width(font, DROPDOWN_GLYPH)
		val contentRight = pillRow(graphics, font, selector.name, x, y, width, height, valueWidth + PILL_GAP + glyphWidth, hovered, false)
		val baseline = textTop(font, y, height)
		val glyphLeft = contentRight - glyphWidth
		DhenType.text(graphics, font, value, glyphLeft - PILL_GAP - valueWidth, baseline, DhenPalette.TEXT_PRIMARY)
		DhenType.text(graphics, font, DROPDOWN_GLYPH, glyphLeft, baseline, DhenPalette.TEXT_SECONDARY)
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

	protected fun caretReserve(): Int = if (editing) CARET_WIDTH else 0

	protected fun drawCaret(graphics: GuiGraphicsExtractor, font: Font, afterX: Int, top: Int) {
		if (editing) caret(graphics, font, afterX, top)
	}
}

internal class TextControl(private val string: StringSetting) : EditableControl(string) {
	override val maxLength: Int get() = string.maxLength

	override fun committedText(): String = string.value

	override fun accepts(codepoint: Int): Boolean = isPrintable(codepoint)

	override fun commit(text: String): Boolean {
		if (text == string.value) return false
		string.value = text
		return true
	}

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val shown = editText()
		val shownWidth = DhenType.width(font, shown)
		val reserve = caretReserve()
		val contentRight = pillRow(graphics, font, string.name, x, y, width, height, shownWidth + reserve, hovered, editing)
		val baseline = textTop(font, y, height)
		val valueX = contentRight - shownWidth - reserve
		DhenType.text(graphics, font, shown, valueX, baseline, DhenPalette.TEXT_PRIMARY)
		drawCaret(graphics, font, valueX + shownWidth, baseline)
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
		val shown = editText()
		val shownWidth = DhenType.width(font, shown)
		val reserve = caretReserve()
		val content = shownWidth + reserve + SWATCH_GAP + SWATCH_SIZE
		val swatchRight = pillRow(graphics, font, color.name, x, y, width, height, content, hovered, editing)
		val swatchLeft = swatchRight - SWATCH_SIZE
		val swatchTop = widgetTop(y, height) + (WIDGET_HEIGHT - SWATCH_SIZE) / 2
		RoundedGui.frame(graphics, swatchLeft, swatchTop, swatchRight, swatchTop + SWATCH_SIZE, SWATCH_RADIUS, color.value.argb, DhenPalette.BORDER)
		val baseline = textTop(font, y, height)
		val valueX = swatchLeft - SWATCH_GAP - shownWidth - reserve
		DhenType.text(graphics, font, shown, valueX, baseline, DhenPalette.TEXT_PRIMARY)
		drawCaret(graphics, font, valueX + shownWidth, baseline)
	}
}

internal class KeybindControl(private val keybind: KeybindSetting) : SettingControl(keybind) {
	private var armed = false
	private var cachedCode = Int.MIN_VALUE
	private var cachedLabel = ""

	override fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val shown = if (armed) CAPTURE_PROMPT else keyLabel()
		val shownWidth = DhenType.width(font, shown)
		val contentRight = pillRow(graphics, font, keybind.name, x, y, width, height, shownWidth, hovered, armed)
		val valueColor = if (armed) DhenPalette.accent else DhenPalette.TEXT_PRIMARY
		DhenType.text(graphics, font, shown, contentRight - shownWidth, textTop(font, y, height), valueColor)
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
		val top = widgetTop(y, height)
		RoundedGui.pill(graphics, x, top, x + width, top + WIDGET_HEIGHT, if (hovered) DhenPalette.accent else DhenPalette.accentMuted)
		val label = action.name
		val labelTint = if (hovered) DhenPalette.accentForeground else DhenPalette.TEXT_PRIMARY
		DhenType.text(graphics, font, label, x + (width - DhenType.width(font, label)) / 2, textTop(font, y, height), labelTint)
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

internal fun textTop(font: Font, y: Int, height: Int): Int = y + (height - DhenType.lineHeight(font)) / 2

private fun widgetTop(y: Int, height: Int): Int = y + (height - WIDGET_HEIGHT) / 2

internal fun pillLeft(x: Int, width: Int, contentWidth: Int): Int =
	(x + width - contentWidth - 2 * PILL_PAD).coerceIn(x, maxOf(x, x + width - PILL_MIN_WIDTH))

private fun pillRow(
	graphics: GuiGraphicsExtractor,
	font: Font,
	name: String,
	x: Int,
	y: Int,
	width: Int,
	height: Int,
	contentWidth: Int,
	hovered: Boolean,
	active: Boolean
): Int {
	val right = x + width
	val top = widgetTop(y, height)
	RoundedGui.pillFrame(
		graphics,
		pillLeft(x, width, contentWidth),
		top,
		right,
		top + WIDGET_HEIGHT,
		GlassGui.raised(hovered),
		if (active) DhenPalette.accent else DhenPalette.BORDER
	)
	DhenType.text(graphics, font, name, x + CONTROL_TEXT_INSET, textTop(font, y, height), DhenPalette.label(hovered))
	return right - PILL_PAD
}

internal fun caret(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int) {
	FlatGui.fill(graphics, x, top, x + CARET_WIDTH, top + DhenType.lineHeight(font), DhenPalette.TEXT_PRIMARY)
}

internal fun isPrintable(codepoint: Int): Boolean = codepoint in PRINTABLE_MIN..PRINTABLE_MAX && codepoint != DELETE_CODE

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> ToggleControl(setting)
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
