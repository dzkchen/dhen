package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.input.TextInputTarget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class SoundManagerScreen(private val parent: Screen) : LiveWorldScreen(Component.literal(TITLE)), TextInputTarget {
	private val sounds = BuiltInRegistries.SOUND_EVENT.entrySet().map { entry ->
		val identifier = entry.key.identifier()
		ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier))
	}.sortedBy { sound -> sound.identifier.toString() }
	private var visible: List<SoundListItem> = emptyList()
	private val rows = ScrollingStack(0, 0, 0, { visible.size }, { VIEW_HEIGHT }, { ROW_HEIGHT })
	private val titleMemo = DhenType.memo()
	private val searchMemo = DhenType.memo()
	private var category = SoundCategory.ALL
	private var query = ""
	private var searchFocused = false

	override val textInputFocused: Boolean
		get() = searchFocused

	override fun init() {
		updateFilter()
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val outsideWorld = minecraft.level == null
		if (outsideWorld) extractPanorama(graphics, a)
		if (Effects.reduced) {
			if (outsideWorld) extractMenuBackground(graphics)
			return
		}
		extractBlurredBackground(graphics)
		GlassGui.scrim(graphics, width, height)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val left = (width - WINDOW_WIDTH) / 2
		val top = (height - WINDOW_HEIGHT) / 2
		val right = left + WINDOW_WIDTH
		val bottom = top + WINDOW_HEIGHT
		val sidebarRight = left + SIDEBAR_WIDTH
		GlassGui.roundedFrame(graphics, left, top, right, bottom, WINDOW_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		graphics.enableScissor(left + HAIRLINE_INSET, top + HAIRLINE_INSET, sidebarRight, bottom - HAIRLINE_INSET)
		RoundedGui.fill(
			graphics,
			left + HAIRLINE_INSET,
			top + HAIRLINE_INSET,
			sidebarRight + WINDOW_RADIUS.toInt(),
			bottom - HAIRLINE_INSET,
			WINDOW_RADIUS - HAIRLINE_INSET,
			GlassGui.surface()
		)
		graphics.disableScissor()
		SharpGui.fill(graphics, left + HAIRLINE_INSET, top + HAIRLINE_INSET, right - HAIRLINE_INSET, top + 2, DhenPalette.accent)
		drawCentered(graphics, titleMemo, TITLE, sidebarRight, right, top + TITLE_TOP, DhenPalette.TEXT_PRIMARY)
		drawCategories(graphics, mouseX, mouseY, left, top)
		drawList(graphics, mouseX, mouseY, left, top)
		drawSearch(graphics, left, bottom)
	}

	private fun drawCategories(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int) {
		var index = 0
		while (index < SoundCategory.entries.size) {
			val entry = SoundCategory.entries[index]
			val rowTop = top + CATEGORY_TOP + index * CATEGORY_HEIGHT
			val selected = category == entry
			val hovered = mouseX in left until left + SIDEBAR_WIDTH && mouseY in rowTop until rowTop + CATEGORY_HEIGHT
			if (selected || hovered) {
				RoundedGui.fill(
					graphics,
					left + CATEGORY_INSET,
					rowTop + CATEGORY_ROW_INSET,
					left + SIDEBAR_WIDTH - CATEGORY_INSET,
					rowTop + CATEGORY_HEIGHT - CATEGORY_ROW_INSET,
					CATEGORY_RADIUS,
					if (selected) DhenPalette.accentMuted else GlassGui.interactive()
				)
			}
			DhenType.text(
				graphics,
				font,
				entry.title,
				left + CATEGORY_TEXT_INSET,
				textTop(rowTop, CATEGORY_HEIGHT),
				if (selected) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
			)
			index++
		}
	}

	private fun drawList(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int) {
		val viewLeft = left + VIEW_LEFT
		val viewTop = top + VIEW_TOP
		val viewRight = viewLeft + VIEW_WIDTH
		val viewBottom = viewTop + VIEW_HEIGHT
		val offset = rows.offset
		graphics.enableScissor(viewLeft, viewTop, viewRight, viewBottom)
		var index = maxOf(0, offset / ROW_HEIGHT)
		val end = minOf(visible.size, index + ceil(VIEW_HEIGHT.toDouble() / ROW_HEIGHT).toInt() + 1)
		while (index < end) {
			val rowTop = viewTop + index * ROW_HEIGHT - offset
			when (val item = visible[index]) {
				is SoundHeader -> drawHeader(graphics, item, viewLeft, rowTop)
				is ManagedSound -> drawSound(graphics, item, viewLeft, rowTop, mouseX, mouseY)
			}
			index++
		}
		graphics.disableScissor()
		drawScrollbar(graphics, left, viewTop)
	}

	private fun drawHeader(graphics: GuiGraphicsExtractor, header: SoundHeader, left: Int, top: Int) {
		RoundedGui.fill(graphics, left, top + ROW_INSET, left + VIEW_WIDTH, top + ROW_HEIGHT - ROW_INSET, ROW_RADIUS, GlassGui.raised())
		drawCentered(graphics, header.memo, header.category.title, left, left + VIEW_WIDTH, textTop(top, ROW_HEIGHT), DhenPalette.accent)
	}

	private fun drawSound(
		graphics: GuiGraphicsExtractor,
		sound: ManagedSound,
		left: Int,
		top: Int,
		mouseX: Int,
		mouseY: Int
	) {
		if (mouseX in left until left + VIEW_WIDTH && mouseY in top until top + ROW_HEIGHT) {
			RoundedGui.fill(graphics, left, top + ROW_INSET, left + VIEW_WIDTH, top + ROW_HEIGHT - ROW_INSET, ROW_RADIUS, GlassGui.interactive())
		}
		val shown = sound.memo.fit(font, sound.cleanName, VIEW_WIDTH - NAME_PAD - ROW_SIDE_PAD)
		sound.memo.text(graphics, font, shown, left + NAME_PAD, textTop(top, ROW_HEIGHT), DhenPalette.TEXT_PRIMARY)
	}

	private fun drawScrollbar(graphics: GuiGraphicsExtractor, left: Int, viewTop: Int) {
		val max = rows.max()
		if (max <= 0) return
		val trackLeft = left + SCROLLBAR_LEFT
		val thumbHeight = ClickGuiScroll.thumbHeight(VIEW_HEIGHT, VIEW_HEIGHT, max, MIN_THUMB_HEIGHT)
		val thumbTop = ClickGuiScroll.thumbTop(viewTop, VIEW_HEIGHT, thumbHeight, rows.offset, max)
		RoundedGui.pill(graphics, trackLeft, viewTop, trackLeft + SCROLLBAR_WIDTH, viewTop + VIEW_HEIGHT, GlassGui.raised())
		RoundedGui.pill(graphics, trackLeft, thumbTop, trackLeft + SCROLLBAR_WIDTH, thumbTop + thumbHeight, DhenPalette.accentMuted)
	}

	private fun drawSearch(graphics: GuiGraphicsExtractor, left: Int, bottom: Int) {
		val searchLeft = left + SEARCH_LEFT
		val searchTop = bottom - SEARCH_BOTTOM - SEARCH_HEIGHT
		RoundedGui.pillFrame(
			graphics,
			searchLeft,
			searchTop,
			searchLeft + SEARCH_WIDTH,
			searchTop + SEARCH_HEIGHT,
			GlassGui.interactive(),
			if (searchFocused) DhenPalette.accent else DhenPalette.BORDER
		)
		if (query.isEmpty() && !searchFocused) {
			drawCentered(graphics, searchMemo, SEARCH_PLACEHOLDER, searchLeft, searchLeft + SEARCH_WIDTH, textTop(searchTop, SEARCH_HEIGHT), DhenPalette.TEXT_DISABLED)
			return
		}
		val shown = searchMemo.fit(font, query, SEARCH_WIDTH - 2 * SEARCH_TEXT_PAD, fromEnd = true)
		val textLeft = searchLeft + SEARCH_TEXT_PAD
		searchMemo.text(graphics, font, shown, textLeft, textTop(searchTop, SEARCH_HEIGHT), DhenPalette.TEXT_PRIMARY)
		if (searchFocused) {
			val caret = textLeft + searchMemo.width(font, shown)
			SharpGui.fill(graphics, caret, searchTop + CARET_INSET, caret + 1, searchTop + SEARCH_HEIGHT - CARET_INSET, DhenPalette.accent)
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val mouseX = event.x().toInt()
		val mouseY = event.y().toInt()
		val left = (width - WINDOW_WIDTH) / 2
		val top = (height - WINDOW_HEIGHT) / 2
		var index = 0
		while (index < SoundCategory.entries.size) {
			val rowTop = top + CATEGORY_TOP + index * CATEGORY_HEIGHT
			if (mouseX in left until left + SIDEBAR_WIDTH && mouseY in rowTop until rowTop + CATEGORY_HEIGHT) {
				val selected = SoundCategory.entries[index]
				if (category != selected) {
					category = selected
					updateFilter()
				}
				searchFocused = false
				return true
			}
			index++
		}
		val searchLeft = left + SEARCH_LEFT
		val searchTop = top + WINDOW_HEIGHT - SEARCH_BOTTOM - SEARCH_HEIGHT
		if (mouseX in searchLeft until searchLeft + SEARCH_WIDTH && mouseY in searchTop until searchTop + SEARCH_HEIGHT) {
			searchFocused = true
			return true
		}
		searchFocused = false
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		val delta = ((scrollY + scrollX) * ROW_HEIGHT * WHEEL_ROWS).roundToInt()
		if (delta == 0 || !rows.scrollBy(delta)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose()
			return true
		}
		if (!searchFocused || event.key() != GLFW.GLFW_KEY_BACKSPACE || query.isEmpty()) return super.keyPressed(event)
		query = query.substring(0, query.offsetByCodePoints(query.length, -1))
		updateFilter()
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (!searchFocused || !Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) return super.charTyped(event)
		query += String(Character.toChars(codepoint))
		updateFilter()
		return true
	}

	override fun onClose() = minecraft.gui.setScreen(parent)

	private fun updateFilter() {
		visible = filterSounds(sounds, category, query.lowercase(Locale.ROOT))
		rows.scrollTo(0)
	}

	private fun drawCentered(
		graphics: GuiGraphicsExtractor,
		memo: TextMemo,
		text: String,
		left: Int,
		right: Int,
		top: Int,
		color: Int
	) {
		memo.text(graphics, font, text, left + (right - left - memo.width(font, text)) / 2, top, color)
	}

	private fun textTop(top: Int, height: Int): Int = top + (height - DhenType.lineHeight(font)) / 2

	private companion object {
		const val TITLE = "Sound Manager"
		const val SEARCH_PLACEHOLDER = "Search sounds..."
		const val WINDOW_WIDTH = 540
		const val WINDOW_HEIGHT = 285
		const val WINDOW_RADIUS = 6f
		const val SIDEBAR_WIDTH = 108
		const val TITLE_TOP = 8
		const val CATEGORY_TOP = 30
		const val CATEGORY_HEIGHT = 20
		const val CATEGORY_INSET = 4
		const val CATEGORY_TEXT_INSET = 10
		const val CATEGORY_ROW_INSET = 1
		const val CATEGORY_RADIUS = 3f
		const val VIEW_LEFT = 118
		const val VIEW_TOP = 26
		const val VIEW_WIDTH = 398
		const val VIEW_HEIGHT = 215
		const val ROW_HEIGHT = 26
		const val ROW_INSET = 1
		const val ROW_RADIUS = 3f
		const val ROW_SIDE_PAD = 8
		const val NAME_PAD = 5
		const val SCROLLBAR_LEFT = 522
		const val SCROLLBAR_WIDTH = 6
		const val MIN_THUMB_HEIGHT = 28
		const val SEARCH_LEFT = 207
		const val SEARCH_WIDTH = 200
		const val SEARCH_HEIGHT = 20
		const val SEARCH_BOTTOM = 10
		const val SEARCH_TEXT_PAD = 8
		const val CARET_INSET = 5
		const val WHEEL_ROWS = 2
	}
}

internal enum class SoundCategory(val title: String) {
	ALL("All"),
	RECENT("Recent"),
	BLOCKS("Blocks"),
	HOSTILE_MOBS("Hostile Mobs"),
	NEUTRAL_MOBS("Neutral Mobs"),
	MUSIC("Music"),
	AMBIENT("Ambient"),
	ITEMS("Items"),
	UI("UI"),
	MISC("Misc")
}

internal sealed interface SoundListItem

internal class SoundHeader(val category: SoundCategory) : SoundListItem {
	val memo = DhenType.memo()
}

internal class ManagedSound(
	val identifier: Identifier,
	val cleanName: String,
	val category: SoundCategory
) : SoundListItem {
	val memo = DhenType.memo()
	val searchText = "$identifier $cleanName"
}

internal fun soundCleanName(identifier: Identifier): String {
	val path = identifier.path
	val trimmed = when {
		path.startsWith("entity.hostile.") -> path.removePrefix("entity.hostile.")
		path.startsWith("entity.") -> path.removePrefix("entity.")
		'.' in path -> path.substringAfter('.')
		else -> path
	}
	return trimmed.replace('.', ' ').replace('_', ' ')
}

internal fun soundCategory(identifier: Identifier): SoundCategory = when {
	identifier.path.startsWith("block") -> SoundCategory.BLOCKS
	identifier.path.startsWith("entity.hostile") -> SoundCategory.HOSTILE_MOBS
	identifier.path.startsWith("entity") -> SoundCategory.NEUTRAL_MOBS
	identifier.path.startsWith("music") -> SoundCategory.MUSIC
	identifier.path.startsWith("ambient") -> SoundCategory.AMBIENT
	identifier.path.startsWith("item") -> SoundCategory.ITEMS
	identifier.path.startsWith("ui") -> SoundCategory.UI
	else -> SoundCategory.MISC
}

internal fun filterSounds(sounds: List<ManagedSound>, category: SoundCategory, query: String): List<SoundListItem> = buildList {
	if (category == SoundCategory.RECENT) return@buildList
	var group = SoundCategory.BLOCKS.ordinal
	while (group <= SoundCategory.MISC.ordinal) {
		val current = SoundCategory.entries[group]
		if (category == SoundCategory.ALL || category == current) {
			var headerAdded = false
			var index = 0
			while (index < sounds.size) {
				val sound = sounds[index]
				if (sound.category == current && sound.searchText.contains(query)) {
					if (!headerAdded) {
						add(SoundHeader(current))
						headerAdded = true
					}
					add(sound)
				}
				index++
			}
		}
		group++
	}
}
