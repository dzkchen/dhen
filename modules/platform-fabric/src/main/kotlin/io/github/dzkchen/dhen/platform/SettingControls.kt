package io.github.dzkchen.dhen.platform

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.BooleanValueSchema
import io.github.dzkchen.dhen.api.ColorSetting
import io.github.dzkchen.dhen.api.ColorValueSchema
import io.github.dzkchen.dhen.api.EnumSetting
import io.github.dzkchen.dhen.api.EnumValueSchema
import io.github.dzkchen.dhen.api.FloatRangeSetting
import io.github.dzkchen.dhen.api.FloatRangeValueSchema
import io.github.dzkchen.dhen.api.IntRangeSetting
import io.github.dzkchen.dhen.api.IntRangeValueSchema
import io.github.dzkchen.dhen.api.KeybindSetting
import io.github.dzkchen.dhen.api.KeybindValueSchema
import io.github.dzkchen.dhen.api.ListSetting
import io.github.dzkchen.dhen.api.ListValueSchema
import io.github.dzkchen.dhen.api.ObjectSetting
import io.github.dzkchen.dhen.api.ObjectValueSchema
import io.github.dzkchen.dhen.api.SettingSchema
import io.github.dzkchen.dhen.api.SettingValueSchema
import io.github.dzkchen.dhen.api.StringSetting
import io.github.dzkchen.dhen.api.StringValueSchema
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.Locale

internal object GuiStyle {
	const val LINE = 12
	const val GAP = 2
	const val BOX_HEIGHT = 11

	val TEXT = 0xFFE0E0E0.toInt()
	val DIM = 0xFF888888.toInt()
	val ACCENT = 0xFF8AB4F8.toInt()
	val ERROR = 0xFFFF6B6B.toInt()
	val FIELD_BG = 0xFF101016.toInt()
	val BORDER = 0xFF44444F.toInt()
	val FOCUS = 0xFFFFFFFF.toInt()
	val BLACK = 0xFF000000.toInt()
}

// Bridges a control to its stored value. Top-level controls read/write through the runtime's
// validated setter; nested controls read/write a slot inside their parent's composite value.
internal class ValueSlot(val get: () -> Any?, val set: (Any?) -> Boolean)

internal fun controlFor(schema: SettingSchema, slot: ValueSlot): SettingControl = when (schema) {
	is BooleanSetting -> BooleanControl(schema.name, schema.description, slot)
	is EnumSetting -> EnumControl(schema.name, schema.description, schema.values, slot)
	is IntRangeSetting -> SliderControl(schema.name, schema.description, schema.min.toDouble(), schema.max.toDouble(), true, slot)
	is FloatRangeSetting -> SliderControl(schema.name, schema.description, schema.min, schema.max, false, slot)
	is StringSetting -> TextControl(schema.name, schema.description, slot)
	is ColorSetting -> ColorControl(schema.name, schema.description, slot)
	is KeybindSetting -> KeybindControl(schema.name, schema.description, slot)
	is ListSetting -> ListControl(schema.name, schema.description, schema.itemSchema, slot)
	is ObjectSetting -> ObjectControl(schema.name, schema.description, schema.fields, slot)
}

internal fun controlForValue(schema: SettingValueSchema, label: String, slot: ValueSlot): SettingControl = when (schema) {
	is BooleanValueSchema -> BooleanControl(label, "", slot)
	is EnumValueSchema -> EnumControl(label, "", schema.values, slot)
	is IntRangeValueSchema -> SliderControl(label, "", schema.min.toDouble(), schema.max.toDouble(), true, slot)
	is FloatRangeValueSchema -> SliderControl(label, "", schema.min, schema.max, false, slot)
	is StringValueSchema -> TextControl(label, "", slot)
	is ColorValueSchema -> ColorControl(label, "", slot)
	is KeybindValueSchema -> KeybindControl(label, "", slot)
	is ListValueSchema -> ListControl(label, "", schema.itemSchema, slot)
	is ObjectValueSchema -> ObjectControl(label, "", schema.fields, slot)
}

