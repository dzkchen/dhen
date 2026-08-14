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
internal const val LABEL_GAP = 4
internal const val PILL_MIN_WIDTH = 26
private const val WIDGET_HEIGHT = 14
private const val WIDGET_PAD = 3
internal const val CONTROL_ROW_HEIGHT = WIDGET_HEIGHT + 2 * WIDGET_PAD
internal const val PILL_CAP = WIDGET_HEIGHT / 2
internal const val PILL_PAD = PILL_CAP + 1
private const val PILL_GAP = 4
private const val OPAQUE_ALPHA = 0xFF
internal const val CARET_WIDTH = 1
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
private const val PRINTABLE_MIN = 32
private const val PRINTABLE_MAX = 0xFFFF
private const val DELETE_CODE = 127

private val LOG = LoggerFactory.getLogger(Dhen.MOD_ID)

internal enum class ControlPress { CHANGED, TRACK, FOCUS, INVOKED }

internal enum class ControlKey { IGNORED, CONSUMED, COMMITTED, CANCELLED }

internal sealed class SettingControl(private val setting: Setting<*>) {
	private val memos = mutableListOf<TextMemo>()

	protected val labelText = memo()
	protected val valueText = memo()

	var failed = false
		private set

	val clientOwned: Boolean
		get() = setting.owner == null

	protected abstract fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean)

	protected abstract fun onPress(localX: Int, width: Int): ControlPress

	protected open fun onDrag(localX: Int, width: Int) = Unit

	protected open fun onKeyPressed(key: Int, modifiers: Int): ControlKey = ControlKey.IGNORED

	protected open fun onCharTyped(codepoint: Int): Boolean = false

	protected open fun onCaptureMouse(button: Int): ControlKey = ControlKey.IGNORED

	protected open fun onBlur(): Boolean = false

	fun renderable(): Boolean {
		if (failed) return false
		return try {
			setting.isVisible
		} catch (throwable: Throwable) {
			quarantine(throwable)
			false
		}
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		if (failed) return
		try {
			onDraw(graphics, font, x, y, width, height, hovered)
		} catch (throwable: Throwable) {
			quarantine(throwable)
		}
	}

	fun press(localX: Int, width: Int): ControlPress? {
		if (failed) return null
		return try {
			onPress(localX, width)
		} catch (throwable: Throwable) {
			quarantine(throwable)
			null
		}
	}

	fun drag(localX: Int, width: Int) {
		if (failed) return
		try {
			onDrag(localX, width)
		} catch (throwable: Throwable) {
			quarantine(throwable)
		}
	}

	fun keyPressed(key: Int, modifiers: Int): ControlKey {
		if (failed) return ControlKey.IGNORED
		return try {
			onKeyPressed(key, modifiers)
		} catch (throwable: Throwable) {
			quarantine(throwable)
			ControlKey.CANCELLED
		}
	}

	fun charTyped(codepoint: Int): Boolean {
		if (failed) return false
		return try {
			onCharTyped(codepoint)
		} catch (throwable: Throwable) {
			quarantine(throwable)
			true
		}
	}

	fun captureMouse(button: Int): ControlKey {
		if (failed) return ControlKey.IGNORED
		return try {
			onCaptureMouse(button)
		} catch (throwable: Throwable) {
			quarantine(throwable)
			ControlKey.CANCELLED
		}
	}

	fun blur(): Boolean {
		if (failed) return false
		return try {
			onBlur()
		} catch (throwable: Throwable) {
			quarantine(throwable)
			false
		}
	}

	protected fun memo(): TextMemo = DhenType.memo().also { memos += it }

	fun invalidateMeasurement() {
		for (i in memos.indices) memos[i].invalidate()
	}

	protected fun pillRow(
		graphics: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		hovered: Boolean,
		name: String,
		value: String,
		valueColor: Int,
		trailing: Int = 0,
		editing: Boolean = false,
		active: Boolean = editing
	): Int {
		val label = labelText.fit(font, name, labelRoom(width, PILL_MIN_WIDTH + trailing))
		val labelWidth = labelText.width(font, label)
		val reserve = if (editing) CARET_WIDTH else 0
		val shown = valueText.fit(font, value, pillContent(width, labelWidth, trailing) - trailing - reserve, editing)
		val shownWidth = valueText.width(font, shown)
		val right = x + width
		val top = widgetTop(y, height)
		RoundedGui.pillFrame(
			graphics,
			pillLeft(x, width, shownWidth + reserve + trailing, labelWidth),
			top,
			right,
			top + WIDGET_HEIGHT,
			GlassGui.raised(hovered),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		val baseline = textTop(font, y, height)
		labelText.text(graphics, font, label, x + CONTROL_TEXT_INSET, baseline, DhenPalette.label(hovered))
		val contentRight = right - PILL_PAD
		val valueRight = contentRight - trailing - reserve
		valueText.text(graphics, font, shown, valueRight - shownWidth, baseline, valueColor)
		if (editing) caret(graphics, font, valueRight, baseline)
		return contentRight
	}

	private fun quarantine(throwable: Throwable) {
		failed = true
		val owner = setting.owner
		if (owner == null) LOG.error("Client setting '{}' threw", setting.name, throwable)
		else owner.reportError(throwable)
	}
}

