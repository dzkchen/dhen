package io.github.dzkchen.dhen.command

interface HotkeyCommands {
	fun add(keys: String, command: String): String

	fun remove(target: String): String

	fun scope(target: String, context: String): String

	fun list(): List<String>

	fun targets(): List<String>

	companion object {
		const val UNAVAILABLE = "Hotkeys are not available."

		val NONE: HotkeyCommands = object : HotkeyCommands {
			override fun add(keys: String, command: String): String = UNAVAILABLE

			override fun remove(target: String): String = UNAVAILABLE

			override fun scope(target: String, context: String): String = UNAVAILABLE

			override fun list(): List<String> = listOf(UNAVAILABLE)

			override fun targets(): List<String> = emptyList()
		}
	}
}
