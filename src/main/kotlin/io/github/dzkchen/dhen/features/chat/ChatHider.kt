package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.util.shortNumber

internal const val HIDER_SEPARATOR = "\n"

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

internal class ChatHider(private val stored: () -> String) {
	private var source: String? = null
	private val custom = mutableListOf<Regex>()
	private var lastBlank = false

	fun patterns(): List<String> = compiled().map { it.pattern }

	fun hides(stripped: String): Boolean {
		if (stripped.isBlank()) {
			if (lastBlank) return true
			lastBlank = true
			return false
		}
		if (USELESS.any { it.matches(stripped) } || compiled().any { it.matches(stripped) }) return true
		lastBlank = false
		return false
	}

	fun forget() {
		lastBlank = false
	}

	private fun compiled(): List<Regex> {
		val current = stored()
		if (current === source) return custom
		source = current
		custom.clear()
		for (pattern in current.split(HIDER_SEPARATOR)) {
			if (pattern.isEmpty()) continue
			runCatching { Regex(pattern) }.getOrNull()?.let { custom += it }
		}
		return custom
	}
}
