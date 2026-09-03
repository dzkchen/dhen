package io.github.dzkchen.dhen.command

interface WaypointCommands {
	fun sendPing(note: String): String

	fun save(name: String): String

	fun forget(name: String): String

	fun list(): List<String>

	fun names(): List<String>

	fun hide(x: Int, y: Int, z: Int): String

	fun redraw(x: Int, y: Int, z: Int, label: String): String

	companion object {
		const val UNAVAILABLE = "Waypoints are not available."

		val NONE: WaypointCommands = object : WaypointCommands {
			override fun sendPing(note: String): String = UNAVAILABLE

			override fun save(name: String): String = UNAVAILABLE

			override fun forget(name: String): String = UNAVAILABLE

			override fun list(): List<String> = listOf(UNAVAILABLE)

			override fun names(): List<String> = emptyList()

			override fun hide(x: Int, y: Int, z: Int): String = UNAVAILABLE

			override fun redraw(x: Int, y: Int, z: Int, label: String): String = UNAVAILABLE
		}
	}
}
