package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.SoundOption
import io.github.dzkchen.dhen.config.SoundSetting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

internal const val SOUND_VISIBLE_ROWS = 5
internal const val SOUND_SEARCH_HEIGHT = 18
internal const val SOUND_ROWS_TOP = CONTROL_ROW_HEIGHT + LIST_PAD + SOUND_SEARCH_HEIGHT
private const val SOUND_SEARCH_INSET = 4
private const val SOUND_SEARCH_MAX_LENGTH = 128
private const val SOUND_LIST_RADIUS = 4f
private const val SOUND_ROW_RADIUS = 3f
private const val SOUND_SCROLLBAR_WIDTH = 2
private const val SOUND_SCROLLBAR_MIN_HEIGHT = LIST_ROW_HEIGHT
private const val SOUND_GLYPH_GAP = 4
private const val SOUND_GLYPH = "⌄"
private const val SOUND_SEARCH_PLACEHOLDER = "Search sounds..."

internal class SoundControl(private val sound: SoundSetting) : SettingControl(sound) {
	private val glyphText = memo()
	private val searchText = memo()
	private val optionText = Array(SOUND_VISIBLE_ROWS) { memo() }
	private var filtered = SoundSetting.options
	private var query = ""
	private var firstVisible = 0
	private var open = false
	private var editing = false
	private var cachedIdentifier: Identifier? = null
	private var cachedName = ""

	override val height: Int
		get() = if (open) CONTROL_ROW_HEIGHT + soundListHeight() else CONTROL_ROW_HEIGHT

	override val expanded: Boolean
		get() = open

	override val acceptsTextInput: Boolean
		get() = open && editing

	override fun collapse(): Boolean {
		if (!open) return false
		open = false
		editing = false
		return true
	}