// Starting value for a newly added list item, read from the item schema's declared defaults.
internal fun defaultValueFor(schema: SettingValueSchema): Any? = when (schema) {
	is BooleanValueSchema -> schema.default ?: false
	is EnumValueSchema -> schema.default ?: schema.values.first()
	is IntRangeValueSchema -> schema.default ?: schema.min
	is FloatRangeValueSchema -> schema.default ?: schema.min
	is StringValueSchema -> schema.default ?: ""
	is ColorValueSchema -> schema.default ?: 0xFFFFFF
	is KeybindValueSchema -> schema.default ?: -1
	is ListValueSchema -> schema.default ?: emptyList<Any?>()
	is ObjectValueSchema -> schema.fields.mapNotNull { field -> defaultFieldValue(field)?.let { field.id.value to it } }.toMap()
}

private fun defaultFieldValue(schema: SettingSchema): Any? = when (schema) {
	is BooleanSetting -> schema.default
	is EnumSetting -> schema.default
	is IntRangeSetting -> schema.default
	is FloatRangeSetting -> schema.default
	is StringSetting -> schema.default
	is ColorSetting -> schema.default
	is KeybindSetting -> schema.default
	is ListSetting -> schema.default
	is ObjectSetting -> schema.fields.mapNotNull { field -> defaultFieldValue(field)?.let { field.id.value to it } }
		.toMap().ifEmpty { null }
}

// One inline widget in a module's expanded settings section. Bounds are set by layout() every
// frame before render; input handlers hit-test against the last laid-out bounds.
internal abstract class SettingControl(val label: String, val description: String) {
	var x = 0
		private set
	var y = 0
		private set
	var width = 0
		private set

	open val height: Int get() = GuiStyle.LINE

	// True while the control owns the keyboard (text editing or key capture); the screen routes
	// all keys here first so Escape/arrows do not fall through to screen navigation.
	open val grabsKeyboard: Boolean get() = false

	fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in x until x + width && mouseY in y until y + height

	open fun layout(x: Int, y: Int, width: Int) {
		this.x = x
		this.y = y
		this.width = width
	}

	abstract fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?)

	// Returns the (possibly nested) control that consumed the click, or null.
	open fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? = null

	open fun mouseDragged(mouseX: Int, mouseY: Int) {}

	open fun mouseReleased() {}

	open fun keyPressed(event: KeyEvent): Boolean = false

	open fun charTyped(chr: Char): Boolean = false

	// Keyboard-focusable leaves, in visual order. Composites return their children.
	open fun focusables(): List<SettingControl> = listOf(this)

	open fun onFocusLost() {}

	protected fun drawFocus(g: GuiGraphicsExtractor, focused: SettingControl?) {
		if (focused === this) outlineRect(g, x, y, x + width, y + height, GuiStyle.FOCUS)
	}

	protected fun outlineRect(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
		g.fill(x0, y0, x1, y0 + 1, color)
		g.fill(x0, y1 - 1, x1, y1, color)
		g.fill(x0, y0, x0 + 1, y1, color)
		g.fill(x1 - 1, y0, x1, y1, color)
	}
}

