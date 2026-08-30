package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.SoundSetting
import io.github.dzkchen.dhen.config.StringSetting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.slf4j.LoggerFactory

internal const val CONTROL_TEXT_INSET = 2
internal const val LABEL_GAP = 4
internal const val WIDGET_HEIGHT = 14
internal const val WIDGET_PAD = 3
internal const val CONTROL_ROW_HEIGHT = WIDGET_HEIGHT + 2 * WIDGET_PAD
internal const val PILL_CAP = WIDGET_HEIGHT / 2
internal const val PILL_PAD = PILL_CAP + 1
internal const val OPAQUE_ALPHA = 0xFF
internal const val CARET_WIDTH = 1
private const val PRINTABLE_MIN = 32
private const val PRINTABLE_MAX = 0xFFFF
private const val DELETE_CODE = 127

private val LOG = LoggerFactory.getLogger(Dhen.MOD_ID)

internal sealed class SettingControl(private val setting: Setting<*>) {
	private val memos = mutableListOf<TextMemo>()

	protected val valueText = memo()
	private val labelFloorText = memo()
	private val labelWrap = DhenType.wrap()
	private var pillSpan = 0
	private var valueSpan = 0
	private var trailingSpan = 0
	private var reserveSpan = 0

	var failed = false
		private set

	val clientOwned: Boolean
		get() = setting.owner == null

	protected var rowHeight = CONTROL_ROW_HEIGHT
		private set

	protected val rowGrowth: Int
		get() = rowHeight - CONTROL_ROW_HEIGHT

	protected open val caretReserve: Int
		get() = 0

	val labelElided: Boolean
		get() = labelWrap.elided

	open val height: Int
		get() = rowHeight

	open val expanded: Boolean
		get() = false

	open val acceptsTextInput: Boolean
		get() = false

	val extent: Int
		get() = if (renderable()) height else 0

	open fun collapse(): Boolean = false

	fun measure(font: Font, width: Int): Boolean = guarded(false, true) {
		labelWrap.measure(font, setting.name, onMeasure(font, width))
		val grown = labelWrap.height(font, CONTROL_ROW_HEIGHT)
		val changed = grown != rowHeight
		rowHeight = grown
		changed
	}

	protected open fun onMeasure(font: Font, width: Int): Int = labelRoom(width, 0)

	protected fun measurePill(
		font: Font,
		width: Int,
		sizingValue: String,
		claimedValueWidth: Int = 0,
		trailing: Int = 0
	): Int {
		reserveSpan = caretReserve
		trailingSpan = trailing
		val fullValueWidth = maxOf(valueText.width(font, sizingValue), claimedValueWidth)
		val labelFloor = labelFloorText.width(font, ELLIPSIS)
		valueSpan = pillValueRoom(width, fullValueWidth, labelFloor, trailing, reserveSpan)
		pillSpan = pillWidth(valueSpan, trailing, reserveSpan, width)
		return labelRoom(width, pillSpan)
	}

