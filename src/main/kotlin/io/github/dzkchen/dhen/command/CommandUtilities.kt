package io.github.dzkchen.dhen.command

interface CommandUtilities {
	fun sendCoordinates(message: String): String

	fun openLastStorage(): String

	fun wiki(search: String): String

	fun heldItemWiki(): String

	fun link(): String

	fun calculate(expression: String): String

	companion object {
		const val UNAVAILABLE = "The command helpers are not available."

		val NONE: CommandUtilities = object : CommandUtilities {
			override fun sendCoordinates(message: String): String = UNAVAILABLE

			override fun openLastStorage(): String = UNAVAILABLE

			override fun wiki(search: String): String = UNAVAILABLE

			override fun heldItemWiki(): String = UNAVAILABLE

			override fun link(): String = UNAVAILABLE

			override fun calculate(expression: String): String = UNAVAILABLE
		}
	}
}
