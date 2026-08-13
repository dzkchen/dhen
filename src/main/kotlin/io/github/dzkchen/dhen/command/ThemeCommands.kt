package io.github.dzkchen.dhen.command

interface ThemeCommands {
	fun names(): List<String> = emptyList()

	fun summary(): String = UNAVAILABLE

	fun select(name: String): String = UNAVAILABLE

	fun export(name: String?, notify: (String) -> Unit = {}): Unit = notify(UNAVAILABLE)

	fun reload(notify: (String) -> Unit = {}): Unit = notify(UNAVAILABLE)

	companion object {
		const val UNAVAILABLE = "Themes are not available."

		val NONE: ThemeCommands = object : ThemeCommands {}
	}
}