	protected abstract fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int)

	protected abstract fun onPress(localX: Int, localY: Int, width: Int): ControlPress

	protected open fun onDrag(localX: Int, localY: Int, width: Int) = Unit

	protected open fun onKeyPressed(key: Int, modifiers: Int): ControlKey = ControlKey.IGNORED

	protected open fun onCharTyped(codepoint: Int): Boolean = false

	protected open fun onCaptureMouse(button: Int): ControlKey = ControlKey.IGNORED

	protected open fun onScroll(localX: Int, localY: Int, width: Int, delta: Int): Boolean = false

	protected open fun onBlur(): Boolean = false

	protected open fun onInvalidateMeasurement() = Unit

	fun renderable(): Boolean = guarded(false, false) { setting.isVisible }

	fun draw(
		graphics: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		width: Int,
		pointerY: Int,
		tooltip: ClickGuiTooltip
	) = guarded(Unit, Unit) {
		onDraw(graphics, font, x, y, width, pointerY)
		if (!hovering(y, pointerY)) return@guarded
		tooltip.hover(if (labelElided) setting.name else "", setting.description, x, width, y)
	}

	fun press(localX: Int, localY: Int, width: Int): ControlPress? =
		guarded(null, null) { onPress(localX, localY, width) }

	fun drag(localX: Int, localY: Int, width: Int) = guarded(Unit, Unit) { onDrag(localX, localY, width) }

	fun keyPressed(key: Int, modifiers: Int): ControlKey =
		guarded(ControlKey.IGNORED, ControlKey.CANCELLED) { onKeyPressed(key, modifiers) }

	fun charTyped(codepoint: Int): Boolean = guarded(false, true) { onCharTyped(codepoint) }

	fun captureMouse(button: Int): ControlKey =
		guarded(ControlKey.IGNORED, ControlKey.CANCELLED) { onCaptureMouse(button) }

	fun scroll(localX: Int, localY: Int, width: Int, delta: Int): Boolean =
		guarded(false, true) { onScroll(localX, localY, width, delta) }

	fun blur(): Boolean = guarded(false, false) { onBlur() }

	private inline fun <T> guarded(whenFailed: T, whenThrown: T, body: () -> T): T {
		if (failed) return whenFailed
		return try {
			body()
		} catch (throwable: Throwable) {
			quarantine(throwable)
			whenThrown
		}
	}

	protected fun memo(): TextMemo = DhenType.memo().also { memos += it }

	fun invalidateMeasurement() {
		for (i in memos.indices) memos[i].invalidate()
		labelWrap.invalidate()
		onInvalidateMeasurement()
	}

	protected fun hovering(y: Int, pointerY: Int): Boolean = pointerY >= y && pointerY < y + rowHeight

	protected fun widgetTop(y: Int): Int = y + (rowHeight - WIDGET_HEIGHT) / 2

	protected fun widgetTextTop(font: Font, y: Int): Int = textTop(font, widgetTop(y), WIDGET_HEIGHT)

	protected fun labelBlockTop(font: Font, y: Int): Int = labelWrap.blockTop(font, y, rowHeight)

	protected fun drawLabel(
		graphics: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		top: Int,
		color: Int,
		centered: Boolean = false
	) = labelWrap.draw(graphics, font, x, top, color, centered)

	protected fun pillRow(
		graphics: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		width: Int,
		hovered: Boolean,
		value: String,
		valueColor: Int,
		editing: Boolean = false,
		active: Boolean = editing
	): Int {
		val shown = valueText.fit(font, value, valueSpan, editing)
		val shownWidth = valueText.width(font, shown)
		val right = x + width
		val top = widgetTop(y)
		RoundedGui.pillFrame(
			graphics,
			right - pillSpan,
			top,
			right,
			top + WIDGET_HEIGHT,
			GlassGui.raised(hovered),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		drawLabel(graphics, font, x + CONTROL_TEXT_INSET, labelBlockTop(font, y), DhenPalette.label(hovered))
		val baseline = widgetTextTop(font, y)
		val contentRight = right - PILL_PAD
		val valueRight = contentRight - trailingSpan - reserveSpan
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

internal fun textTop(font: Font, y: Int, height: Int): Int = y + (height - DhenType.lineHeight(font)) / 2

internal fun labelRoom(width: Int, occupied: Int): Int = width - CONTROL_TEXT_INSET - LABEL_GAP - occupied

internal fun pillValueRoom(width: Int, valueWidth: Int, labelFloor: Int, trailing: Int, reserve: Int): Int =
	minOf(
		maxOf(valueWidth, 0),
		maxOf(width - CONTROL_TEXT_INSET - LABEL_GAP - labelFloor - 2 * PILL_PAD - trailing - reserve, 0)
	)

internal fun pillWidth(valueRoom: Int, trailing: Int, reserve: Int, width: Int): Int =
	minOf(maxOf(valueRoom, 0) + trailing + reserve + 2 * PILL_PAD, maxOf(width, 0))

internal class WidestText {
	private val memo = DhenType.memo()
	private var values: List<String>? = null
	private var revision = Int.MIN_VALUE
	private var widest = 0

	fun width(font: Font, candidates: List<String>): Int {
		val current = DhenFont.revision
		if (values === candidates && revision == current) return widest
		values = candidates
		revision = current
		widest = 0
		for (i in candidates.indices) widest = maxOf(widest, memo.width(font, candidates[i]))
		return widest
	}

	fun invalidate() {
		values = null
		memo.invalidate()
	}
}

internal fun caret(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int) {
	SharpGui.fill(graphics, x, top, x + CARET_WIDTH, top + DhenType.lineHeight(font), DhenPalette.TEXT_PRIMARY)
}

internal fun isPrintable(codepoint: Int): Boolean = codepoint in PRINTABLE_MIN..PRINTABLE_MAX && codepoint != DELETE_CODE

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> ToggleControl(setting)
	is NumberSetting -> SliderControl(setting)
	is SelectorSetting -> if (setting.listed) DropdownControl(setting) else CycleControl(setting)
	is StringSetting -> TextControl(setting)
	is ColorSetting -> ColorControl(setting)
	is KeybindSetting -> KeybindControl(setting)
	is ActionSetting -> ActionControl(setting)
	is SoundSetting -> SoundControl(setting)
	else -> null
}
