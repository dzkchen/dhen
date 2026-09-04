package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.network.chat.Component

private const val REPEAT_OPEN = " (×"
private const val REPEAT_CLOSE = ')'
private const val SHORTEST_SEPARATOR = 5
private const val SHORTEST_CENTERED_SEPARATOR = 10

internal fun stripRepeatSuffix(text: String): String {
	val open = text.lastIndexOf(REPEAT_OPEN)
	if (open <= 0 || text.lastOrNull() != REPEAT_CLOSE) return text
	for (index in open + REPEAT_OPEN.length until text.length - 1) {
		if (!text[index].isDigit()) return text
	}
	return text.substring(0, open)
}

internal fun plainChatText(content: Component): String = stripRepeatSuffix(withoutCodes(content.string)).trim()

internal fun isSeparatorLine(trimmed: String): Boolean {
	val bare = stripRepeatSuffix(trimmed)
	return isRuled(bare) || isCenteredRule(bare)
}

private fun isRuled(trimmed: String): Boolean {
	if (trimmed.length < SHORTEST_SEPARATOR) return false
	for (character in trimmed) if (!isRuleCharacter(character)) return false
	return true
}

private fun isCenteredRule(trimmed: String): Boolean {
	if (trimmed.length < SHORTEST_CENTERED_SEPARATOR) return false
	if (!isRuleCharacter(trimmed.first()) || !isRuleCharacter(trimmed.last())) return false
	for (character in trimmed) {
		if (!isRuleCharacter(character) && character != ' ') return true
	}
	return false
}

private fun isRuleCharacter(character: Char): Boolean =
	character == '-' || character == '—' || character == '=' || character == '▬'
