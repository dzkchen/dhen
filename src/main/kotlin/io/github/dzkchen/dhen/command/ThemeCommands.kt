package io.github.dzkchen.dhen.command

interface ThemeCommands {
	fun names(): List<String>

	fun summary(): String

	fun select(name: String): String

	fun export(name: String?, notify: (String) -> Unit = {})

	fun reload(notify: (String) -> Unit = {})

	companion object {
		const val UNAVAILABLE = "Themes are not available."

		val NONE: ThemeCommands = object : ThemeCommands {
			override fun names(): List<String> = emptyList()

			override fun summary(): String = UNAVAILABLE

			override fun select(name: String): String = UNAVAILABLE

			override fun export(name: String?, notify: (String) -> Unit) = notify(UNAVAILABLE)

			override fun reload(notify: (String) -> Unit) = notify(UNAVAILABLE)
		}
	}
}
