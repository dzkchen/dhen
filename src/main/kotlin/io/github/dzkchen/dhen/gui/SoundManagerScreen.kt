package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.input.TextInputTarget
import io.github.dzkchen.dhen.sound.SoundManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Util
import net.minecraft.world.entity.MobCategory
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class SoundManagerScreen(private val parent: Screen) : LiveWorldScreen(Component.literal(TITLE)), TextInputTarget {
	private val sounds = BuiltInRegistries.SOUND_EVENT.entrySet().map { entry ->
		val identifier = entry.key.identifier()
		ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier), entry.value)
	}.sortedBy { sound -> sound.identifier.toString() }
	private val soundsById = sounds.associateBy(ManagedSound::identifier)
	private var visible: List<SoundListItem> = emptyList()
	private val rows = ScrollingStack(0, 0, 0, { visible.size }, { VIEW_HEIGHT }, { ROW_HEIGHT })
	private val titleMemo = DhenType.memo()
	private val searchMemo = DhenType.memo()
	private val playMemo = DhenType.memo()
	private var category = SoundCategory.ALL
	private var query = ""
	private var searchFocused = false
	private var recentSoundsVersion = -1L
	private var recentIdentifiers: List<Identifier> = emptyList()
	private var scrollShown = 0f
	private var scrollFrom = 0f
	private var scrollStartedAt = 0L
	private var scrollAnimating = false
	private var draggedSound: ManagedSound? = null
	private var draggingScrollbar = false
	private var scrollbarDragOffset = 0

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
		refreshRecent()
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
		drawList(graphics, mouseX, mouseY, left, top, currentScroll(Util.getMillis()).roundToInt())
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

	private fun drawList(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int, offset: Int) {
		val viewLeft = left + VIEW_LEFT
		val viewTop = top + VIEW_TOP
		val viewRight = viewLeft + VIEW_WIDTH
		val viewBottom = viewTop + VIEW_HEIGHT
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
		drawScrollbar(graphics, left, viewTop, offset)
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
		val playLeft = left + VIEW_WIDTH - PLAY_WIDTH - ROW_SIDE_PAD
		val sliderLeft = playLeft - CONTROL_GAP - SLIDER_WIDTH
		val sliderRight = sliderLeft + SLIDER_WIDTH
		val playTop = top + PLAY_TOP
		val playBottom = playTop + PLAY_HEIGHT
		if (mouseX in left until left + VIEW_WIDTH && mouseY in top until top + ROW_HEIGHT) {
			RoundedGui.fill(graphics, left, top + ROW_INSET, left + VIEW_WIDTH, top + ROW_HEIGHT - ROW_INSET, ROW_RADIUS, GlassGui.interactive())
		}
		if (SoundManager.hasRule(sound.identifier)) {
			RoundedGui.pill(
				graphics,
				left,
				top + RULE_MARKER_INSET,
				left + RULE_MARKER_WIDTH,
				top + ROW_HEIGHT - RULE_MARKER_INSET,
				DhenPalette.accent
			)
		}
		val shown = sound.memo.fit(font, sound.cleanName, sliderLeft - left - NAME_PAD - NAME_CONTROL_GAP)
		sound.memo.text(graphics, font, shown, left + NAME_PAD, textTop(top, ROW_HEIGHT), DhenPalette.TEXT_PRIMARY)
		val volume = SoundManager.getVolumePercent(sound.identifier)
		val value = sound.volumeLabel(volume)
		sound.valueMemo.text(
			graphics,
			font,
			value,
			sliderRight - sound.valueMemo.width(font, value),
			top + VALUE_TOP,
			DhenPalette.TEXT_SECONDARY
		)
		val progress = volume.toFloat() / MAX_VOLUME_PERCENT
		val edge = RoundedGui.capsuleTrack(
			graphics,
			sliderLeft,
			top + SLIDER_TOP,
			sliderRight,
			top + SLIDER_TOP + SLIDER_HEIGHT,
			progress,
			GlassGui.raised(),
			DhenPalette.accent
		)
		RoundedGui.circle(
			graphics,
			edge.coerceIn(sliderLeft + SLIDER_KNOB_RADIUS, sliderRight - SLIDER_KNOB_RADIUS),
			top + SLIDER_TOP + SLIDER_HEIGHT / 2,
			SLIDER_KNOB_RADIUS,
			DhenPalette.TEXT_PRIMARY
		)
		val playHovered = mouseX in playLeft until playLeft + PLAY_WIDTH && mouseY in playTop until playBottom
		RoundedGui.fill(
			graphics,
			playLeft,
			playTop,
			playLeft + PLAY_WIDTH,
			playBottom,
			PLAY_RADIUS,
			if (playHovered) DhenPalette.accentMuted else GlassGui.raised()
		)
		drawCentered(
			graphics,
			playMemo,
			PLAY_LABEL,
			playLeft,
			playLeft + PLAY_WIDTH,
			textTop(playTop, PLAY_HEIGHT),
			if (playHovered) DhenPalette.TEXT_PRIMARY else DhenPalette.TEXT_SECONDARY
		)
	}

	private fun drawScrollbar(graphics: GuiGraphicsExtractor, left: Int, viewTop: Int, offset: Int) {
		val max = rows.max()
		if (max <= 0) {
			draggingScrollbar = false
			return
		}
		val trackLeft = left + SCROLLBAR_LEFT
		val thumbHeight = ClickGuiScroll.thumbHeight(VIEW_HEIGHT, VIEW_HEIGHT, max, MIN_THUMB_HEIGHT)
		val thumbTop = ClickGuiScroll.thumbTop(viewTop, VIEW_HEIGHT, thumbHeight, offset, max)
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
		val viewLeft = left + VIEW_LEFT
		val viewTop = top + VIEW_TOP
		val maxScroll = rows.max()
		if (maxScroll > 0) {
			val trackLeft = left + SCROLLBAR_LEFT
			if (mouseX in trackLeft - SCROLLBAR_HIT_PAD until trackLeft + SCROLLBAR_WIDTH + SCROLLBAR_HIT_PAD &&
				mouseY in viewTop until viewTop + VIEW_HEIGHT
			) {
				val offset = currentScroll(Util.getMillis()).roundToInt()
				val thumbHeight = ClickGuiScroll.thumbHeight(VIEW_HEIGHT, VIEW_HEIGHT, maxScroll, MIN_THUMB_HEIGHT)
				val thumbTop = ClickGuiScroll.thumbTop(viewTop, VIEW_HEIGHT, thumbHeight, offset, maxScroll)
				scrollbarDragOffset = if (mouseY in thumbTop until thumbTop + thumbHeight) mouseY - thumbTop else thumbHeight / 2
				draggingScrollbar = true
				if (mouseY in thumbTop until thumbTop + thumbHeight) {
					rows.scrollTo(offset)
					snapScroll(rows.offset.toFloat())
				} else {
					dragScrollbar(mouseY, viewTop, thumbHeight, maxScroll)
				}
				return true
			}
		}
		if (mouseX in viewLeft until viewLeft + VIEW_WIDTH && mouseY in viewTop until viewTop + VIEW_HEIGHT) {
			val offset = currentScroll(Util.getMillis()).roundToInt()
			val index = (mouseY - viewTop + offset) / ROW_HEIGHT
			val sound = visible.getOrNull(index) as? ManagedSound
			if (sound != null) {
				val rowTop = viewTop + index * ROW_HEIGHT - offset
				val playLeft = viewLeft + VIEW_WIDTH - PLAY_WIDTH - ROW_SIDE_PAD
				val sliderLeft = playLeft - CONTROL_GAP - SLIDER_WIDTH
				if (mouseX in playLeft until playLeft + PLAY_WIDTH && mouseY in rowTop + PLAY_TOP until rowTop + PLAY_TOP + PLAY_HEIGHT) {
					SoundManager.playPreview(sound.event)
					return true
				}
				if (mouseX in sliderLeft - SLIDER_HIT_PAD until sliderLeft + SLIDER_WIDTH + SLIDER_HIT_PAD) {
					draggedSound = sound
					setVolume(sound, mouseX, sliderLeft)
					return true
				}
			}
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val sound = draggedSound
		if (sound != null) {
			val left = (width - WINDOW_WIDTH) / 2
			val viewLeft = left + VIEW_LEFT
			val playLeft = viewLeft + VIEW_WIDTH - PLAY_WIDTH - ROW_SIDE_PAD
			setVolume(sound, event.x().toInt(), playLeft - CONTROL_GAP - SLIDER_WIDTH)
			return true
		}
		if (draggingScrollbar) {
			val top = (height - WINDOW_HEIGHT) / 2
			val viewTop = top + VIEW_TOP
			val maxScroll = rows.max()
			if (maxScroll > 0) {
				val thumbHeight = ClickGuiScroll.thumbHeight(VIEW_HEIGHT, VIEW_HEIGHT, maxScroll, MIN_THUMB_HEIGHT)
				dragScrollbar(event.y().toInt(), viewTop, thumbHeight, maxScroll)
			}
			return true
		}
		return super.mouseDragged(event, dragX, dragY)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (draggedSound == null && !draggingScrollbar) return super.mouseReleased(event)
		draggedSound = null
		draggingScrollbar = false
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		val delta = ((scrollY + scrollX) * ROW_HEIGHT * WHEEL_ROWS).roundToInt()
		if (delta == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val now = Util.getMillis()
		val current = currentScroll(now)
		if (!rows.scrollBy(delta)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		beginScroll(current, now)
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

	private fun updateFilter(resetScroll: Boolean = true) {
		val lowered = query.lowercase(Locale.ROOT)
		visible = if (category == SoundCategory.RECENT) recentSounds(lowered) else filterSounds(sounds, category, lowered)
		if (resetScroll) {
			rows.scrollTo(0)
			snapScroll(0f)
		} else {
			val previousTarget = rows.offset
			rows.reclamp()
			if (rows.offset != previousTarget) snapScroll(minOf(scrollShown, rows.offset.toFloat()))
		}
	}

	private fun recentSounds(lowered: String): List<SoundListItem> {
		refreshRecentIdentifiers()
		return filterRecentSounds(soundsById, recentIdentifiers, lowered)
	}

	private fun refreshRecentIdentifiers() {
		if (recentSoundsVersion == SoundManager.recentSoundsVersion) return
		var version: Long
		var identifiers: List<Identifier>
		do {
			version = SoundManager.recentSoundsVersion
			identifiers = SoundManager.recentSoundIds()
		} while (version != SoundManager.recentSoundsVersion)
		recentIdentifiers = identifiers
		recentSoundsVersion = version
	}

	private fun refreshRecent() {
		if (category == SoundCategory.RECENT && recentSoundsVersion != SoundManager.recentSoundsVersion) updateFilter(resetScroll = false)
	}

	private fun setVolume(sound: ManagedSound, mouseX: Int, sliderLeft: Int) {
		val percent = soundVolumePercent(mouseX, sliderLeft, SLIDER_WIDTH)
		if (percent != SoundManager.getVolumePercent(sound.identifier)) SoundManager.setVolumePercent(sound.identifier, percent)
	}

	private fun dragScrollbar(mouseY: Int, viewTop: Int, thumbHeight: Int, maxScroll: Int) {
		rows.scrollTo(soundScrollOffset(mouseY, viewTop, VIEW_HEIGHT, thumbHeight, scrollbarDragOffset, maxScroll))
		snapScroll(rows.offset.toFloat())
	}

	private fun beginScroll(current: Float, now: Long) {
		scrollShown = current
		scrollFrom = current
		scrollStartedAt = now
		scrollAnimating = current != rows.offset.toFloat()
	}

	private fun currentScroll(now: Long): Float {
		if (!scrollAnimating) {
			scrollShown = rows.offset.toFloat()
			return scrollShown
		}
		val target = rows.offset.toFloat()
		scrollShown = if (Effects.reduced) target else animatedSoundScroll(scrollFrom, target, now - scrollStartedAt)
		if (scrollShown == target) scrollAnimating = false
		return scrollShown
	}

	private fun snapScroll(offset: Float) {
		scrollShown = offset
		scrollFrom = offset
		scrollAnimating = false
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
		const val RULE_MARKER_WIDTH = 2
		const val RULE_MARKER_INSET = 5
		const val NAME_CONTROL_GAP = 12
		const val SLIDER_WIDTH = 140
		const val SLIDER_TOP = 17
		const val SLIDER_HEIGHT = 5
		const val SLIDER_KNOB_RADIUS = 3
		const val SLIDER_HIT_PAD = 5
		const val VALUE_TOP = 4
		const val CONTROL_GAP = 8
		const val PLAY_LABEL = "Play"
		const val PLAY_WIDTH = 34
		const val PLAY_HEIGHT = 15
		const val PLAY_TOP = 6
		const val PLAY_RADIUS = 3f
		const val SCROLLBAR_LEFT = 522
		const val SCROLLBAR_WIDTH = 6
		const val SCROLLBAR_HIT_PAD = 3
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
	PASSIVE_MOBS("Passive Mobs"),
	PLAYER("Player"),
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
	val category: SoundCategory,
	val event: SoundEvent
) : SoundListItem {
	val memo = DhenType.memo()
	val valueMemo = DhenType.memo()
	val searchText = "$identifier $cleanName"
	private var shownVolume = Int.MIN_VALUE
	private var shownVolumeLabel = ""

	fun volumeLabel(percent: Int): String {
		if (shownVolume != percent) {
			shownVolume = percent
			shownVolumeLabel = "$percent%"
		}
		return shownVolumeLabel
	}
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

internal fun soundCategory(identifier: Identifier): SoundCategory {
	val path = identifier.path
	return when {
		path.startsWith(GENERIC_HOSTILE_PREFIX) -> SoundCategory.HOSTILE_MOBS
		path.startsWith(ENTITY_PREFIX) -> entitySoundCategory(entitySoundOwner(path))
		path.startsWith("block") -> SoundCategory.BLOCKS
		path.startsWith("music") -> SoundCategory.MUSIC
		path.startsWith("ambient") || path.startsWith("weather") -> SoundCategory.AMBIENT
		path.startsWith("item") -> SoundCategory.ITEMS
		path.startsWith("ui") -> SoundCategory.UI
		else -> SoundCategory.MISC
	}
}

private fun entitySoundOwner(path: String): String {
	val end = path.indexOf('.', ENTITY_PREFIX.length)
	return if (end < 0) path.substring(ENTITY_PREFIX.length) else path.substring(ENTITY_PREFIX.length, end)
}

private fun entitySoundCategory(owner: String): SoundCategory {
	val key = Identifier.withDefaultNamespace(owner)
	if (key == PLAYER_TYPE) return SoundCategory.PLAYER
	if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return SoundCategory.MISC
	val type = BuiltInRegistries.ENTITY_TYPE.getValue(key)
	if (type.category == MobCategory.MONSTER) return SoundCategory.HOSTILE_MOBS
	return if (type.defaultLootTable.isPresent) SoundCategory.PASSIVE_MOBS else SoundCategory.MISC
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

internal fun filterRecentSounds(
	soundsById: Map<Identifier, ManagedSound>,
	recentIdentifiers: List<Identifier>,
	query: String
): List<SoundListItem> = buildList {
	var headerAdded = false
	for (identifier in recentIdentifiers) {
		val sound = soundsById[identifier] ?: continue
		if (!sound.searchText.contains(query)) continue
		if (!headerAdded) {
			add(SoundHeader(SoundCategory.RECENT))
			headerAdded = true
		}
		add(sound)
	}
}

internal fun soundVolumePercent(mouseX: Int, sliderLeft: Int, sliderWidth: Int): Int {
	if (sliderWidth <= 0) return 0
	val relative = (mouseX - sliderLeft).coerceIn(0, sliderWidth)
	val raw = (relative.toDouble() * MAX_VOLUME_PERCENT / sliderWidth).roundToInt()
	return ((raw + VOLUME_STEP_PERCENT / 2) / VOLUME_STEP_PERCENT) * VOLUME_STEP_PERCENT
}

internal fun animatedSoundScroll(from: Float, target: Float, elapsed: Long): Float =
	GlassGui.tween(from, target, elapsed, SCROLL_MILLIS)

internal fun soundScrollOffset(
	mouseY: Int,
	trackTop: Int,
	trackHeight: Int,
	thumbHeight: Int,
	dragOffset: Int,
	maxScroll: Int
): Int {
	val travel = maxOf(0, trackHeight - thumbHeight)
	if (travel == 0 || maxScroll <= 0) return 0
	val thumbTop = (mouseY - trackTop - dragOffset).coerceIn(0, travel)
	return ((thumbTop.toLong() * maxScroll + travel / 2) / travel).toInt()
}

private const val ENTITY_PREFIX = "entity."
private const val GENERIC_HOSTILE_PREFIX = "entity.hostile."
private val PLAYER_TYPE = Identifier.withDefaultNamespace("player")
private const val MAX_VOLUME_PERCENT = 200
private const val VOLUME_STEP_PERCENT = 5
private const val SCROLL_MILLIS = 200L
