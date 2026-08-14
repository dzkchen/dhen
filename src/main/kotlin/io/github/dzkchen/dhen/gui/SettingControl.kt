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
import java.util.function.IntUnaryOperator
import kotlin.math.abs
import kotlin.math.roundToInt
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
private const val LISTED_FROM_OPTIONS = 3
private const val LIST_PAD = 3
private const val LIST_ROW_HEIGHT = 13
private const val LIST_TEXT_INSET = 4
private const val LIST_RADIUS = 4f
private const val LIST_ROW_RADIUS = 3f
private const val PRINTABLE_MIN = 32
private const val PRINTABLE_MAX = 0xFFFF
private const val DELETE_CODE = 127
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

private val LOG = LoggerFactory.getLogger(Dhen.MOD_ID)
private val OPAQUE_BLACK = Color.rgba(0, 0, 0).argb

internal enum class ControlPress { CHANGED, RESIZED, TRACK, FOCUS, INVOKED }

internal enum class ControlKey { IGNORED, CONSUMED, COMMITTED, CANCELLED }

internal sealed class SettingControl(private val setting: Setting<*>) {
	private val memos = mutableListOf<TextMemo>()

	protected val labelText = memo()
	protected val valueText = memo()

	var failed = false
		private set

	val clientOwned: Boolean
		get() = setting.owner == null

	open val height: Int
		get() = CONTROL_ROW_HEIGHT

	open val expanded: Boolean
		get() = false

	val extent: Int
		get() = if (renderable()) height else 0

	open fun collapse(): Boolean = false

	protected abstract fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int)

	protected abstract fun onPress(localX: Int, localY: Int, width: Int): ControlPress

	protected open fun onDrag(localX: Int, localY: Int, width: Int) = Unit

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

	fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		if (failed) return
		try {
			onDraw(graphics, font, x, y, width, pointerY)
		} catch (throwable: Throwable) {
			quarantine(throwable)
		}
	}

	fun press(localX: Int, localY: Int, width: Int): ControlPress? {
		if (failed) return null
		return try {
			onPress(localX, localY, width)
		} catch (throwable: Throwable) {
			quarantine(throwable)
			null
		}
	}

	fun drag(localX: Int, localY: Int, width: Int) {
		if (failed) return
		try {
			onDrag(localX, localY, width)
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
		val top = widgetTop(y)
		RoundedGui.pillFrame(
			graphics,
			pillLeft(x, width, shownWidth + reserve + trailing, labelWidth),
			top,
			right,
			top + WIDGET_HEIGHT,
			GlassGui.raised(hovered),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		val baseline = rowTextTop(font, y)
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

internal class ControlBody(private val controls: List<SettingControl>) {
	private val extentAt = IntUnaryOperator { index -> controls[index].extent }

	val indices: IntRange
		get() = controls.indices
	val height: Int
		get() = ClickGuiShell.spanTotal(controls.size, extentAt, NO_GAP)

	fun at(index: Int): SettingControl = controls[index]

	fun topOf(index: Int): Int = ClickGuiShell.spanStart(index, extentAt, NO_GAP)

	fun indexAt(localY: Int): Int = ClickGuiShell.spanAt(localY, controls.size, extentAt, NO_GAP)

	fun invalidateMeasurements() {
		for (i in controls.indices) controls[i].invalidateMeasurement()
	}

	private companion object {
		const val NO_GAP = 0
	}
}

internal class ToggleControl(private val boolean: BooleanSetting) : SettingControl(boolean) {
	private var slide = if (boolean.on) GlassGui.SETTLED else 0f
	private var slideTarget = slide
	private var slideFrom = slide
	private var slideAt = 0L

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val on = boolean.on
		val hovered = hovering(y, pointerY)
		val progress = slide()
		val label = labelText.fit(font, boolean.name, labelRoom(width, TOGGLE_WIDTH))
		labelText.text(graphics, font, label, x + CONTROL_TEXT_INSET, rowTextTop(font, y), DhenPalette.label(on || hovered))
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

internal class SliderControl(private val number: NumberSetting) : SettingControl(number) {
	private var cachedValue = Double.NaN
	private var cachedText = ""

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val labelTop = y + SLIDER_LABEL_INSET
		val hovered = hovering(y, pointerY)
		val value = displayValue()
		val valueWidth = valueText.width(font, value)
		val label = labelText.fit(font, number.name, labelRoom(width, valueWidth + CONTROL_TEXT_INSET))
		labelText.text(graphics, font, label, x + CONTROL_TEXT_INSET, labelTop, DhenPalette.label(hovered))
		valueText.text(graphics, font, value, x + width - valueWidth - CONTROL_TEXT_INSET, labelTop, DhenPalette.TEXT_PRIMARY)
		val right = x + width
		val bottom = y + CONTROL_ROW_HEIGHT - SLIDER_TRACK_INSET
		val top = bottom - SLIDER_TRACK_HEIGHT
		val edge = RoundedGui.capsuleTrack(graphics, x, top, right, bottom, fraction().toFloat(), GlassGui.raised(hovered), DhenPalette.accent)
		val knobX = edge.coerceAtMost(right - SLIDER_KNOB_RADIUS).coerceAtLeast(x + SLIDER_KNOB_RADIUS)
		RoundedGui.circle(graphics, knobX, (top + bottom) / 2, SLIDER_KNOB_RADIUS, DhenPalette.TEXT_PRIMARY)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
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
}

internal class CycleControl(private val selector: SelectorSetting) : SettingControl(selector) {
	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			selector.name, selector.value, DhenPalette.TEXT_PRIMARY
		)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		selector.index += 1
		return ControlPress.CHANGED
	}
}

internal class DropdownControl(private val selector: SelectorSetting) : SettingControl(selector) {
	private val glyphText = memo()
	private val optionText = mutableListOf<TextMemo>()
	private var listed = false

	override val height: Int
		get() = if (listed) CONTROL_ROW_HEIGHT + listHeight() else CONTROL_ROW_HEIGHT

	override val expanded: Boolean
		get() = listed

	override fun collapse(): Boolean {
		if (!listed) return false
		listed = false
		return true
	}

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val glyphWidth = glyphText.width(font, DROPDOWN_GLYPH)
		val contentRight = pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			selector.name, selector.value, DhenPalette.TEXT_PRIMARY, PILL_GAP + glyphWidth, active = listed
		)
		glyphText.text(graphics, font, DROPDOWN_GLYPH, contentRight - glyphWidth, rowTextTop(font, y), DhenPalette.TEXT_SECONDARY)
		if (listed) drawOptions(graphics, font, x, y + CONTROL_ROW_HEIGHT, width, pointerY)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		val option = optionAt(localY)
		if (option == ClickGuiShell.NONE) {
			listed = !listed
			return ControlPress.RESIZED
		}
		selector.index = option
		listed = false
		return ControlPress.CHANGED
	}

	private fun listHeight(): Int = 2 * LIST_PAD + selector.options.size * LIST_ROW_HEIGHT

	private fun optionAt(localY: Int): Int {
		val top = CONTROL_ROW_HEIGHT + LIST_PAD
		if (!listed || localY < top) return ClickGuiShell.NONE
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

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(graphics, font, x, y, width, hovering(y, pointerY), string.name, editText(), DhenPalette.TEXT_PRIMARY, editing = editing)
	}
}

