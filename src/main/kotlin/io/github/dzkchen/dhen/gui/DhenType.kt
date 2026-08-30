package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.vertex.PoseStack
import io.github.dzkchen.dhen.util.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence

internal const val ELLIPSIS = "…"

private const val UNMEASURED = -1
private const val SHADOW_PIXELS = 1f
private const val WORLD_SHADOW_OFFSET = 0.5f
private const val WORLD_SHADOW_ORDER = 0
private const val WORLD_FOREGROUND_ORDER = 1
private const val SHADOW_DIM = 0.25f
private const val DEFAULT_WRAP_LINES = 2

internal class RoomBand {
	private var shrinksBelow = 0
	private var growsAt = 0

	fun holds(room: Int): Boolean = room in shrinksBelow until growsAt

	fun set(shrinksBelow: Int, growsAt: Int) {
		this.shrinksBelow = shrinksBelow
		this.growsAt = growsAt
	}

	fun from(shrinksBelow: Int) = set(shrinksBelow, Int.MAX_VALUE)

	fun only(room: Int) = set(room, room + 1)

	fun clear() = set(0, 0)
}

internal inline fun elide(
	text: String,
	maxWidth: Int,
	fromEnd: Boolean,
	measure: (String) -> Int,
	band: RoomBand? = null
): String {
	val sourceWidth = measure(text)
	if (sourceWidth <= maxWidth) {
		band?.from(sourceWidth)
		return text
	}
	val ellipsisWidth = measure(ELLIPSIS)
	val room = maxWidth - ellipsisWidth
	if (room < 0) {
		band?.set(0, ellipsisWidth)
		return ""
	}
	val step = if (fromEnd) 1 else -1
	val exhausted = if (fromEnd) text.length else 0
	var cut = if (fromEnd) 0 else text.length
	var growsAt = sourceWidth
	while (true) {
		cut = text.offsetByCodePoints(cut, step)
		if (cut == exhausted) {
			band?.set(ellipsisWidth, growsAt)
			return ELLIPSIS
		}
		val part = if (fromEnd) text.substring(cut) else text.substring(0, cut)
		val partWidth = measure(part)
		if (partWidth <= room) {
			band?.set(partWidth + ellipsisWidth, growsAt)
			return if (fromEnd) ELLIPSIS + part else part + ELLIPSIS
		}
		growsAt = partWidth + ellipsisWidth
	}
}

internal inline fun wrapPoint(text: String, maxWidth: Int, measure: (String) -> Int): Int {
	if (maxWidth <= 0 || text.isEmpty()) return 0
	if (measure(text) <= maxWidth) return text.length
	var cut = 0
	var breakAfter = 0
	while (cut < text.length) {
		val next = text.offsetByCodePoints(cut, 1)
		if (measure(text.substring(0, next)) > maxWidth) break
		if (text[next - 1] == ' ') breakAfter = next
		cut = next
	}
	return if (breakAfter > 0) breakAfter else cut
}

private fun measure(font: Font, component: Component): Int = font.width(component.visualOrderText)

private fun measure(font: Font, text: String): Int = measure(font, DhenType.component(text))

internal class TextMemo {
	private var revision = DhenFont.revision
	private var source = ""
	private var sourceWidth = UNMEASURED
	private var shown = ""
	private var shownComponent: Component? = null
	private var component = BLANK
	private var shownWidth = UNMEASURED
	private var fitted = ""
	private var fitFromEnd = false
	private val band = RoomBand()

	fun width(font: Font, text: String): Int {
		synchronize()
		hold(text)
		if (shownWidth == UNMEASURED) shownWidth = measure(font, component)
		return shownWidth
	}

	fun width(font: Font, text: Component): Int {
		synchronize()
		hold(text)
		if (shownWidth == UNMEASURED) shownWidth = measure(font, component)
		return shownWidth
	}

	fun fit(font: Font, text: String, maxWidth: Int, fromEnd: Boolean = false): String {
		synchronize()
		val room = maxOf(maxWidth, 0)
		if (text != source) {
			source = text
			sourceWidth = UNMEASURED
			band.clear()
		}
		if (band.holds(room) && fromEnd == fitFromEnd) {
			hold(fitted)
			return fitted
		}
		if (sourceWidth == UNMEASURED) sourceWidth = width(font, text)
		when {
			room == 0 -> {
				hold("")
				shownWidth = 0
				band.only(room)
			}
			sourceWidth <= room -> {
				hold(text)
				shownWidth = sourceWidth
				band.from(sourceWidth)
			}
			else -> hold(elide(text, room, fromEnd, { measure(font, it) }, band))
		}
		fitted = shown
		fitFromEnd = fromEnd
		return fitted
	}

