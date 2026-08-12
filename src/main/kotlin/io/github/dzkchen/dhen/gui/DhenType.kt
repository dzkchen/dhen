package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB

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

	fun width(font: Font, text: String): Int {
		val entry = cached(text)
		if (entry.width == UNMEASURED) entry.width = font.width(entry.component.visualOrderText)
		return entry.width
	}

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
