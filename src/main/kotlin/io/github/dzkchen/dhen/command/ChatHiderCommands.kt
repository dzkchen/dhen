package io.github.dzkchen.dhen.command

interface ChatHiderCommands {
	fun add(pattern: String): String

	fun remove(pattern: String): String

	fun list(): List<String>

	fun patterns(): List<String>

	companion object {
		const val UNAVAILABLE = "The chat hider is not available."

		val NONE: ChatHiderCommands = object : ChatHiderCommands {
			override fun add(pattern: String): String = UNAVAILABLE

			override fun remove(pattern: String): String = UNAVAILABLE

			override fun list(): List<String> = listOf(UNAVAILABLE)

			override fun patterns(): List<String> = emptyList()
		}
	}
}
