package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.RowBook
import io.github.dzkchen.dhen.util.shortNumber

internal val EXPLOSIVE_SHOT = Regex("""^Your Explosive Shot hit (\d+) (?:enemy|enemies) for ([\d,.]+) damage\.$""")

private val USELESS = listOf(EXPLOSIVE_SHOT)

internal fun explosiveShotSummary(stripped: String): String? {
	val match = EXPLOSIVE_SHOT.matchEntire(stripped) ?: return null
	val hits = match.groupValues[1].toIntOrNull() ?: return null
	val damage = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
	val each = if (hits > 0) damage / hits else damage
	return "Explosive shot did ${shortNumber(each.toLong())} damage per enemy."
}

internal fun validPattern(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

internal class ChatHider(stored: () -> String) {
	private val book = RowBook<String, Regex>(stored) { pattern ->
		if (pattern.isEmpty()) null else runCatching { Regex(pattern) }.getOrNull()?.let { pattern to it }
	}
	private var lastBlank = false

	fun patterns(): List<String> = book.all().keys.toList()

	fun hides(stripped: String): Boolean {
		if (stripped.isBlank()) {
			if (lastBlank) return true
			lastBlank = true
			return false
		}
		if (USELESS.any { it.matches(stripped) } || book.all().values.any { it.matches(stripped) }) return true
		lastBlank = false
		return false
	}

	fun forget() {
		lastBlank = false
	}
}