internal class ColorControl(private val color: ColorSetting) : EditableControl(color) {
	private var cacheValid = false
	private var cachedArgb = 0
	private var cachedHex = ""
	private var open = false
	private var tracking = SQUARE_REGION
	private var pickerArgb = color.value.argb
	private var hue = color.value.hue
	private var saturation = color.value.saturation
	private var brightness = color.value.brightness

	override val maxLength: Int get() = if (color.allowAlpha) 8 else 6

	override val height: Int
		get() = if (open) panelBottom() else CONTROL_ROW_HEIGHT

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

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		sync()
		val value = color.value
		val swatchRight = pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			color.name, editText(), DhenPalette.TEXT_PRIMARY, SWATCH_GAP + SWATCH_SIZE, editing, editing || open
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
		if (localY < CONTROL_ROW_HEIGHT) {
			if (localX < swatchHit(width)) return super.onPress(localX, localY, width)
			open = !open
			return ControlPress.RESIZED
		}
		sync()
		tracking = regionAt(localX, localY, width)
		onDrag(localX, localY, width)
		return ControlPress.TRACK
	}

	override fun onDrag(localX: Int, localY: Int, width: Int) {
		var level = color.value.alpha
		when (tracking) {
			HUE_REGION -> hue = fractionOf(localY - PICKER_SQUARE_TOP, PICKER_SQUARE_HEIGHT)
			ALPHA_REGION -> level = channelOf(fractionOf(localX - PICKER_PAD, width - 2 * PICKER_PAD))
			SQUARE_REGION -> {
				saturation = fractionOf(localX - PICKER_PAD, pickerSquareRight(width) - PICKER_PAD)
				brightness = 1f - fractionOf(localY - PICKER_SQUARE_TOP, PICKER_SQUARE_HEIGHT)
			}
			else -> return
		}
		color.value = Color.hsv(hue, saturation, brightness, level)
		pickerArgb = color.value.argb
	}

	private fun panelBottom(): Int =
		PICKER_PAD + if (color.allowAlpha) PICKER_ALPHA_TOP + PICKER_ALPHA_HEIGHT else PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT

	private fun regionAt(localX: Int, localY: Int, width: Int): Int = when {
		localY < PICKER_SQUARE_TOP -> ClickGuiShell.NONE
		color.allowAlpha && localY >= PICKER_ALPHA_TOP ->
			if (localY < PICKER_ALPHA_TOP + PICKER_ALPHA_HEIGHT) ALPHA_REGION else ClickGuiShell.NONE
		localY >= PICKER_SQUARE_TOP + PICKER_SQUARE_HEIGHT -> ClickGuiShell.NONE
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
		RoundedGui.frame(graphics, x, y + CONTROL_ROW_HEIGHT, x + width, y + panelBottom(), PICKER_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		val left = x + PICKER_PAD
		val right = x + pickerSquareRight(width)
		val top = y + PICKER_SQUARE_TOP
		val tint = Color.hsv(hue, 1f, 1f)
		GradientGui.quad(graphics, left, top, right, top + PICKER_SQUARE_HEIGHT, Color.hsv(hue, 0f, 1f).argb, tint.argb, OPAQUE_BLACK, OPAQUE_BLACK)
		val opaque = value.opaque().argb
		val markerX = left + (saturation * (right - left)).roundToInt()
		val markerY = top + ((1f - brightness) * PICKER_SQUARE_HEIGHT).roundToInt()
		RoundedGui.circle(graphics, markerX, markerY, MARKER_RADIUS, inkOn(opaque))
		RoundedGui.circle(graphics, markerX, markerY, MARKER_RADIUS - 1, opaque)
		drawHueStrip(graphics, x + pickerStripLeft(width), top, tint.argb)
		if (color.allowAlpha) drawAlphaStrip(graphics, left, y + PICKER_ALPHA_TOP, x + width - PICKER_PAD, value.alpha)
	}

	private fun drawHueStrip(graphics: GuiGraphicsExtractor, left: Int, top: Int, tint: Int) {
		val right = left + PICKER_STRIP_WIDTH
		for (segment in 0 until PICKER_HUE_SEGMENTS) {
			GradientGui.vertical(
				graphics,
				left, top + PICKER_SQUARE_HEIGHT * segment / PICKER_HUE_SEGMENTS, right, top + PICKER_SQUARE_HEIGHT * (segment + 1) / PICKER_HUE_SEGMENTS,
				Color.hsv(segment.toFloat() / PICKER_HUE_SEGMENTS, 1f, 1f).argb,
				Color.hsv((segment + 1).toFloat() / PICKER_HUE_SEGMENTS, 1f, 1f).argb
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

internal class KeybindControl(private val keybind: KeybindSetting) : SettingControl(keybind) {
	private var armed = false
	private var cachedCode = Int.MIN_VALUE
	private var cachedLabel = ""

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		pillRow(
			graphics, font, x, y, width, hovering(y, pointerY),
			keybind.name, if (armed) CAPTURE_PROMPT else keyLabel(),
			if (armed) DhenPalette.accent else DhenPalette.TEXT_PRIMARY, active = armed
		)
	}

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
	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val hovered = hovering(y, pointerY)
		val top = widgetTop(y)
		RoundedGui.pill(graphics, x, top, x + width, top + WIDGET_HEIGHT, if (hovered) DhenPalette.accent else DhenPalette.accentMuted)
		val label = labelText.fit(font, action.name, width - 2 * PILL_PAD)
		val labelTint = if (hovered) DhenPalette.accentForeground else DhenPalette.TEXT_PRIMARY
		val labelLeft = x + ClickGuiShell.centeredLeft(width, labelText.width(font, label))
		labelText.text(graphics, font, label, labelLeft, rowTextTop(font, y), labelTint)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		action.value.invoke()
		return ControlPress.INVOKED
	}
}

internal fun textTop(font: Font, y: Int, height: Int): Int = y + (height - DhenType.lineHeight(font)) / 2

private fun rowTextTop(font: Font, y: Int): Int = textTop(font, y, CONTROL_ROW_HEIGHT)

private fun hovering(y: Int, pointerY: Int): Boolean = pointerY >= y && pointerY < y + CONTROL_ROW_HEIGHT

private fun widgetTop(y: Int): Int = y + WIDGET_PAD

internal fun labelRoom(width: Int, occupied: Int): Int = width - CONTROL_TEXT_INSET - LABEL_GAP - occupied

internal fun pillContent(width: Int, labelWidth: Int, trailing: Int): Int =
	maxOf(width - CONTROL_TEXT_INSET - labelWidth - LABEL_GAP, PILL_MIN_WIDTH + trailing) - 2 * PILL_PAD

internal fun pillLeft(x: Int, width: Int, contentWidth: Int, labelWidth: Int): Int {
	val rightmost = maxOf(x, x + width - PILL_MIN_WIDTH)
	val clearOfLabel = (x + CONTROL_TEXT_INSET + labelWidth + LABEL_GAP).coerceIn(x, rightmost)
	return (x + width - contentWidth - 2 * PILL_PAD).coerceIn(clearOfLabel, rightmost)
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

internal fun caret(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int) {
	SharpGui.fill(graphics, x, top, x + CARET_WIDTH, top + DhenType.lineHeight(font), DhenPalette.TEXT_PRIMARY)
}

internal fun isPrintable(codepoint: Int): Boolean = codepoint in PRINTABLE_MIN..PRINTABLE_MAX && codepoint != DELETE_CODE

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> ToggleControl(setting)
	is NumberSetting -> SliderControl(setting)
	is SelectorSetting ->
		if (setting.options.size < LISTED_FROM_OPTIONS) CycleControl(setting) else DropdownControl(setting)
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