// Minimal single-line text editor shared by string, numeric, and hex fields. Enter commits
// through the callback (a red border marks a rejected value), Escape reverts.
internal class MiniTextBox(
	private val filter: (Char) -> Boolean = { !it.isISOControl() },
	private val commit: (String) -> Boolean,
) {
	var editing = false
		private set
	private var invalid = false
	private var text = ""
	private var caret = 0

	fun beginEdit(current: String) {
		text = current
		caret = text.length
		editing = true
		invalid = false
	}

	fun endEdit(apply: Boolean) {
		if (!editing) return
		if (apply) {
			invalid = !commit(text)
			if (!invalid) editing = false
		} else {
			invalid = false
			editing = false
		}
	}

	fun charTyped(chr: Char): Boolean {
		if (!editing || !filter(chr)) return false
		text = text.substring(0, caret) + chr + text.substring(caret)
		caret++
		invalid = false
		return true
	}

	fun keyPressed(key: Int): Boolean {
		if (!editing) return false
		when (key) {
			GLFW.GLFW_KEY_BACKSPACE -> if (caret > 0) {
				text = text.removeRange(caret - 1, caret)
				caret--
			}
			GLFW.GLFW_KEY_DELETE -> if (caret < text.length) text = text.removeRange(caret, caret + 1)
			GLFW.GLFW_KEY_LEFT -> caret = (caret - 1).coerceAtLeast(0)
			GLFW.GLFW_KEY_RIGHT -> caret = (caret + 1).coerceAtMost(text.length)
			GLFW.GLFW_KEY_HOME -> caret = 0
			GLFW.GLFW_KEY_END -> caret = text.length
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> endEdit(true)
			GLFW.GLFW_KEY_ESCAPE -> endEdit(false)
			else -> return false
		}
		return true
	}

	fun render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, current: String) {
		val border = when {
			invalid -> GuiStyle.ERROR
			editing -> GuiStyle.FOCUS
			else -> GuiStyle.BORDER
		}
		g.fill(x, y, x + w, y + h, GuiStyle.FIELD_BG)
		g.fill(x, y, x + w, y + 1, border)
		g.fill(x, y + h - 1, x + w, y + h, border)
		g.fill(x, y, x + 1, y + h, border)
		g.fill(x + w - 1, y, x + w, y + h, border)
		val shown = if (editing) text else current
		g.enableScissor(x + 2, y + 1, x + w - 2, y + h - 1)
		g.text(font, shown, x + 3, y + 2, GuiStyle.TEXT)
		if (editing) {
			val caretX = x + 3 + font.width(text.take(caret))
			g.fill(caretX, y + 2, caretX + 1, y + h - 2, GuiStyle.FOCUS)
		}
		g.disableScissor()
	}
}

internal class BooleanControl(label: String, description: String, private val slot: ValueSlot) :
	SettingControl(label, description) {
	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		val on = slot.get() == true
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		val badge = if (on) "[ON]" else "[OFF]"
		g.text(font, badge, x + width - font.width(badge), y + 2, if (on) GuiStyle.ACCENT else GuiStyle.DIM)
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		toggle()
		return this
	}

	override fun keyPressed(event: KeyEvent): Boolean = when (event.key()) {
		GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
			toggle()
			true
		}
		else -> false
	}

	private fun toggle() {
		slot.set(slot.get() != true)
	}
}

internal class EnumControl(
	label: String,
	description: String,
	private val values: List<String>,
	private val slot: ValueSlot,
) : SettingControl(label, description) {
	override val height: Int get() = GuiStyle.LINE * 2

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		val rowY = y + GuiStyle.LINE + 2
		g.text(font, "<", x, rowY, GuiStyle.DIM)
		g.centeredText(font, current(), x + width / 2, rowY, GuiStyle.ACCENT)
		g.text(font, ">", x + width - font.width(">"), rowY, GuiStyle.DIM)
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		cycle(if (mouseX < x + width / 3) -1 else 1)
		return this
	}

	override fun keyPressed(event: KeyEvent): Boolean = when (event.key()) {
		GLFW.GLFW_KEY_LEFT -> {
			cycle(-1)
			true
		}
		GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
			cycle(1)
			true
		}
		else -> false
	}

	private fun current(): String = slot.get() as? String ?: values.first()

	private fun cycle(delta: Int) {
		val index = (values.indexOf(current()) + delta + values.size) % values.size
		slot.set(values[index])
	}
}

