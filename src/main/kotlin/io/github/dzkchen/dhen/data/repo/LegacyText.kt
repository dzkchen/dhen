package io.github.dzkchen.dhen.data.repo

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

private const val SECTION = '§'

private val UNSTYLED: Style = Style.EMPTY
	.withBold(false)
	.withItalic(false)
	.withUnderlined(false)
	.withStrikethrough(false)
	.withObfuscated(false)

internal fun legacyComponent(legacy: String): Component {
	var only: MutableComponent? = null
	var joined: MutableComponent? = null
	val pending = StringBuilder(legacy.length)
	var style = UNSTYLED
	var index = 0

	fun flush() {
		if (pending.isEmpty()) return
		val segment = Component.literal(pending.toString()).setStyle(style)
		pending.setLength(0)
		val started = only
		when {
			started == null -> only = segment
			joined == null -> joined = Component.empty().append(started).append(segment)
			else -> joined?.append(segment)
		}
	}

	while (index < legacy.length) {
		val character = legacy[index]
		if (character != SECTION || index + 1 >= legacy.length) {
			pending.append(character)
			index++
			continue
		}
		val code = ChatFormatting.getByCode(legacy[index + 1])
		if (code != null && pending.isNotEmpty()) {
			flush()
			style = UNSTYLED
		}
		style = when (code) {
			ChatFormatting.BOLD -> style.withBold(true)
			ChatFormatting.ITALIC -> style.withItalic(true)
			ChatFormatting.UNDERLINE -> style.withUnderlined(true)
			ChatFormatting.STRIKETHROUGH -> style.withStrikethrough(true)
			ChatFormatting.OBFUSCATED -> style.withObfuscated(true)
			else -> style.withColor(code)
		}
		index += 2
	}
	flush()
	return joined ?: only ?: Component.empty()
}
