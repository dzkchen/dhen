package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.sound.ANY_PITCH
import io.github.dzkchen.dhen.sound.SoundManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.roundToInt

internal const val RULE_PANEL_WIDTH = 300
internal const val RULE_PANEL_HEIGHT = 164

internal enum class RuleAction { CONSUMED, PICK, CLOSE }

internal enum class RuleField(val label: String, val slider: IntRange?) {
	MATCH_PITCH("Match pitch", null),
	VOLUME("Volume", VOLUME_RANGE),
	REPLACEMENT("Replace with", null),
	REPLACEMENT_VOLUME("Replacement volume", REPLACEMENT_VOLUME_RANGE),
	REPLACEMENT_PITCH("Replacement pitch", PITCH_RANGE)
}

private enum class RuleButton(val label: String) {
	PLAY("Play"),
	PLAY_ORIGINAL("Play original"),
	REMOVE("Remove rule")
}

internal class SoundRuleEditor {
	var sound: ManagedSound? = null
		private set

	private val titleMemo = DhenType.memo()
	private val labelMemos = Array(RuleField.entries.size) { DhenType.memo() }
	private val valueMemos = Array(RuleField.entries.size) { DhenType.memo() }
	private val buttonMemos = Array(RuleButton.entries.size) { DhenType.memo() }
	private val clearMemo = DhenType.memo()
	private val shownValues = IntArray(RuleField.entries.size) { Int.MIN_VALUE }
	private val shownLabels = arrayOfNulls<String>(RuleField.entries.size)
	private var dragged: RuleField? = null
	private var matchPitch = ANY_PITCH
	private var preferredMatchPitch = DEFAULT_MATCH_PITCH
	private var shownReplacement: Identifier? = null
	private var shownReplacementName = NO_REPLACEMENT

	fun open(sound: ManagedSound) {
		this.sound = sound
		matchPitch = sound.matchPitch
		preferredMatchPitch = if (matchPitch.isNaN()) DEFAULT_MATCH_PITCH else matchPitch
		dragged = null
	}

	fun close() {
		sound = null
		dragged = null
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, top: Int, mouseX: Int, mouseY: Int) {
		val edited = sound ?: return
		GlassGui.roundedFrame(
			graphics,
			left,
			top,
			left + RULE_PANEL_WIDTH,
			top + RULE_PANEL_HEIGHT,
			PANEL_RADIUS,
			DhenPalette.CANVAS,
			DhenPalette.BORDER
		)
		val name = titleMemo.fit(font, edited.cleanName, RULE_PANEL_WIDTH - 2 * PANEL_PAD)
		titleMemo.text(
			graphics,
			font,
			name,
			left + (RULE_PANEL_WIDTH - titleMemo.width(font, name)) / 2,
			top + TITLE_TOP,
			DhenPalette.accent
		)
		var field = 0
		while (field < RuleField.entries.size) {
			drawField(graphics, font, edited.identifier, RuleField.entries[field], left, top, mouseX, mouseY)
			field++
		}
		var button = 0
		while (button < RuleButton.entries.size) {
			val buttonLeft = buttonLeft(left, button)
			drawSoundButton(
				graphics,
				font,
				buttonMemos[button],
				RuleButton.entries[button].label,
				buttonLeft,
				top + BUTTONS_TOP,
				buttonLeft + BUTTON_WIDTH,
				top + BUTTONS_TOP + BUTTON_HEIGHT,
				hovering(mouseX, mouseY, buttonLeft, top + BUTTONS_TOP, buttonLeft + BUTTON_WIDTH, top + BUTTONS_TOP + BUTTON_HEIGHT)
			)
			button++
		}
	}