	override fun onDraw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, pointerY: Int) {
		val glyphWidth = glyphText.width(font, SOUND_GLYPH)
		val contentRight = pillRow(
			graphics,
			font,
			x,
			y,
			width,
			hovering(y, pointerY),
			sound.name,
			selectedName(),
			DhenPalette.TEXT_PRIMARY,
			SOUND_GLYPH_GAP + glyphWidth,
			active = open
		)
		val shownGlyph = glyphText.fit(font, SOUND_GLYPH, glyphWidth)
		glyphText.text(graphics, font, shownGlyph, contentRight - glyphWidth, rowTextTop(font, y), DhenPalette.TEXT_SECONDARY)
		if (open) drawList(graphics, font, x, y + CONTROL_ROW_HEIGHT, width, pointerY)
	}

	override fun onPress(localX: Int, localY: Int, width: Int): ControlPress {
		if (localY < CONTROL_ROW_HEIGHT) {
			open = !open
			editing = open
			return if (open) ControlPress.FOCUS else ControlPress.RESIZED
		}
		if (!open) return ControlPress.IGNORED
		val slot = (localY - SOUND_ROWS_TOP) / LIST_ROW_HEIGHT
		if (localY >= SOUND_ROWS_TOP && slot in 0 until SOUND_VISIBLE_ROWS) {
			val index = firstVisible + slot
			if (index in filtered.indices) {
				val identifier = filtered[index].identifier
				val changed = identifier != sound.value.location()
				if (changed) sound.select(identifier)
				open = false
				editing = false
				return if (changed) ControlPress.CHANGED else ControlPress.RESIZED
			}
		}
		editing = true
		return ControlPress.FOCUS
	}

	override fun onKeyPressed(key: Int, modifiers: Int): ControlKey {
		if (!acceptsTextInput) return ControlKey.IGNORED
		if (key == GLFW.GLFW_KEY_ESCAPE) return ControlKey.IGNORED
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (query.isNotEmpty()) {
				query = query.substring(0, query.length - 1)
				updateFilter()
			}
			return ControlKey.CONSUMED
		}
		return ControlKey.CONSUMED
	}

	override fun onCharTyped(codepoint: Int): Boolean {
		if (!acceptsTextInput) return false
		if (query.length < SOUND_SEARCH_MAX_LENGTH && isPrintable(codepoint)) {
			query += codepoint.toChar()
			updateFilter()
		}
		return true
	}

	override fun onScroll(localX: Int, localY: Int, width: Int, delta: Int): Boolean {
		if (!open || localX !in 0 until width || localY !in SOUND_ROWS_TOP until height || delta == 0) return false
		val maxFirst = maxOf(filtered.size - SOUND_VISIBLE_ROWS, 0)
		val next = (firstVisible + if (delta < 0) 1 else -1).coerceIn(0, maxFirst)
		if (next == firstVisible) return false
		firstVisible = next
		return true
	}

	override fun onBlur(): Boolean {
		editing = false
		return false
	}

	private fun drawList(graphics: GuiGraphicsExtractor, font: Font, x: Int, top: Int, width: Int, pointerY: Int) {
		val right = x + width
		val bottom = top + soundListHeight()
		RoundedGui.frame(graphics, x, top, right, bottom, SOUND_LIST_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		val searchTop = top + LIST_PAD
		val searchLeft = x + LIST_PAD
		val searchRight = right - LIST_PAD
		RoundedGui.frame(
			graphics,
			searchLeft,
			searchTop,
			searchRight,
			searchTop + SOUND_SEARCH_HEIGHT,
			SOUND_ROW_RADIUS,
			GlassGui.raised(),
			if (editing) DhenPalette.accent else DhenPalette.BORDER
		)
		drawSearch(graphics, font, searchLeft, searchRight, searchTop)
		drawOptions(graphics, font, x, right, searchTop + SOUND_SEARCH_HEIGHT, pointerY)
	}

	private fun drawSearch(graphics: GuiGraphicsExtractor, font: Font, left: Int, right: Int, top: Int) {
		val baseline = textTop(font, top, SOUND_SEARCH_HEIGHT)
		if (query.isEmpty()) {
			val shown = searchText.fit(font, SOUND_SEARCH_PLACEHOLDER, right - left - 2 * SOUND_SEARCH_INSET)
			searchText.text(graphics, font, shown, left + SOUND_SEARCH_INSET, baseline, DhenPalette.TEXT_DISABLED)
			if (editing) caret(graphics, font, left + SOUND_SEARCH_INSET, baseline)
			return
		}
		val room = right - left - 2 * SOUND_SEARCH_INSET - if (editing) CARET_WIDTH else 0
		val shown = searchText.fit(font, query, room, fromEnd = editing)
		val shownWidth = searchText.width(font, shown)
		searchText.text(graphics, font, shown, left + SOUND_SEARCH_INSET, baseline, DhenPalette.TEXT_PRIMARY)
		if (editing) caret(graphics, font, left + SOUND_SEARCH_INSET + shownWidth, baseline)
	}

	private fun drawOptions(graphics: GuiGraphicsExtractor, font: Font, x: Int, right: Int, rowsTop: Int, pointerY: Int) {
		val visibleCount = minOf(SOUND_VISIBLE_ROWS, filtered.size - firstVisible)
		val scrollbar = filtered.size > SOUND_VISIBLE_ROWS
		val textRight = right - LIST_PAD - if (scrollbar) SOUND_SEARCH_INSET else 0
		val room = textRight - x - LIST_PAD - SOUND_SEARCH_INSET
		for (slot in 0 until visibleCount) {
			val option = filtered[firstVisible + slot]
			val rowTop = rowsTop + slot * LIST_ROW_HEIGHT
			val selected = option.identifier == sound.value.location()
			val hovered = pointerY in rowTop until rowTop + LIST_ROW_HEIGHT
			if (selected || hovered) {
				val fill = if (selected) GlassGui.raised() else GlassGui.interactive()
				RoundedGui.fill(graphics, x + LIST_PAD, rowTop, right - LIST_PAD, rowTop + LIST_ROW_HEIGHT, SOUND_ROW_RADIUS, fill)
			}
			val memo = optionText[slot]
			val shown = memo.fit(font, option.name, room)
			val tint = when {
				selected -> DhenPalette.accent
				hovered -> DhenPalette.TEXT_PRIMARY
				else -> DhenPalette.TEXT_SECONDARY
			}
			memo.text(graphics, font, shown, x + LIST_PAD + SOUND_SEARCH_INSET, textTop(font, rowTop, LIST_ROW_HEIGHT), tint)
		}
		if (scrollbar) drawScrollbar(graphics, right, rowsTop)
	}

	private fun drawScrollbar(graphics: GuiGraphicsExtractor, right: Int, rowsTop: Int) {
		val viewport = SOUND_VISIBLE_ROWS * LIST_ROW_HEIGHT
		val barHeight = maxOf(SOUND_SCROLLBAR_MIN_HEIGHT, viewport * SOUND_VISIBLE_ROWS / filtered.size)
		val travel = viewport - barHeight
		val maxFirst = filtered.size - SOUND_VISIBLE_ROWS
		val barTop = rowsTop + travel * firstVisible / maxFirst
		RoundedGui.pill(
			graphics,
			right - LIST_PAD - SOUND_SCROLLBAR_WIDTH,
			barTop,
			right - LIST_PAD,
			barTop + barHeight,
			DhenPalette.accent
		)
	}

	private fun updateFilter() {
		filtered = if (query.isBlank()) SoundSetting.options else SoundSetting.options.filter { soundMatches(it, query) }
		firstVisible = 0
	}

	private fun selectedName(): String {
		val identifier = sound.value.location()
		if (identifier == cachedIdentifier) return cachedName
		cachedIdentifier = identifier
		for (i in SoundSetting.options.indices) {
			val option = SoundSetting.options[i]
			if (option.identifier != identifier) continue
			cachedName = option.name
			return cachedName
		}
		cachedName = SoundSetting.prettyName(identifier)
		return cachedName
	}
}

internal fun soundMatches(option: SoundOption, query: String): Boolean =
	query.isBlank() || option.name.contains(query, ignoreCase = true) || option.identifier.toString().contains(query, ignoreCase = true)

private fun soundListHeight(): Int = 2 * LIST_PAD + SOUND_SEARCH_HEIGHT + SOUND_VISIBLE_ROWS * LIST_ROW_HEIGHT