internal class ToggleControl(private val boolean: BooleanSetting) : SettingControl(boolean) {
	private var slide = if (boolean.on) GlassGui.SETTLED else 0f
	private var slideTarget = slide
	private var slideFrom = slide
	private var slideAt = 0L

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val on = boolean.on
		val progress = slide()
		val label = labelText.fit(font, boolean.name, labelRoom(width, TOGGLE_WIDTH))
		labelText.text(graphics, font, label, x + CONTROL_TEXT_INSET, textTop(font, y, height), DhenPalette.label(on || hovered))
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

	override fun onPress(localX: Int, width: Int): ControlPress {
		boolean.value = !boolean.on
		return ControlPress.CHANGED
	}

	private fun slide(): Float {
		val target = if (boolean.on) GlassGui.SETTLED else 0f
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

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val labelTop = y + SLIDER_LABEL_INSET
		val value = displayValue()
		val valueWidth = valueText.width(font, value)
		val label = labelText.fit(font, number.name, labelRoom(width, valueWidth + CONTROL_TEXT_INSET))
		labelText.text(graphics, font, label, x + CONTROL_TEXT_INSET, labelTop, DhenPalette.label(hovered))
		valueText.text(graphics, font, value, x + width - valueWidth - CONTROL_TEXT_INSET, labelTop, DhenPalette.TEXT_PRIMARY)
		val right = x + width
		val bottom = y + height - SLIDER_TRACK_INSET
		val top = bottom - SLIDER_TRACK_HEIGHT
		val edge = RoundedGui.capsuleTrack(graphics, x, top, right, bottom, fraction().toFloat(), GlassGui.raised(hovered), DhenPalette.accent)
		val knobX = edge.coerceAtMost(right - SLIDER_KNOB_RADIUS).coerceAtLeast(x + SLIDER_KNOB_RADIUS)
		RoundedGui.circle(graphics, knobX, (top + bottom) / 2, SLIDER_KNOB_RADIUS, DhenPalette.TEXT_PRIMARY)
	}

	override fun onPress(localX: Int, width: Int): ControlPress {
		number.value = valueAt(localX, width)
		return ControlPress.TRACK
	}

	override fun onDrag(localX: Int, width: Int) {
		number.value = valueAt(localX, width)
	}

	private fun fraction(): Double {
		val range = number.max - number.min
		if (range <= 0.0) return 0.0
		return ((number.amount - number.min) / range).coerceIn(0.0, 1.0)
	}

	private fun valueAt(localX: Int, width: Int): Double {
		if (width <= 0) return number.min
		val fraction = (localX.toDouble() / width).coerceIn(0.0, 1.0)
		return number.min + fraction * (number.max - number.min)
	}

	private fun displayValue(): String {
		if (number.amount != cachedValue) {
			cachedValue = number.amount
			cachedText = formatSliderValue(number.amount)
		}
		return cachedText
	}
}

internal class DropdownControl(private val selector: SelectorSetting) : SettingControl(selector) {
	private val glyphText = memo()

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val glyphWidth = glyphText.width(font, DROPDOWN_GLYPH)
		val contentRight = pillRow(
			graphics, font, x, y, width, height, hovered,
			selector.name, selector.value, DhenPalette.TEXT_PRIMARY, PILL_GAP + glyphWidth
		)
		glyphText.text(graphics, font, DROPDOWN_GLYPH, contentRight - glyphWidth, textTop(font, y, height), DhenPalette.TEXT_SECONDARY)
	}

	override fun onPress(localX: Int, width: Int): ControlPress {
		selector.index += 1
		return ControlPress.CHANGED
	}
}

internal abstract class EditableControl(setting: Setting<*>) : SettingControl(setting) {
	protected var editing = false
		private set
	private var draft = ""

	protected abstract val maxLength: Int
	protected abstract fun committedText(): String
	protected abstract fun accepts(codepoint: Int): Boolean
	protected abstract fun commit(text: String): Boolean

	protected open fun initialDraft(): String = committedText()

	override fun onPress(localX: Int, width: Int): ControlPress {
		editing = true
		draft = initialDraft()
		return ControlPress.FOCUS
	}

	override fun onCharTyped(codepoint: Int): Boolean {
		if (!editing) return false
		if (draft.length < maxLength && accepts(codepoint)) draft += codepoint.toChar()
		return true
	}

