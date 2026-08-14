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

internal class StubFont : Font(TenPixelGlyphs) {
	var measurements = 0
		private set

	override fun width(text: FormattedCharSequence): Int {
		measurements++
		return super.width(text)
	}
}

private object TenPixelGlyphs : Font.Provider, GlyphSource, BakedGlyph {
	private val square = GlyphInfo.simple(STUB_GLYPH_WIDTH.toFloat())

	override fun glyphs(font: FontDescription): GlyphSource = this

	override fun effect(): EffectGlyph = throw UnsupportedOperationException()

	override fun getGlyph(codepoint: Int): BakedGlyph = this

	override fun getRandomGlyph(random: RandomSource, width: Int): BakedGlyph = this

	override fun info(): GlyphInfo = square

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