internal class SliderControl(
	label: String,
	description: String,
	private val min: Double,
	private val max: Double,
	private val integer: Boolean,
	private val slot: ValueSlot,
) : SettingControl(label, description) {
	private var dragValue: Double? = null
	private val box = MiniTextBox(filter = { it.isDigit() || it == '.' || it == '-' }) { commitText(it) }

	override val height: Int get() = GuiStyle.LINE * 2
	override val grabsKeyboard: Boolean get() = box.editing

	private val trackWidth: Int get() = (width - FIELD_WIDTH - 4).coerceAtLeast(1)

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		val rowY = y + GuiStyle.LINE
		val fraction = if (max > min) ((value() - min) / (max - min)).coerceIn(0.0, 1.0) else 0.0
		g.fill(x, rowY + 4, x + trackWidth, rowY + 7, GuiStyle.FIELD_BG)
		g.fill(x, rowY + 4, x + (fraction * trackWidth).toInt(), rowY + 7, GuiStyle.ACCENT)
		val knobX = x + (fraction * (trackWidth - 2)).toInt()
		g.fill(knobX, rowY + 2, knobX + 2, rowY + 9, GuiStyle.FOCUS)
		box.render(g, font, x + width - FIELD_WIDTH, rowY, FIELD_WIDTH, GuiStyle.BOX_HEIGHT, display(value()))
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		if (mouseY >= y + GuiStyle.LINE) {
			if (mouseX >= x + width - FIELD_WIDTH) {
				box.beginEdit(display(value()))
			} else {
				box.endEdit(true)
				dragValue = valueAt(mouseX)
			}
		}
		return this
	}

	override fun mouseDragged(mouseX: Int, mouseY: Int) {
		if (dragValue != null) dragValue = valueAt(mouseX)
	}

	override fun mouseReleased() {
		dragValue?.let {
			dragValue = null
			commitValue(it)
		}
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (box.keyPressed(event.key())) return true
		return when (event.key()) {
			GLFW.GLFW_KEY_LEFT -> {
				step(-1)
				true
			}
			GLFW.GLFW_KEY_RIGHT -> {
				step(1)
				true
			}
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				box.beginEdit(display(value()))
				true
			}
			else -> false
		}
	}

	override fun charTyped(chr: Char): Boolean = box.charTyped(chr)

	override fun onFocusLost() {
		box.endEdit(true)
		mouseReleased()
	}

	private fun value(): Double = dragValue ?: ((slot.get() as? Number)?.toDouble() ?: min)

	private fun display(value: Double): String =
		if (integer) Math.round(value).toString() else String.format(Locale.ROOT, "%.2f", value)

	private fun valueAt(mouseX: Int): Double {
		val fraction = ((mouseX - x).toDouble() / trackWidth).coerceIn(0.0, 1.0)
		val raw = min + fraction * (max - min)
		return if (integer) Math.round(raw).toDouble() else raw
	}

	private fun step(direction: Int) {
		val size = if (integer) 1.0 else (max - min) / 100
		commitValue((value() + direction * size).coerceIn(min, max))
	}

	private fun commitValue(value: Double): Boolean =
		slot.set(if (integer) Math.round(value).toInt() else value)

	private fun commitText(text: String): Boolean {
		val parsed = text.toDoubleOrNull() ?: return false
		if (parsed < min || parsed > max) return false
		return commitValue(parsed)
	}

	private companion object {
		const val FIELD_WIDTH = 34
	}
}

internal class TextControl(label: String, description: String, private val slot: ValueSlot) :
	SettingControl(label, description) {
	private val box = MiniTextBox { slot.set(it) }

	override val height: Int get() = GuiStyle.LINE * 2
	override val grabsKeyboard: Boolean get() = box.editing

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		box.render(g, font, x, y + GuiStyle.LINE, width, GuiStyle.BOX_HEIGHT, slot.get() as? String ?: "")
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		if (!box.editing) box.beginEdit(slot.get() as? String ?: "")
		return this
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (box.keyPressed(event.key())) return true
		return when (event.key()) {
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				box.beginEdit(slot.get() as? String ?: "")
				true
			}
			else -> false
		}
	}

	override fun charTyped(chr: Char): Boolean = box.charTyped(chr)

	override fun onFocusLost() = box.endEdit(true)
}

