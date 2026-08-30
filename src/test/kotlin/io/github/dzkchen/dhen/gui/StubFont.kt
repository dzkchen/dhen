package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.font.GlyphInfo
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.RandomSource

internal const val STUB_GLYPH_WIDTH = 10
internal const val WIDE_ELLIPSIS_WIDTH = 30

internal class StubFont(ellipsisWidth: Int = STUB_GLYPH_WIDTH) : Font(StubGlyphs(ellipsisWidth)) {
	var measurements = 0
		private set

	override fun width(text: FormattedCharSequence): Int {
		measurements++
		return super.width(text)
	}
}

private class StubGlyphs(ellipsisWidth: Int) : Font.Provider, GlyphSource {
	private val square = StubGlyph(STUB_GLYPH_WIDTH)
	private val ellipsis = StubGlyph(ellipsisWidth)

	override fun glyphs(font: FontDescription): GlyphSource = this

	override fun effect(): EffectGlyph = throw UnsupportedOperationException()

	override fun getGlyph(codepoint: Int): BakedGlyph =
		if (codepoint == ELLIPSIS.codePointAt(0)) ellipsis else square

	override fun getRandomGlyph(random: RandomSource, width: Int): BakedGlyph = square
}

private class StubGlyph(width: Int) : BakedGlyph {
	private val glyph = GlyphInfo.simple(width.toFloat())

	override fun info(): GlyphInfo = glyph

	override fun createGlyph(
		x: Float,
		y: Float,
		color: Int,
		shadowColor: Int,
		style: Style,
		boldOffset: Float,
		shadowOffset: Float
	): TextRenderable.Styled? = null
}
