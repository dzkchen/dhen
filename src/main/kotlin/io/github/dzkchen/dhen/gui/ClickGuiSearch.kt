package io.github.dzkchen.dhen.gui

internal object ClickGuiSearch {
	fun matches(query: String, name: String, description: String): Boolean =
		query.isEmpty() ||
			name.contains(query, ignoreCase = true) ||
			description.contains(query, ignoreCase = true)
}