// HSV color picker: swatch + hex field, expanding to a saturation/value square and hue strip.
// Drags update local HSV state live and commit the RGB value on release.
internal class ColorControl(label: String, description: String, private val slot: ValueSlot) :
	SettingControl(label, description) {
	private var expanded = false
	private var dragging = DragTarget.NONE
	private val hsv = FloatArray(3)
	private var lastValue = -1
	private val box = MiniTextBox(filter = { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '#' }) { commitHex(it) }

	override val height: Int
		get() = GuiStyle.LINE * 2 + if (expanded) SV_HEIGHT + HUE_HEIGHT + 3 * GuiStyle.GAP else 0

	override val grabsKeyboard: Boolean get() = box.editing

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		val rgb = currentRgb()
		if (dragging == DragTarget.NONE && rgb != lastValue) syncFrom(rgb)
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		val rowY = y + GuiStyle.LINE
		g.fill(x, rowY, x + SWATCH_WIDTH, rowY + GuiStyle.BOX_HEIGHT, GuiStyle.BLACK or previewRgb())
		outlineRect(g, x, rowY, x + SWATCH_WIDTH, rowY + GuiStyle.BOX_HEIGHT, GuiStyle.BORDER)
		g.text(font, if (expanded) "-" else "+", x + 2, rowY + 2, GuiStyle.FOCUS)
		box.render(
			g, font, x + SWATCH_WIDTH + GuiStyle.GAP, rowY, width - SWATCH_WIDTH - GuiStyle.GAP,
			GuiStyle.BOX_HEIGHT, String.format(Locale.ROOT, "#%06X", previewRgb()),
		)
		if (expanded) renderPicker(g)
		drawFocus(g, focused)
	}

	private fun renderPicker(g: GuiGraphicsExtractor) {
		val svTop = svTop()
		for (px in 0 until width) {
			val saturation = px / (width - 1).coerceAtLeast(1).toFloat()
			val top = GuiStyle.BLACK or (Color.HSBtoRGB(hsv[0], saturation, 1f) and 0xFFFFFF)
			g.fillGradient(x + px, svTop, x + px + 1, svTop + SV_HEIGHT, top, GuiStyle.BLACK)
		}
		val svX = x + (hsv[1] * (width - 1)).toInt()
		val svY = svTop + ((1 - hsv[2]) * (SV_HEIGHT - 1)).toInt()
		outlineRect(g, svX - 2, svY - 2, svX + 3, svY + 3, GuiStyle.FOCUS)

		val hueTop = hueTop()
		for (px in 0 until width) {
			val hue = px / (width - 1).coerceAtLeast(1).toFloat()
			g.fill(x + px, hueTop, x + px + 1, hueTop + HUE_HEIGHT, GuiStyle.BLACK or (Color.HSBtoRGB(hue, 1f, 1f) and 0xFFFFFF))
		}
		val hueX = x + (hsv[0] * (width - 1)).toInt()
		g.fill(hueX, hueTop - 1, hueX + 1, hueTop + HUE_HEIGHT + 1, GuiStyle.FOCUS)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		val rowY = y + GuiStyle.LINE
		when {
			mouseY in rowY until rowY + GuiStyle.BOX_HEIGHT && mouseX < x + SWATCH_WIDTH -> expanded = !expanded
			mouseY in rowY until rowY + GuiStyle.BOX_HEIGHT ->
				if (!box.editing) box.beginEdit(String.format(Locale.ROOT, "%06X", previewRgb()))
			expanded && mouseY in svTop() until svTop() + SV_HEIGHT -> {
				dragging = DragTarget.SV
				applySv(mouseX, mouseY)
			}
			expanded && mouseY in hueTop() until hueTop() + HUE_HEIGHT -> {
				dragging = DragTarget.HUE
				applyHue(mouseX)
			}
		}
		return this
	}

	override fun mouseDragged(mouseX: Int, mouseY: Int) {
		when (dragging) {
			DragTarget.SV -> applySv(mouseX, mouseY)
			DragTarget.HUE -> applyHue(mouseX)
			DragTarget.NONE -> Unit
		}
	}

	override fun mouseReleased() {
		if (dragging == DragTarget.NONE) return
		dragging = DragTarget.NONE
		commitHsv()
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (box.keyPressed(event.key())) return true
		return when (event.key()) {
			GLFW.GLFW_KEY_SPACE -> {
				expanded = !expanded
				true
			}
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				box.beginEdit(String.format(Locale.ROOT, "%06X", previewRgb()))
				true
			}
			else -> false
		}
	}

	override fun charTyped(chr: Char): Boolean = box.charTyped(chr)

	override fun onFocusLost() {
		box.endEdit(true)
		mouseReleased()
	}

	private fun svTop(): Int = y + GuiStyle.LINE * 2 + GuiStyle.GAP

	private fun hueTop(): Int = svTop() + SV_HEIGHT + GuiStyle.GAP

	private fun applySv(mouseX: Int, mouseY: Int) {
		hsv[1] = ((mouseX - x).toFloat() / (width - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
		hsv[2] = 1f - ((mouseY - svTop()).toFloat() / (SV_HEIGHT - 1)).coerceIn(0f, 1f)
	}

	private fun applyHue(mouseX: Int) {
		hsv[0] = ((mouseX - x).toFloat() / (width - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
	}

	private fun currentRgb(): Int = ((slot.get() as? Number)?.toInt() ?: 0xFFFFFF) and 0xFFFFFF

	private fun previewRgb(): Int =
		if (dragging == DragTarget.NONE) currentRgb() else Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) and 0xFFFFFF

	private fun syncFrom(rgb: Int) {
		Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsv)
		lastValue = rgb
	}

	private fun commitHsv() {
		val rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) and 0xFFFFFF
		if (slot.set(rgb)) lastValue = rgb
	}

	private fun commitHex(text: String): Boolean {
		val cleaned = text.removePrefix("#")
		if (cleaned.isEmpty() || cleaned.length > 6) return false
		val rgb = cleaned.toIntOrNull(16) ?: return false
		return slot.set(rgb)
	}

	private enum class DragTarget { NONE, SV, HUE }

	private companion object {
		const val SV_HEIGHT = 36
		const val HUE_HEIGHT = 8
		const val SWATCH_WIDTH = 14
	}
}

