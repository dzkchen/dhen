package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.StringSetting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToLong

private const val PILL_GAP = 4
private const val TOGGLE_WIDTH = 24
private const val TOGGLE_KNOB_RADIUS = 5
private const val TOGGLE_KNOB_INSET = WIDGET_HEIGHT / 2
private const val SLIDER_LABEL_INSET = 1
private const val SLIDER_TRACK_INSET = 3
private const val SLIDER_TRACK_HEIGHT = 3
private const val SLIDER_KNOB_RADIUS = 3
private const val SLIDER_DRAFT_MAX_LENGTH = 32
private const val CAPTURE_PROMPT = "..."
private const val UNBOUND_LABEL = "None"
private const val DROPDOWN_GLYPH = "⌄"
internal const val LIST_PAD = 3
internal const val LIST_ROW_HEIGHT = 13
private const val LIST_TEXT_INSET = 4
private const val LIST_RADIUS = 4f
private const val LIST_ROW_RADIUS = 3f

internal class ToggleControl(private val boolean: BooleanSetting) : SettingControl(boolean) {
	private var slide = if (boolean.on) GlassGui.SETTLED else 0f
	private var slideTarget = slide
	private var slideFrom = slide
	private var slideAt = 0L

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val on = boolean.on
		val hovered = hovering(y, pointerY)
		val progress = slide()
		drawLabel(graphics, font, x + CONTROL_TEXT_INSET, labelBlockTop(font, y), DhenPalette.label(on || hovered))
		val right = x + width
		val left = right - TOGGLE_WIDTH
		val top = widgetTop(y)
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

	override fun onMeasure(font: Font, width: Int): Int = labelRoom(width, TOGGLE_WIDTH)

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
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

internal class SliderControl(private val number: NumberSetting) : EditableControl(number) {
	private val widestValue = WidestText()
	private val sizingValues = listOf(widestSliderValue(number.min, number.max, number.step))
	private var cachedValue = Double.NaN
	private var cachedText = ""
	private var valueWidth = 0

	override val maxLength: Int
		get() = SLIDER_DRAFT_MAX_LENGTH

	override fun committedText(): String = displayValue()

	override fun accepts(codepoint: Int): Boolean =
		codepoint in '0'.code..'9'.code || codepoint == '.'.code || codepoint == '-'.code

	override fun commit(text: String): Boolean {
		val parsed = text.toDoubleOrNull() ?: return false
		val before = number.value
		number.value = parsed
		return number.value != before
	}

	override fun onMeasure(font: Font, width: Int): Int {
		fitValue(font, width)
		return labelRoom(width, widestValue.width(font, sizingValues) + caretReserve + CONTROL_TEXT_INSET)
	}

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val labelTop = y + SLIDER_LABEL_INSET
		val hovered = hovering(y, pointerY)
		val value = fitValue(font, width)
		drawLabel(graphics, font, x + CONTROL_TEXT_INSET, labelTop, DhenPalette.label(hovered))
		val valueRight = x + width - CONTROL_TEXT_INSET - caretReserve
		valueText.text(graphics, font, value, valueRight - valueWidth, labelTop, DhenPalette.TEXT_PRIMARY)
		if (editing) caret(graphics, font, x + width - CONTROL_TEXT_INSET, labelTop)
		val right = x + width
		val bottom = y + rowHeight - SLIDER_TRACK_INSET
		val top = bottom - SLIDER_TRACK_HEIGHT
		val edge = RoundedGui.capsuleTrack(graphics, x, top, right, bottom, fraction().toFloat(), GlassGui.raised(hovered), DhenPalette.accent)
		val knobX = edge.coerceAtMost(right - SLIDER_KNOB_RADIUS).coerceAtLeast(x + SLIDER_KNOB_RADIUS)
		RoundedGui.circle(graphics, knobX, (top + bottom) / 2, SLIDER_KNOB_RADIUS, DhenPalette.TEXT_PRIMARY)
	}

	private fun fitValue(font: Font, width: Int): String {
		val value = valueText.fit(font, editText(), width - 2 * CONTROL_TEXT_INSET - caretReserve, editing)
		valueWidth = valueText.width(font, value)
		return value
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		val valueLeft = width - CONTROL_TEXT_INSET - valueWidth
		val trackTop = rowHeight - SLIDER_TRACK_INSET - SLIDER_TRACK_HEIGHT
		if (localX >= valueLeft && localY < trackTop) return super.onPress(localX, localY, width)
		number.value = valueAt(localX, width)
		return ControlPress.TRACK
	}

	override fun onDrag(localX: Int, localY: Int, width: Int) {
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

	override fun onInvalidateMeasurement() = widestValue.invalidate()
}

internal class CycleControl(private val selector: SelectorSetting) : SettingControl(selector) {
	private val widestValue = WidestText()

	override fun onMeasure(font: Font, width: Int): Int =
		measurePill(font, width, selector.value, widestValue.width(font, selector.options))

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(graphics, font, x, y, width, hovering(y, pointerY), selector.value, DhenPalette.TEXT_PRIMARY)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		selector.index += 1
		return ControlPress.CHANGED
	}

	override fun onInvalidateMeasurement() = widestValue.invalidate()
}

internal class DropdownControl(private val selector: SelectorSetting) : SettingControl(selector) {
	private val glyphText = memo()
	private val widestValue = WidestText()
	private val optionText = mutableListOf<TextMemo>()
	private var open = false

	override val height: Int
		get() = if (open) rowHeight + listHeight() else rowHeight

