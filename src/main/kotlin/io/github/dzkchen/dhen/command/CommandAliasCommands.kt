package io.github.dzkchen.dhen.command

interface CommandAliasCommands {
	fun add(alias: String, replacement: String): String

	fun remove(alias: String): String

	fun list(): List<String>

	fun aliases(): List<String>

	companion object {
		const val UNAVAILABLE = "Command aliases are not available."

		val NONE: CommandAliasCommands = object : CommandAliasCommands {
			override fun add(alias: String, replacement: String): String = UNAVAILABLE

			override fun remove(alias: String): String = UNAVAILABLE

			override fun list(): List<String> = listOf(UNAVAILABLE)

			override fun aliases(): List<String> = emptyList()
		}
	}
}