internal class KeybindControl(label: String, description: String, private val slot: ValueSlot) :
	SettingControl(label, description) {
	private var capturing = false

	override val grabsKeyboard: Boolean get() = capturing

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		val badge = if (capturing) "[...]" else "[${keyName()}]"
		g.text(font, badge, x + width - font.width(badge), y + 2, if (capturing) GuiStyle.ACCENT else GuiStyle.DIM)
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		capturing = true
		return this
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (!capturing) {
			return when (event.key()) {
				GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
					capturing = true
					true
				}
				else -> false
			}
		}
		when (event.key()) {
			GLFW.GLFW_KEY_ESCAPE -> Unit
			GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> slot.set(-1)
			else -> slot.set(event.key())
		}
		capturing = false
		return true
	}

	override fun onFocusLost() {
		capturing = false
	}

	private fun keyName(): String {
		val key = (slot.get() as? Number)?.toInt() ?: -1
		return if (key == -1) "None" else InputConstants.Type.KEYSYM.getOrCreate(key).displayName.string
	}
}

// Editable list: one nested control per item, [+] to append the schema default, [x] to remove,
// and a drag handle to reorder. Structural edits and reorders commit the whole list.
internal class ListControl(
	label: String,
	description: String,
	private val itemSchema: SettingValueSchema,
	private val slot: ValueSlot,
) : SettingControl(label, description) {
	private var working = mutableListOf<Any?>()
	private var children = mutableListOf<SettingControl>()
	private var lastSeen: List<Any?>? = null
	private var dragIndex = -1

	override val height: Int get() = GuiStyle.LINE + children.sumOf { it.height + GuiStyle.GAP }

	override fun layout(x: Int, y: Int, width: Int) {
		syncFromSlot()
		super.layout(x, y, width)
		var childY = y + GuiStyle.LINE
		for (child in children) {
			child.layout(x + HANDLE_WIDTH, childY, width - HANDLE_WIDTH - REMOVE_WIDTH)
			childY += child.height + GuiStyle.GAP
		}
	}

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, label, x, y + 2, GuiStyle.TEXT)
		g.text(font, "[+]", x + width - font.width("[+]"), y + 2, GuiStyle.ACCENT)
		for ((index, child) in children.withIndex()) {
			g.text(font, "=", x, child.y + 2, if (index == dragIndex) GuiStyle.FOCUS else GuiStyle.DIM)
			child.render(g, font, mouseX, mouseY, focused)
			g.text(font, "x", x + width - REMOVE_WIDTH + 2, child.y + 2, GuiStyle.ERROR)
		}
		drawFocus(g, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return null
		if (mouseY < y + GuiStyle.LINE) {
			if (mouseX >= x + width - 14) {
				working.add(defaultValueFor(itemSchema))
				commit()
				rebuild()
			}
			return this
		}
		for ((index, child) in children.withIndex()) {
			if (mouseY !in child.y until child.y + child.height) continue
			if (mouseX < x + HANDLE_WIDTH) {
				dragIndex = index
				return this
			}
			if (mouseX >= x + width - REMOVE_WIDTH) {
				working.removeAt(index)
				commit()
				rebuild()
				return this
			}
			child.mouseClicked(mouseX, mouseY, button)?.let { return it }
			return this
		}
		return this
	}

	override fun mouseDragged(mouseX: Int, mouseY: Int) {
		if (dragIndex < 0) return
		val target = children.indexOfFirst { mouseY in it.y until it.y + it.height }
		if (target < 0 || target == dragIndex) return
		working.add(target, working.removeAt(dragIndex))
		dragIndex = target
		rebuild()
	}

	override fun mouseReleased() {
		if (dragIndex < 0) return
		dragIndex = -1
		commit()
	}

	override fun keyPressed(event: KeyEvent): Boolean = when (event.key()) {
		GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
			working.add(defaultValueFor(itemSchema))
			commit()
			rebuild()
			true
		}
		else -> false
	}

	override fun focusables(): List<SettingControl> = listOf(this) + children.flatMap { it.focusables() }

	private fun syncFromSlot() {
		if (dragIndex >= 0) return
		val current = (slot.get() as? List<*>)?.toList() ?: emptyList()
		if (current == lastSeen) return
		lastSeen = current
		working = ArrayList(current)
		rebuild()
	}

	private fun rebuild() {
		children = working.indices.mapTo(ArrayList()) { index ->
			controlForValue(
				itemSchema,
				"#${index + 1}",
				ValueSlot(
					get = { working.getOrNull(index) },
					set = { value ->
						if (index < working.size) working[index] = value
						commit()
					},
				),
			)
		}
	}

	private fun commit(): Boolean {
		val snapshot = working.toList()
		val accepted = slot.set(snapshot)
		lastSeen = if (accepted) snapshot else null
		return accepted
	}

	private companion object {
		const val HANDLE_WIDTH = 10
		const val REMOVE_WIDTH = 10
	}
}

