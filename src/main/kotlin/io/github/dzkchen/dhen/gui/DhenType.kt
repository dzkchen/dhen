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

internal object DhenType {
	const val CACHE_LIMIT = 512

	private const val FONT_NAME = "inter"
	private const val UNMEASURED = -1
	private const val LOAD_FACTOR = 0.75f
	private const val SHADOW_PIXELS = 1f
	private const val SHADOW_DIM = 0.25f

	val fontId: Identifier = Identifier.fromNamespaceAndPath(Dhen.MOD_ID, FONT_NAME)

	private val style: Style = Style.EMPTY.withFont(FontDescription.Resource(fontId))
	private val cache = object : LinkedHashMap<String, Styled>(CACHE_LIMIT, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Styled>): Boolean = size > CACHE_LIMIT
	}

	private var unicodeForced = false
	private var japaneseVariants = false

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

	fun shadowed(
		graphics: GuiGraphicsExtractor,
		font: Font,
		text: String,
		x: Int,
		y: Int,
		color: Int,
		scale: Float
	) {
		val pose = graphics.pose()
		val offset = shadowOffset(scale)
		pose.translate(offset, offset)
		text(graphics, font, text, x, y, ARGB.scaleRGB(color, SHADOW_DIM))
		pose.translate(-offset, -offset)
		text(graphics, font, text, x, y, color)
	}

	fun width(font: Font, text: String): Int = measured(font, cached(text))

	fun fit(font: Font, text: String, maxWidth: Int, fromEnd: Boolean = false): String {
		if (maxWidth <= 0) return ""
		val entry = cached(text)
		if (measured(font, entry) <= maxWidth) return text
		if (entry.fitWidth != maxWidth || entry.fitFromEnd != fromEnd) {
			entry.fitWidth = maxWidth
			entry.fitFromEnd = fromEnd
			entry.fitted = elide(text, maxWidth, fromEnd) { font.width(component(it).visualOrderText) }
		}
		return entry.fitted
	}

	fun lineHeight(font: Font): Int = font.lineHeight

	fun fontOptionsChanged(forceUnicode: Boolean, japaneseGlyphVariants: Boolean): Boolean {
		if (forceUnicode == unicodeForced && japaneseGlyphVariants == japaneseVariants) return false
		unicodeForced = forceUnicode
		japaneseVariants = japaneseGlyphVariants
		return true
	}

	fun invalidateMeasurements() {
		for (entry in cache.values) {
			entry.width = UNMEASURED
			entry.fitWidth = UNMEASURED
		}
	}

	private fun measured(font: Font, entry: Styled): Int {
		if (entry.width == UNMEASURED) entry.width = font.width(entry.component.visualOrderText)
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
		var fitWidth = UNMEASURED
		var fitFromEnd = false
		var fitted = ""
	}
}
