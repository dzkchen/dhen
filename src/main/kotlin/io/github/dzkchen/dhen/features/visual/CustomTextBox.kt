package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudElement
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object CustomTextBox : Module(
	name = "Custom Text Box",
	category = Category.VISUAL,
	description = "Draws lines of your own text on screen, wherever you put them in the HUD editor."
) {
	internal val textSetting = StringSetting(
		"Text",
		DEFAULT_TEXT,
		MAX_LENGTH,
		"What the box says. Write & for a colour code and \\n where a new line starts."
	)

	init {
		registerSetting(textSetting)
		hud(CustomTextElement())
	}
}

internal class CustomTextElement : HudElement("Custom Text", offsetX = MARGIN, offsetY = MARGIN) {
	private var memos: Array<TextMemo> = emptyArray()
	private var source: String? = null
	private var lines: List<String> = emptyList()

	override val hasContent: Boolean
		get() = shownLines().isNotEmpty()

	override fun width(font: Font): Int {
		val shown = shownLines()
		var width = 1
		for (index in shown.indices) width = maxOf(width, memos[index].width(font, shown[index]))
		return width
	}

	override fun height(font: Font): Int = maxOf(1, shownLines().size) * DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val shown = shownLines()
		val lineHeight = DhenType.lineHeight(font)
		for (index in shown.indices) {
			memos[index].shadowed(graphics, font, shown[index], 0, index * lineHeight, textInk, scale)
		}
	}

	override fun invalidateMeasurement() {
		for (memo in memos) memo.invalidate()
	}

	private fun shownLines(): List<String> {
		val text = CustomTextBox.textSetting.value
		if (text != source) {
			source = text
			lines = if (text.isBlank()) emptyList() else text.replace(CODE, SECTION).split(BREAK)
			if (memos.size < lines.size) memos = Array(lines.size) { DhenType.memo() }
		}
		return lines
	}

	private companion object {
		const val CODE = '&'
		const val SECTION = '§'
		const val BREAK = "\\n"
	}
}

private const val MARGIN = 10
private const val MAX_LENGTH = 512
private const val DEFAULT_TEXT = "&aYour Text Here\\n&bYour new line here"
