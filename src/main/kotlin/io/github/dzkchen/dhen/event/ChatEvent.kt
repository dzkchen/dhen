package io.github.dzkchen.dhen.event

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.StringUtil
import java.util.Optional

sealed class TextEvent : Event, Cancellable {
	override var cancelled: Boolean = false

	var text: Component = Component.empty()
		set(value) {
			field = value
			styledText = null
			strippedText = null
		}

	private var styledText: String? = null
	private var strippedText: String? = null

	val styled: String
		get() = styledText ?: legacyCodes(text).also { styledText = it }

	val stripped: String
		get() = strippedText ?: withoutCodes(text.string).also { strippedText = it }
}

class ChatReceiveEvent internal constructor() : TextEvent()

class ActionBarEvent internal constructor() : TextEvent()

private val LEGACY_COLORS: Map<TextColor, ChatFormatting> = ChatFormatting.entries
	.mapNotNull { format -> TextColor.fromLegacyFormat(format)?.let { color -> color to format } }
	.toMap()

private fun legacyCodes(text: Component): String {
	val codes = StringBuilder()
	var carried = false
	text.visit({ style, literal ->
		val color = style.color?.let(LEGACY_COLORS::get)
		if (color != null) codes.append(color) else if (carried) codes.append(ChatFormatting.RESET)
		val beforeFormats = codes.length
		if (style.isBold) codes.append(ChatFormatting.BOLD)
		if (style.isItalic) codes.append(ChatFormatting.ITALIC)
		if (style.isUnderlined) codes.append(ChatFormatting.UNDERLINE)
		if (style.isStrikethrough) codes.append(ChatFormatting.STRIKETHROUGH)
		if (style.isObfuscated) codes.append(ChatFormatting.OBFUSCATED)
		carried = color != null || codes.length > beforeFormats
		codes.append(literal)
		Optional.empty<Unit>()
	}, Style.EMPTY)
	return codes.toString()
}

private fun withoutCodes(text: String): String =
	if (text.indexOf(ChatFormatting.PREFIX_CODE) < 0) text else StringUtil.stripColor(text)
