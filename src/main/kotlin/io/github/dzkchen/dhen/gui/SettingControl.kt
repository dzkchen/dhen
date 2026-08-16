package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.StringSetting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.slf4j.LoggerFactory

internal const val CONTROL_TEXT_INSET = 2
internal const val LABEL_GAP = 4
internal const val PILL_MIN_WIDTH = 26
internal const val WIDGET_HEIGHT = 14
private const val WIDGET_PAD = 3
internal const val CONTROL_ROW_HEIGHT = WIDGET_HEIGHT + 2 * WIDGET_PAD
internal const val PILL_CAP = WIDGET_HEIGHT / 2
internal const val PILL_PAD = PILL_CAP + 1
internal const val OPAQUE_ALPHA = 0xFF
internal const val CARET_WIDTH = 1
private const val PRINTABLE_MIN = 32
private const val PRINTABLE_MAX = 0xFFFF
private const val DELETE_CODE = 127
private const val LISTED_FROM_OPTIONS = 3

private val LOG = LoggerFactory.getLogger(Dhen.MOD_ID)

internal sealed class SettingControl(val setting: Setting<*>) {
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

	protected open fun onOutdated(): Boolean = false

	protected abstract fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int)

	protected abstract fun onPress(localX: Int, localY: Int, width: Int): ControlPress

	protected open fun onDrag(localX: Int, localY: Int, width: Int) = Unit

	protected open fun onKeyPressed(key: Int, modifiers: Int): ControlKey = ControlKey.IGNORED

	protected open fun onCharTyped(codepoint: Int): Boolean = false

	protected open fun onCaptureMouse(button: Int): ControlKey = ControlKey.IGNORED

	protected open fun onBlur(): Boolean = false

	fun renderable(): Boolean = guarded(false, false) { setting.isVisible }

	fun outdated(): Boolean = guarded(false, false) { onOutdated() }

	fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) =
		guarded(Unit, Unit) { onDraw(graphics, font, x, y, width, pointerY) }

	fun press(localX: Int, localY: Int, width: Int): ControlPress? =
		guarded(null, null) { onPress(localX, localY, width) }

	fun drag(localX: Int, localY: Int, width: Int) = guarded(Unit, Unit) { onDrag(localX, localY, width) }

	fun keyPressed(key: Int, modifiers: Int): ControlKey =
		guarded(ControlKey.IGNORED, ControlKey.CANCELLED) { onKeyPressed(key, modifiers) }

	fun charTyped(codepoint: Int): Boolean = guarded(false, true) { onCharTyped(codepoint) }

	fun captureMouse(button: Int): ControlKey =
		guarded(ControlKey.IGNORED, ControlKey.CANCELLED) { onCaptureMouse(button) }

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
	}

	protected fun hovering(y: Int, pointerY: Int): Boolean = pointerY >= y && pointerY < y + CONTROL_ROW_HEIGHT

	protected fun widgetTop(y: Int): Int = y + WIDGET_PAD

	protected fun rowTextTop(font: Font, y: Int): Int = textTop(font, y, CONTROL_ROW_HEIGHT)

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

internal fun textTop(font: Font, y: Int, height: Int): Int = y + (height - DhenType.lineHeight(font)) / 2

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

internal fun listable(selector: SelectorSetting): Boolean = selector.options.size >= LISTED_FROM_OPTIONS

internal fun controlFor(setting: Setting<*>): SettingControl? = when (setting) {
	is BooleanSetting -> ToggleControl(setting)
	is NumberSetting -> SliderControl(setting)
	is SelectorSetting -> if (listable(setting)) DropdownControl(setting) else CycleControl(setting)
	is StringSetting -> TextControl(setting)
	is ColorSetting -> ColorControl(setting)
	is KeybindSetting -> KeybindControl(setting)
	is ActionSetting -> ActionControl(setting)
	else -> null
}
