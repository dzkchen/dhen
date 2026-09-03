package io.github.dzkchen.dhen.features.chat

import java.util.Locale

internal class SuggestionBranch(words: List<String>, val offers: () -> Collection<String>) {
	val words: Set<String> = words.mapTo(LinkedHashSet()) { it.lowercase(Locale.ROOT) }
}

internal class SuggestionCommand(
	names: List<String>,
	val branches: List<SuggestionBranch> = emptyList(),
	val matchesAnywhere: Boolean = false,
	val offers: () -> Collection<String> = { emptyList() }
) {
	val names: List<String> = names.map { it.lowercase(Locale.ROOT) }
}

internal class SuggestionTree(commands: List<SuggestionCommand>) {
	private val byName = HashMap<String, SuggestionCommand>()

	init {
		for (command in commands) for (name in command.names) byName[name] = command
	}

	fun suggestions(typed: String): List<String> {
		val words = typed.split(' ')
		if (words.size < 2) return emptyList()
		val command = byName[words[0].lowercase(Locale.ROOT)] ?: return emptyList()
		val offered = offered(command, words)
		if (offered.isEmpty()) return emptyList()
		val typing = words.last()
		if (command.matchesAnywhere) return offered.filter { it.contains(typing, ignoreCase = true) }.distinct()
		return offered.filter { it.startsWith(typing, ignoreCase = true) }.distinct()
	}

	private fun offered(command: SuggestionCommand, words: List<String>): Collection<String> {
		if (words.size == 2) return command.branches.flatMap { it.words } + command.offers()
		val second = words[1].lowercase(Locale.ROOT)
		return command.branches.firstOrNull { second in it.words }?.offers().orEmpty()
	}
}