	fun press(mouseX: Int, mouseY: Int, left: Int, top: Int): RuleAction {
		val edited = sound ?: return RuleAction.CLOSE
		if (mouseX !in left until left + RULE_PANEL_WIDTH || mouseY !in top until top + RULE_PANEL_HEIGHT) {
			return RuleAction.CLOSE
		}
		for (field in RuleField.entries) {
			val rowTop = top + FIELDS_TOP + field.ordinal * FIELD_HEIGHT
			if (mouseY !in rowTop until rowTop + FIELD_HEIGHT) continue
			if (field == RuleField.MATCH_PITCH) {
				toggleMatchPitch(edited.identifier)
				return RuleAction.CONSUMED
			}
			if (!enabled(field, edited.identifier)) return RuleAction.CONSUMED
			val range = field.slider ?: return pressReplacement(edited.identifier, mouseX, left)
			val sliderLeft = sliderLeft(left)
			if (mouseX !in sliderLeft - SLIDER_HIT_PAD until sliderLeft + RULE_SLIDER_WIDTH + SLIDER_HIT_PAD) {
				return RuleAction.CONSUMED
			}
			dragged = field
			apply(field, edited.identifier, steppedSliderValue(mouseX, sliderLeft, RULE_SLIDER_WIDTH, range))
			return RuleAction.CONSUMED
		}
		if (mouseY in top + BUTTONS_TOP until top + BUTTONS_TOP + BUTTON_HEIGHT) {
			for (button in RuleButton.entries) {
				val buttonLeft = buttonLeft(left, button.ordinal)
				if (mouseX !in buttonLeft until buttonLeft + BUTTON_WIDTH) continue
				return press(button, edited)
			}
		}
		return RuleAction.CONSUMED
	}

	fun drag(mouseX: Int, left: Int): Boolean {
		val field = dragged ?: return false
		val edited = sound ?: return false
		val range = field.slider ?: return false
		apply(field, edited.identifier, steppedSliderValue(mouseX, sliderLeft(left), RULE_SLIDER_WIDTH, range))
		return true
	}

	fun release(): Boolean {
		if (dragged == null) return false
		dragged = null
		return true
	}

	fun select(replacement: Identifier) {
		val edited = sound ?: return
		if (SoundManager.getVolumePercent(edited.identifier, matchPitch) == MUTED_PERCENT) {
			SoundManager.setVolumePercent(edited.identifier, DEFAULT_PERCENT, matchPitch)
		}
		setReplacement(edited.identifier, replacement)
	}

	private fun press(button: RuleButton, edited: ManagedSound): RuleAction = when (button) {
		RuleButton.PLAY -> {
			SoundManager.playPreview(edited.event, previewPitch())
			RuleAction.CONSUMED
		}
		RuleButton.PLAY_ORIGINAL -> {
			SoundManager.playOriginal(edited.identifier, previewPitch())
			RuleAction.CONSUMED
		}
		RuleButton.REMOVE -> {
			SoundManager.removeRule(edited.identifier, matchPitch)
			RuleAction.CLOSE
		}
	}

	private fun pressReplacement(identifier: Identifier, mouseX: Int, left: Int): RuleAction {
		if (SoundManager.replacementOf(identifier, matchPitch) != null && mouseX >= clearLeft(left)) {
			setReplacement(identifier, null)
			return RuleAction.CONSUMED
		}
		return RuleAction.PICK
	}

	private fun setReplacement(identifier: Identifier, replacement: Identifier?) = SoundManager.setReplacement(
		identifier,
		replacement,
		SoundManager.replacementVolumeOf(identifier, matchPitch),
		SoundManager.replacementPitchOf(identifier, matchPitch),
		matchPitch
	)