	fun text(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, shadow: Boolean = false) {
		synchronize()
		hold(text)
		graphics.text(font, component, x, y, color, shadow)
	}

	fun shadowed(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, scale: Float) {
		synchronize()
		hold(text)
		val pose = graphics.pose()
		val offset = DhenType.shadowOffset(scale)
		pose.translate(offset, offset)
		graphics.text(font, component, x, y, ARGB.scaleRGB(color, SHADOW_DIM), false)
		pose.translate(-offset, -offset)
		graphics.text(font, component, x, y, color, false)
	}

	fun shadowed(graphics: GuiGraphicsExtractor, font: Font, text: Component, x: Int, y: Int, color: Int, scale: Float) {
		synchronize()
		hold(text)
		val pose = graphics.pose()
		val offset = DhenType.shadowOffset(scale)
		pose.translate(offset, offset)
		graphics.text(font, component, x, y, ARGB.scaleRGB(color, SHADOW_DIM), false)
		pose.translate(-offset, -offset)
		graphics.text(font, component, x, y, color, false)
	}

	fun shadowedWorldText(
		collector: SubmitNodeCollector,
		pose: PoseStack,
		text: String,
		x: Float,
		y: Float,
		color: Int,
		displayMode: Font.DisplayMode,
		lightCoords: Int
	) {
		synchronize()
		hold(text)
		DhenType.shadowedWorldText(collector, pose, component.visualOrderText, x, y, color, displayMode, lightCoords)
	}

	fun invalidate() {
		sourceWidth = UNMEASURED
		shownWidth = UNMEASURED
		band.clear()
	}

	private fun synchronize() {
		val current = DhenFont.revision
		if (revision == current) return
		revision = current
		if (shownComponent == null) component = DhenType.component(shown)
		invalidate()
	}

	private fun hold(text: String) {
		if (shownComponent == null && text == shown) return
		shownComponent = null
		shown = text
		component = DhenType.component(text)
		shownWidth = UNMEASURED
	}

	private fun hold(text: Component) {
		if (text == shownComponent) return
		shownComponent = text
		component = text
		shownWidth = UNMEASURED
	}

	private companion object {
		val BLANK: Component = DhenType.component("")
	}
}

internal class WrappedText {
	private val memos = ArrayList<TextMemo>(DEFAULT_WRAP_LINES)
	private val shown = ArrayList<String>(DEFAULT_WRAP_LINES)
	private var revision = DhenFont.revision
	private var source = ""
	private var measuredRoom = UNMEASURED
	private var measuredLimit = 0

	var elided = false
		private set

	val lines: Int
		get() = maxOf(shown.size, 1)

	fun measure(font: Font, text: String, maxWidth: Int, limit: Int = DEFAULT_WRAP_LINES) {
		val current = DhenFont.revision
		val room = maxOf(maxWidth, 0)
		val cap = maxOf(limit, 1)
		if (current == revision && text == source && room == measuredRoom && cap == measuredLimit) return
		revision = current
		source = text
		measuredRoom = room
		measuredLimit = cap
		shown.clear()
		elided = false
		var rest = text
		while (true) {
			if (shown.size == cap - 1) {
				elided = fitted(font, rest, room) != rest
				return
			}
			val point = wrapPoint(rest, room) { measure(font, it) }
			if (point >= rest.length) {
				shown += rest
				return
			}
			if (point <= 0) {
				fitted(font, rest, room)
				elided = true
				return
			}
			shown += rest.substring(0, point).trimEnd()
			rest = rest.substring(point).trimStart()
			if (rest.isEmpty()) return
		}
	}

	fun height(font: Font, base: Int): Int = base + (lines - 1) * DhenType.lineHeight(font)

	fun blockTop(font: Font, y: Int, height: Int): Int = y + (height - lines * DhenType.lineHeight(font)) / 2

	fun widest(font: Font): Int {
		var widest = 0
		for (i in shown.indices) widest = maxOf(widest, memoAt(i).width(font, shown[i]))
		return widest
	}

	fun draw(
		graphics: GuiGraphicsExtractor,
		font: Font,
		left: Int,
		top: Int,
		color: Int,
		centered: Boolean = false
	) {
		val lineHeight = DhenType.lineHeight(font)
		for (i in shown.indices) {
			val memo = memoAt(i)
			val text = shown[i]
			val x = if (centered) left + ClickGuiShell.centeredLeft(measuredRoom, memo.width(font, text)) else left
			memo.text(graphics, font, text, x, top + i * lineHeight, color)
		}
	}

	fun invalidate() {
		for (i in memos.indices) memos[i].invalidate()
		measuredRoom = UNMEASURED
	}

