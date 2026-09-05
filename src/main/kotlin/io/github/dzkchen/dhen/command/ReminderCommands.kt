package io.github.dzkchen.dhen.command

import net.minecraft.network.chat.Component

interface ReminderCommands {
	fun create(
		amount: Int,
		unit: String,
		trigger: String,
		output: String,
		repeat: String?,
		label: String?,
		message: String
	): String

	fun addTodo(text: String): String

	fun complete(id: Int): String

	fun remove(id: Int): String

	fun removeAll(confirmed: Boolean): List<Component>

	fun rename(id: Int, name: String): String

	fun toggle(id: Int): String

	fun snooze(id: Int, amount: Int, unit: String): String

	fun list(): List<Component>

	fun ids(): List<String>

	fun openManager(): String

	fun exportTodos(): String

	fun importTodos(): String

	companion object {
		const val UNAVAILABLE = "Reminders are not available."

		val NONE: ReminderCommands = object : ReminderCommands {
			override fun create(
				amount: Int,
				unit: String,
				trigger: String,
				output: String,
				repeat: String?,
				label: String?,
				message: String
			): String = UNAVAILABLE

			override fun addTodo(text: String): String = UNAVAILABLE

			override fun complete(id: Int): String = UNAVAILABLE

			override fun remove(id: Int): String = UNAVAILABLE

			override fun removeAll(confirmed: Boolean): List<Component> = emptyList()

			override fun rename(id: Int, name: String): String = UNAVAILABLE

			override fun toggle(id: Int): String = UNAVAILABLE

			override fun snooze(id: Int, amount: Int, unit: String): String = UNAVAILABLE

			override fun list(): List<Component> = emptyList()

			override fun ids(): List<String> = emptyList()

			override fun openManager(): String = UNAVAILABLE

			override fun exportTodos(): String = UNAVAILABLE

			override fun importTodos(): String = UNAVAILABLE
		}
	}
}