internal class ObjectControl(
	label: String,
	description: String,
	fields: List<SettingSchema>,
	private val slot: ValueSlot,
) : SettingControl(label, description) {
	private val children = fields.map { field ->
		controlFor(
			field,
			ValueSlot(
				get = { (slot.get() as? Map<*, *>)?.get(field.id.value) },
				set = { value ->
					val current = LinkedHashMap<String, Any?>()
					(slot.get() as? Map<*, *>)?.forEach { (key, item) -> if (key is String) current[key] = item }
					current[field.id.value] = value
					slot.set(current)
				},
			),
		)
	}

	override val height: Int get() = GuiStyle.LINE + children.sumOf { it.height + GuiStyle.GAP }

	override fun layout(x: Int, y: Int, width: Int) {
		super.layout(x, y, width)
		var childY = y + GuiStyle.LINE
		for (child in children) {
			child.layout(x + INDENT, childY, width - INDENT)
			childY += child.height + GuiStyle.GAP
		}
	}

	override fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, focused: SettingControl?) {
		g.text(font, "$label:", x, y + 2, GuiStyle.DIM)
		for (child in children) child.render(g, font, mouseX, mouseY, focused)
	}

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): SettingControl? {
		if (!contains(mouseX, mouseY)) return null
		for (child in children) child.mouseClicked(mouseX, mouseY, button)?.let { return it }
		return null
	}

	override fun focusables(): List<SettingControl> = children.flatMap { it.focusables() }

	private companion object {
		const val INDENT = 6
	}
}
