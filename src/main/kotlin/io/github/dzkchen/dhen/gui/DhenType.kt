package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB

internal const val ELLIPSIS = "…"

private const val UNMEASURED = -1
private const val SHADOW_PIXELS = 1f
private const val SHADOW_DIM = 0.25f

internal inline fun elide(text: String, maxWidth: Int, fromEnd: Boolean, measure: (String) -> Int): String {
	if (measure(text) <= maxWidth) return text
	val room = maxWidth - measure(ELLIPSIS)
	if (room < 0) return ""
	val step = if (fromEnd) 1 else -1
	val exhausted = if (fromEnd) text.length else 0
	var cut = if (fromEnd) 0 else text.length
	while (true) {
		cut = text.offsetByCodePoints(cut, step)
		if (cut == exhausted) return ELLIPSIS
		val part = if (fromEnd) text.substring(cut) else text.substring(0, cut)
		if (measure(part) <= room) return if (fromEnd) ELLIPSIS + part else part + ELLIPSIS
	}
}

private fun measure(font: Font, component: Component): Int = font.width(component.visualOrderText)

private fun measure(font: Font, text: String): Int = measure(font, DhenType.component(text))

internal class TextMemo {
	private var source = ""
	private var sourceWidth = UNMEASURED
	private var shown = ""
	private var component = BLANK
	private var shownWidth = UNMEASURED
	private var fitWidth = UNMEASURED
	private var fitFromEnd = false

	fun width(font: Font, text: String): Int {
		hold(text)
		if (shownWidth == UNMEASURED) shownWidth = measure(font, component)
		return shownWidth
	}

	fun fit(font: Font, text: String, maxWidth: Int, fromEnd: Boolean = false): String {
		val room = maxOf(maxWidth, 0)
		if (text != source) {
			source = text
			sourceWidth = UNMEASURED
		}
		if (sourceWidth != UNMEASURED && room == fitWidth && fromEnd == fitFromEnd) return shown
		if (sourceWidth == UNMEASURED) sourceWidth = width(font, text)
		when {
			room == 0 -> {
				hold("")
				shownWidth = 0
			}
			sourceWidth <= room -> {
				hold(text)
				shownWidth = sourceWidth
			}
			else -> hold(elide(text, room, fromEnd) { measure(font, it) })
		}
		fitWidth = room
		fitFromEnd = fromEnd
		return shown
	}

	fun text(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, shadow: Boolean = false) {
		hold(text)
		graphics.text(font, component, x, y, color, shadow)
	}

	fun shadowed(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, scale: Float) {
		hold(text)
		val pose = graphics.pose()
		val offset = DhenType.shadowOffset(scale)
		pose.translate(offset, offset)
		graphics.text(font, component, x, y, ARGB.scaleRGB(color, SHADOW_DIM), false)
		pose.translate(-offset, -offset)
		graphics.text(font, component, x, y, color, false)
	}

	fun invalidate() {
		sourceWidth = UNMEASURED
		shownWidth = UNMEASURED
		fitWidth = UNMEASURED
	}

	private fun hold(text: String) {
		if (text == shown) return
		shown = text
		component = DhenType.component(text)
		shownWidth = UNMEASURED
		fitWidth = UNMEASURED
	}

	private companion object {
		val BLANK: Component = DhenType.component("")
	}
}

internal object DhenType {
	const val CACHE_LIMIT = 512

	private const val FONT_NAME = "inter"
	private const val LOAD_FACTOR = 0.75f

	val fontId: Identifier = Identifier.fromNamespaceAndPath(Dhen.MOD_ID, FONT_NAME)

	private val style: Style = Style.EMPTY.withFont(FontDescription.Resource(fontId))
	private val cache = object : LinkedHashMap<String, Styled>(CACHE_LIMIT, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Styled>): Boolean = size > CACHE_LIMIT
	}

	private var unicodeForced = false
	private var japaneseVariants = false

	fun memo(): TextMemo = TextMemo()

	fun component(text: String): Component = Component.literal(text).setStyle(style)

	fun overWorld(text: String): Component =
		Component.literal(text).setStyle(style.withColor(Color(DhenPalette.TEXT_ON_WORLD).rgb))

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