	override fun onKeyPressed(key: Int, modifiers: Int): ControlKey {
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

	override fun onBlur(): Boolean {
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

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		pillRow(graphics, font, x, y, width, height, hovered, string.name, editText(), DhenPalette.TEXT_PRIMARY, editing = editing)
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

	override fun accepts(codepoint: Int): Boolean = isPrintable(codepoint) && Character.digit(codepoint, 16) >= 0

	override fun commit(text: String): Boolean {
		val parsed = parseColor(text, color.allowAlpha) ?: return false
		val before = color.value.argb
		color.value = parsed
		return color.value.argb != before
	}

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val swatchRight = pillRow(
			graphics, font, x, y, width, height, hovered,
			color.name, editText(), DhenPalette.TEXT_PRIMARY, SWATCH_GAP + SWATCH_SIZE, editing
		)
		val swatchTop = widgetTop(y, height) + (WIDGET_HEIGHT - SWATCH_SIZE) / 2
		RoundedGui.frame(graphics, swatchRight - SWATCH_SIZE, swatchTop, swatchRight, swatchTop + SWATCH_SIZE, SWATCH_RADIUS, color.value.argb, DhenPalette.BORDER)
	}
}

internal class KeybindControl(private val keybind: KeybindSetting) : SettingControl(keybind) {
	private var armed = false
	private var cachedCode = Int.MIN_VALUE
	private var cachedLabel = ""

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		pillRow(
			graphics, font, x, y, width, height, hovered,
			keybind.name, if (armed) CAPTURE_PROMPT else keyLabel(),
			if (armed) DhenPalette.accent else DhenPalette.TEXT_PRIMARY, active = armed
		)
	}

	override fun onPress(localX: Int, width: Int): ControlPress {
		armed = true
		return ControlPress.FOCUS
	}

	override fun onKeyPressed(key: Int, modifiers: Int): ControlKey {
		if (!armed) return ControlKey.IGNORED
		armed = false
		return bind(if (key == GLFW.GLFW_KEY_ESCAPE) GLFW.GLFW_KEY_UNKNOWN else key)
	}

	override fun onCaptureMouse(button: Int): ControlKey {
		if (!armed) return ControlKey.IGNORED
		armed = false
		return bind(button)
	}

	override fun onBlur(): Boolean {
		armed = false
		return false
	}

	private fun bind(code: Int): ControlKey {
		if (keybind.code == code) return ControlKey.CANCELLED
		keybind.value = code
		return ControlKey.COMMITTED
	}

	private fun keyLabel(): String {
		val code = keybind.code
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
	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, hovered: Boolean) {
		val top = widgetTop(y, height)
		RoundedGui.pill(graphics, x, top, x + width, top + WIDGET_HEIGHT, if (hovered) DhenPalette.accent else DhenPalette.accentMuted)
		val label = labelText.fit(font, action.name, width - 2 * PILL_PAD)
		val labelTint = if (hovered) DhenPalette.accentForeground else DhenPalette.TEXT_PRIMARY
		val labelLeft = x + ClickGuiShell.centeredLeft(width, labelText.width(font, label))
		labelText.text(graphics, font, label, labelLeft, textTop(font, y, height), labelTint)
	}

	override fun onPress(localX: Int, width: Int): ControlPress {
		action.value.invoke()
		return ControlPress.INVOKED
	}
}

internal fun textTop(font: Font, y: Int, height: Int): Int = y + (height - DhenType.lineHeight(font)) / 2

private fun widgetTop(y: Int, height: Int): Int = y + (height - WIDGET_HEIGHT) / 2

internal fun labelRoom(width: Int, occupied: Int): Int = width - CONTROL_TEXT_INSET - LABEL_GAP - occupied

internal fun pillContent(width: Int, labelWidth: Int, trailing: Int): Int =
	maxOf(width - CONTROL_TEXT_INSET - labelWidth - LABEL_GAP, PILL_MIN_WIDTH + trailing) - 2 * PILL_PAD

internal fun pillLeft(x: Int, width: Int, contentWidth: Int, labelWidth: Int): Int {
	val rightmost = maxOf(x, x + width - PILL_MIN_WIDTH)
	val clearOfLabel = (x + CONTROL_TEXT_INSET + labelWidth + LABEL_GAP).coerceIn(x, rightmost)
	return (x + width - contentWidth - 2 * PILL_PAD).coerceIn(clearOfLabel, rightmost)
}

internal fun caret(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int) {
	SharpGui.fill(graphics, x, top, x + CARET_WIDTH, top + DhenType.lineHeight(font), DhenPalette.TEXT_PRIMARY)
}

internal fun isPrintable(codepoint: Int): Boolean = codepoint in PRINTABLE_MIN..PRINTABLE_MAX && codepoint != DELETE_CODE

internal fun List<SettingControl>.renderableCount(): Int {
	var count = 0
	for (i in indices) {
		if (this[i].renderable()) count++
	}
	return count
}

internal fun List<SettingControl>.invalidateMeasurements() {
	for (i in indices) this[i].invalidateMeasurement()
}

internal fun List<SettingControl>.renderableAt(position: Int): SettingControl? {
	var seen = 0
	for (i in indices) {
		val control = this[i]
		if (!control.renderable()) continue
		if (seen == position) return control
		seen++
	}
	return null
}

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