	private fun fitted(font: Font, text: String, room: Int): String {
		val line = memoAt(shown.size).fit(font, text, room)
		if (line.isNotEmpty() || shown.isEmpty()) shown += line
		return line
	}

	private fun memoAt(index: Int): TextMemo {
		while (memos.size <= index) memos += TextMemo()
		return memos[index]
	}
}

internal object DhenType {
	const val CACHE_LIMIT = 512

	private const val LOAD_FACTOR = 0.75f

	val fontId = DhenFont.id

	private val cache = object : LinkedHashMap<String, Styled>(CACHE_LIMIT, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Styled>): Boolean = size > CACHE_LIMIT
	}

	private var unicodeForced = false
	private var japaneseVariants = false

	fun memo(): TextMemo = TextMemo()

	fun wrap(): WrappedText = WrappedText()

	fun component(text: String): Component = Component.literal(text).setStyle(DhenFont.style())

	fun overWorld(text: String): Component =
		overWorldComponent(text)

	fun overWorld(text: String, color: ChatFormatting): Component =
		Component.literal(text).setStyle(DhenFont.messageStyle().withColor(color))

	fun clickableCommandOverWorld(prefix: String, command: String, suffix: String): Component =
		overWorldComponent(prefix)
			.append(
				overWorldComponent(command).withStyle { style ->
					style.withClickEvent(ClickEvent.RunCommand(command))
				}
			)
			.append(overWorldComponent(suffix))

	fun copyableOverWorld(text: String, copyText: String): Component =
		Component.literal(text).setStyle(
			DhenFont.messageStyle()
				.withColor(Color(DhenPalette.TEXT_ON_WORLD).rgb)
				.withClickEvent(ClickEvent.CopyToClipboard(copyText))
		)

	private fun overWorldComponent(text: String): MutableComponent =
		Component.literal(text).setStyle(DhenFont.messageStyle().withColor(Color(DhenPalette.TEXT_ON_WORLD).rgb))

	fun styled(text: String): Component = cached(text).component

	fun text(
		graphics: GuiGraphicsExtractor,
		font: Font,
		text: String,
		x: Int,
		y: Int,
		color: Int,
		shadow: Boolean = false
	) {
		graphics.text(font, styled(text), x, y, color, shadow)
	}

	fun shadowedWorldText(
		collector: SubmitNodeCollector,
		pose: PoseStack,
		text: String,
		x: Float,
		y: Float,
		color: Int,
		displayMode: Font.DisplayMode,
		lightCoords: Int
	) {
		shadowedWorldText(collector, pose, styled(text).visualOrderText, x, y, color, displayMode, lightCoords)
	}

	internal fun shadowedWorldText(
		collector: SubmitNodeCollector,
		pose: PoseStack,
		label: FormattedCharSequence,
		x: Float,
		y: Float,
		color: Int,
		displayMode: Font.DisplayMode,
		lightCoords: Int
	) {
		collector.order(WORLD_SHADOW_ORDER).submitText(
			pose,
			x + WORLD_SHADOW_OFFSET,
			y + WORLD_SHADOW_OFFSET,
			label,
			false,
			displayMode,
			lightCoords,
			ARGB.scaleRGB(color, SHADOW_DIM),
			0,
			0
		)
		collector.order(WORLD_FOREGROUND_ORDER).submitText(
			pose,
			x,
			y,
			label,
			false,
			displayMode,
			lightCoords,
			color,
			0,
			0
		)
	}

	fun shadowOffset(scale: Float): Float =
		if (scale.isNaN() || scale <= 0f) SHADOW_PIXELS else SHADOW_PIXELS / scale

	fun width(font: Font, text: String): Int = measured(font, cached(text))

	fun lineHeight(font: Font): Int = font.lineHeight

	fun fontOptionsChanged(forceUnicode: Boolean, japaneseGlyphVariants: Boolean): Boolean {
		if (forceUnicode == unicodeForced && japaneseGlyphVariants == japaneseVariants) return false
		unicodeForced = forceUnicode
		japaneseVariants = japaneseGlyphVariants
		return true
	}

	fun invalidateMeasurements() {
		for (entry in cache.values) entry.width = UNMEASURED
	}

	internal fun fontChanged() {
		cache.clear()
	}

	private fun measured(font: Font, entry: Styled): Int {
		if (entry.width == UNMEASURED) entry.width = measure(font, entry.component)
		return entry.width
	}

	private fun cached(text: String): Styled {
		cache[text]?.let { return it }
		val entry = Styled(component(text))
		cache[text] = entry
		return entry
	}

	private class Styled(val component: Component) {
		var width = UNMEASURED
	}
}
