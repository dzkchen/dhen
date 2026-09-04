package io.github.dzkchen.dhen.command

interface TextReplacerCommands {
	fun add(find: String, replacement: String): String

	fun remove(find: String): String

	fun list(): List<String>

	fun finds(): List<String>

	fun name(name: String, replacement: String): String

	fun clearNames(): String

	companion object {
		const val UNAVAILABLE = "Text replacement is not available."

		val NONE: TextReplacerCommands = object : TextReplacerCommands {
			override fun add(find: String, replacement: String): String = UNAVAILABLE

			override fun remove(find: String): String = UNAVAILABLE

			override fun list(): List<String> = listOf(UNAVAILABLE)

			override fun finds(): List<String> = emptyList()

			override fun name(name: String, replacement: String): String = UNAVAILABLE

			override fun clearNames(): String = UNAVAILABLE
		}
	}
}