	override val expanded: Boolean
		get() = open

	override fun collapse(): Boolean {
		if (!open) return false
		open = false
		return true
	}

	override fun onMeasure(font: Font, width: Int): Int = measurePill(
		font,
		width,
		selector.value,
		widestValue.width(font, selector.options),
		PILL_GAP + glyphText.width(font, DROPDOWN_GLYPH)
	)

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val glyphWidth = glyphText.width(font, DROPDOWN_GLYPH)
		val contentRight = pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			selector.value, DhenPalette.TEXT_PRIMARY, active = open
		)
		val shownGlyph = glyphText.fit(font, DROPDOWN_GLYPH, glyphWidth)
		glyphText.text(graphics, font, shownGlyph, contentRight - glyphWidth, widgetTextTop(font, y), DhenPalette.TEXT_SECONDARY)
		if (open) drawOptions(graphics, font, x, y + rowHeight, width, pointerY)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		val option = optionAt(localY)
		if (option == ClickGuiShell.NONE) {
			open = !open
			return ControlPress.RESIZED
		}
		selector.index = option
		open = false
		return ControlPress.CHANGED
	}

	private fun listHeight(): Int = 2 * LIST_PAD + selector.options.size * LIST_ROW_HEIGHT

	private fun optionAt(localY: Int): Int {
		val top = rowHeight + LIST_PAD
		if (!open || localY < top) return ClickGuiShell.NONE
		val option = (localY - top) / LIST_ROW_HEIGHT
		return if (option < selector.options.size) option else ClickGuiShell.NONE
	}

	private fun drawOptions(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int, width: Int, pointerY: Int) {
		val options = selector.options
		val right = x + width
		RoundedGui.frame(graphics, x, top, right, top + listHeight(), LIST_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		val room = width - 2 * (LIST_PAD + LIST_TEXT_INSET)
		var rowTop = top + LIST_PAD
		for (i in options.indices) {
			val selected = i == selector.index
			val hovered = pointerY >= rowTop && pointerY < rowTop + LIST_ROW_HEIGHT
			if (selected || hovered) {
				val fill = if (selected) GlassGui.raised() else GlassGui.interactive()
				RoundedGui.fill(graphics, x + LIST_PAD, rowTop, right - LIST_PAD, rowTop + LIST_ROW_HEIGHT, LIST_ROW_RADIUS, fill)
			}
			val memo = optionMemo(i)
			val shown = memo.fit(font, options[i], room)
			val tint = when {
				selected -> DhenPalette.accent
				hovered -> DhenPalette.TEXT_PRIMARY
				else -> DhenPalette.TEXT_SECONDARY
			}
			memo.text(graphics, font, shown, x + LIST_PAD + LIST_TEXT_INSET, textTop(font, rowTop, LIST_ROW_HEIGHT), tint)
			rowTop += LIST_ROW_HEIGHT
		}
	}

	private fun optionMemo(index: Int): TextMemo {
		while (optionText.size <= index) optionText += memo()
		return optionText[index]
	}

	override fun onInvalidateMeasurement() = widestValue.invalidate()
}

internal abstract class EditableControl(setting: Setting<*>) : SettingControl(setting) {
	protected var editing = false
		private set
	private var draft = ""

	protected abstract val maxLength: Int
	protected abstract fun committedText(): String
	protected abstract fun accepts(codepoint: Int): Boolean
	protected abstract fun commit(text: String): Boolean

	override val acceptsTextInput: Boolean
		get() = editing

	override val caretReserve: Int
		get() = CARET_WIDTH

	protected open fun initialDraft(): String = committedText()

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
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

	override fun onMeasure(font: Font, width: Int): Int = measurePill(font, width, committedText())

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(graphics, font, x, y, width, hovering(y, pointerY), editText(), DhenPalette.TEXT_PRIMARY, editing = editing)
	}
}

internal class KeybindControl(private val keybind: KeybindSetting) : SettingControl(keybind) {
	private var armed = false
	private var cachedCode = Int.MIN_VALUE
	private var cachedLabel = ""

	override fun onMeasure(font: Font, width: Int): Int = measurePill(font, width, keyLabel())

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(
			graphics, font, x, y, width, hovering(y, pointerY), shownLabel(),
			if (armed) DhenPalette.accent else DhenPalette.TEXT_PRIMARY, active = armed
		)
	}

	private fun shownLabel(): String = if (armed) CAPTURE_PROMPT else keyLabel()

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
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
	override fun onMeasure(font: Font, width: Int): Int = width - 2 * PILL_PAD

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val hovered = hovering(y, pointerY)
		RoundedGui.pill(
			graphics,
			x,
			y + WIDGET_PAD,
			x + width,
			y + rowHeight - WIDGET_PAD,
			if (hovered) DhenPalette.accent else DhenPalette.accentMuted
		)
		val labelTint = if (hovered) DhenPalette.accentForeground else DhenPalette.TEXT_PRIMARY
		drawLabel(graphics, font, x + PILL_PAD, labelBlockTop(font, y), labelTint, centered = true)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		action.value.invoke()
		return ControlPress.INVOKED
	}
}

internal fun widestSliderValue(min: Double, max: Double, step: Double): String {
	val low = formatSliderValue(min).substringBefore('.')
	val high = formatSliderValue(max).substringBefore('.')
	val whole = if (low.length >= high.length) low else high
	return if (isWhole(step) && isWhole(min) && isWhole(max)) whole else "$whole.00"
}

private fun isWhole(value: Double): Boolean = value % 1.0 == 0.0

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