	private fun drawField(
		graphics: GuiGraphicsExtractor,
		font: Font,
		identifier: Identifier,
		field: RuleField,
		left: Int,
		top: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val rowTop = top + FIELDS_TOP + field.ordinal * FIELD_HEIGHT
		val enabled = enabled(field, identifier)
		labelMemos[field.ordinal].text(
			graphics,
			font,
			field.label,
			left + PANEL_PAD,
			rowTextTop(rowTop, font),
			if (enabled) DhenPalette.TEXT_SECONDARY else DhenPalette.TEXT_DISABLED
		)
		if (field == RuleField.MATCH_PITCH) drawMatchToggle(graphics, left, rowTop, enabled)
		if (field == RuleField.MATCH_PITCH) {
			val value = value(field, identifier)
			val shown = label(field, value)
			val memo = valueMemos[field.ordinal]
			memo.text(
				graphics,
				font,
				shown,
				left + RULE_PANEL_WIDTH - PANEL_PAD - memo.width(font, shown),
				rowTextTop(rowTop, font),
				if (enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_DISABLED
			)
			return
		}
		val range = field.slider
		if (range == null) {
			drawReplacement(graphics, font, identifier, left, rowTop, mouseX, mouseY)
			return
		}
		val value = value(field, identifier)
		val shown = label(field, value)
		val memo = valueMemos[field.ordinal]
		memo.text(
			graphics,
			font,
			shown,
			left + RULE_PANEL_WIDTH - PANEL_PAD - memo.width(font, shown),
			rowTextTop(rowTop, font),
			if (enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_DISABLED
		)
		val sliderLeft = sliderLeft(left)
		drawSoundSlider(
			graphics,
			sliderLeft,
			rowTop + (FIELD_HEIGHT - SOUND_SLIDER_HEIGHT) / 2,
			sliderLeft + RULE_SLIDER_WIDTH,
			value,
			range,
			enabled
		)
	}

	private fun drawReplacement(
		graphics: GuiGraphicsExtractor,
		font: Font,
		identifier: Identifier,
		left: Int,
		rowTop: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val buttonLeft = sliderLeft(left)
		val right = left + RULE_PANEL_WIDTH - PANEL_PAD
		val top = rowTop + (FIELD_HEIGHT - BUTTON_HEIGHT) / 2
		val bottom = top + BUTTON_HEIGHT
		val replacement = SoundManager.replacementOf(identifier, matchPitch)
		val nameRight = if (replacement == null) right else right - CLEAR_WIDTH
		drawSoundButton(
			graphics,
			font,
			valueMemos[RuleField.REPLACEMENT.ordinal],
			replacementName(replacement),
			buttonLeft,
			top,
			nameRight,
			bottom,
			hovering(mouseX, mouseY, buttonLeft, top, nameRight, bottom)
		)
		if (replacement == null) return
		val clearLeft = clearLeft(left)
		drawSoundButton(
			graphics,
			font,
			clearMemo,
			CLEAR_LABEL,
			clearLeft,
			top,
			right,
			bottom,
			hovering(mouseX, mouseY, clearLeft, top, right, bottom)
		)
	}

	private fun replacementName(replacement: Identifier?): String {
		if (replacement == null) return NO_REPLACEMENT
		if (replacement != shownReplacement) {
			shownReplacement = replacement
			shownReplacementName = soundCleanName(replacement)
		}
		return shownReplacementName
	}

	private fun label(field: RuleField, value: Int): String {
		if (shownValues[field.ordinal] != value) {
			shownValues[field.ordinal] = value
			shownLabels[field.ordinal] = if (field == RuleField.MATCH_PITCH || field == RuleField.REPLACEMENT_PITCH) {
				pitchLabel(value)
			} else {
				"$value%"
			}
		}
		return shownLabels[field.ordinal]!!
	}

	private fun enabled(field: RuleField, identifier: Identifier): Boolean = when (field) {
		RuleField.MATCH_PITCH -> !matchPitch.isNaN()
		RuleField.VOLUME ->
			SoundManager.replacementOf(identifier, matchPitch) == null ||
				SoundManager.getVolumePercent(identifier, matchPitch) == MUTED_PERCENT
		RuleField.REPLACEMENT -> true
		else -> SoundManager.replacementOf(identifier, matchPitch) != null
	}

	private fun value(field: RuleField, identifier: Identifier): Int = when (field) {
		RuleField.MATCH_PITCH -> (preferredMatchPitch * PERCENT_SCALE).roundToInt()
		RuleField.VOLUME -> SoundManager.getVolumePercent(identifier, matchPitch)
		RuleField.REPLACEMENT -> 0
		RuleField.REPLACEMENT_VOLUME -> (SoundManager.replacementVolumeOf(identifier, matchPitch) * PERCENT_SCALE).roundToInt()
		RuleField.REPLACEMENT_PITCH -> (SoundManager.replacementPitchOf(identifier, matchPitch) * PERCENT_SCALE).roundToInt()
	}

	private fun apply(field: RuleField, identifier: Identifier, value: Int) {
		when (field) {
			RuleField.MATCH_PITCH -> Unit
			RuleField.VOLUME -> SoundManager.setVolumePercent(identifier, value, matchPitch)
			RuleField.REPLACEMENT -> Unit
			RuleField.REPLACEMENT_VOLUME -> SoundManager.setReplacement(
				identifier,
				SoundManager.replacementOf(identifier, matchPitch),
				value / PERCENT_SCALE,
				SoundManager.replacementPitchOf(identifier, matchPitch),
				matchPitch
			)
			RuleField.REPLACEMENT_PITCH -> SoundManager.setReplacement(
				identifier,
				SoundManager.replacementOf(identifier, matchPitch),
				SoundManager.replacementVolumeOf(identifier, matchPitch),
				value / PERCENT_SCALE,
				matchPitch
			)
		}
	}

	private fun drawMatchToggle(graphics: GuiGraphicsExtractor, left: Int, rowTop: Int, enabled: Boolean) {
		val toggleLeft = left + MATCH_TOGGLE_LEFT
		val top = rowTop + (FIELD_HEIGHT - MATCH_TOGGLE_HEIGHT) / 2
		val right = toggleLeft + MATCH_TOGGLE_WIDTH
		val bottom = top + MATCH_TOGGLE_HEIGHT
		RoundedGui.pill(graphics, toggleLeft, top, right, bottom, if (enabled) DhenPalette.accentMuted else GlassGui.raised())
		RoundedGui.pillBorder(graphics, toggleLeft, top, right, bottom, RoundedGui.HAIRLINE, DhenPalette.BORDER)
		RoundedGui.circle(
			graphics,
			if (enabled) right - MATCH_TOGGLE_RADIUS - MATCH_TOGGLE_INSET else toggleLeft + MATCH_TOGGLE_RADIUS + MATCH_TOGGLE_INSET,
			(top + bottom) / 2,
			MATCH_TOGGLE_RADIUS,
			if (enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_DISABLED
		)
	}

	private fun toggleMatchPitch(identifier: Identifier) {
		val changed = if (matchPitch.isNaN()) preferredMatchPitch else ANY_PITCH
		SoundManager.moveRule(identifier, matchPitch, changed)
		matchPitch = changed
	}

	private fun previewPitch(): Float = if (matchPitch.isNaN()) DEFAULT_MATCH_PITCH else matchPitch

	private fun buttonLeft(left: Int, index: Int): Int = left + PANEL_PAD + index * (BUTTON_WIDTH + BUTTON_GAP)

	private fun clearLeft(left: Int): Int = left + RULE_PANEL_WIDTH - PANEL_PAD - CLEAR_WIDTH + CLEAR_GAP

	private fun sliderLeft(left: Int): Int =
		left + RULE_PANEL_WIDTH - PANEL_PAD - VALUE_WIDTH - VALUE_GAP - RULE_SLIDER_WIDTH
}

internal fun drawSoundButton(
	graphics: GuiGraphicsExtractor,
	font: Font,
	memo: TextMemo,
	label: String,
	left: Int,
	top: Int,
	right: Int,
	bottom: Int,
	hovered: Boolean
) {
	RoundedGui.fill(graphics, left, top, right, bottom, BUTTON_RADIUS, if (hovered) DhenPalette.accentMuted else GlassGui.raised())
	val shown = memo.fit(font, label, right - left - 2 * LABEL_PAD)
	memo.text(
		graphics,
		font,
		shown,
		left + (right - left - memo.width(font, shown)) / 2,
		top + (bottom - top - DhenType.lineHeight(font)) / 2,
		if (hovered) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
	)
}

internal fun steppedSliderValue(mouseX: Int, sliderLeft: Int, sliderWidth: Int, range: IntRange): Int {
	if (sliderWidth <= 0) return range.first
	val relative = (mouseX - sliderLeft).coerceIn(0, sliderWidth)
	val raw = range.first + (relative.toDouble() * (range.last - range.first) / sliderWidth).roundToInt()
	return ((raw + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
}

internal fun drawSoundSlider(
	graphics: GuiGraphicsExtractor,
	left: Int,
	top: Int,
	right: Int,
	value: Int,
	range: IntRange,
	enabled: Boolean
) {
	val edge = RoundedGui.capsuleTrack(
		graphics,
		left,
		top,
		right,
		top + SOUND_SLIDER_HEIGHT,
		(value - range.first).toFloat() / (range.last - range.first),
		GlassGui.raised(),
		if (enabled) DhenPalette.accent else DhenPalette.accentMuted
	)
	RoundedGui.circle(
		graphics,
		edge.coerceIn(left + SOUND_KNOB_RADIUS, right - SOUND_KNOB_RADIUS),
		top + SOUND_SLIDER_HEIGHT / 2,
		SOUND_KNOB_RADIUS,
		if (enabled) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_DISABLED
	)
}

internal fun pitchLabel(hundredths: Int): String = "${hundredths / DEFAULT_PERCENT}.${(hundredths % DEFAULT_PERCENT) / 10}${hundredths % 10}"

private fun hovering(mouseX: Int, mouseY: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean =
	mouseX in left until right && mouseY in top until bottom

private fun rowTextTop(top: Int, font: Font): Int = top + (FIELD_HEIGHT - DhenType.lineHeight(font)) / 2

internal const val SOUND_SLIDER_HEIGHT = 5
internal const val SOUND_KNOB_RADIUS = 3
internal val VOLUME_RANGE = MUTED_PERCENT..MAX_RULE_PERCENT
internal val REPLACEMENT_VOLUME_RANGE = MUTED_PERCENT..DEFAULT_PERCENT
internal val PITCH_RANGE = MIN_PITCH_PERCENT..MAX_PITCH_PERCENT
private const val MUTED_PERCENT = 0
private const val DEFAULT_PERCENT = 100
private const val MAX_RULE_PERCENT = 200
private const val MIN_PITCH_PERCENT = 50
private const val MAX_PITCH_PERCENT = 200
private const val STEP_PERCENT = 5
private val PERCENT_SCALE = DEFAULT_PERCENT.toFloat()
private const val NO_REPLACEMENT = "None"
private const val CLEAR_LABEL = "×"
private const val PANEL_PAD = 10
private const val PANEL_RADIUS = 5f
private const val TITLE_TOP = 9
private const val FIELDS_TOP = 26
private const val FIELD_HEIGHT = 22
private const val RULE_SLIDER_WIDTH = 110
private const val SLIDER_HIT_PAD = 5
private const val VALUE_WIDTH = 30
private const val VALUE_GAP = 6
private const val BUTTONS_TOP = 138
private const val BUTTON_WIDTH = 88
private const val BUTTON_HEIGHT = 16
private const val BUTTON_GAP = 8
private const val BUTTON_RADIUS = 3f
private const val LABEL_PAD = 4
private const val CLEAR_WIDTH = 18
private const val CLEAR_GAP = 4
private const val MATCH_TOGGLE_LEFT = 80
private const val MATCH_TOGGLE_WIDTH = 24
private const val MATCH_TOGGLE_HEIGHT = 10
private const val MATCH_TOGGLE_RADIUS = 4
private const val MATCH_TOGGLE_INSET = 1
private const val DEFAULT_MATCH_PITCH = 1f
