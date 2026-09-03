package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe

internal object TabCompleteHooks : GuardedHooks<TabCompleteHooks.Channels> {
	override val feed = "Tab complete"

	override val failsafe = Failsafe("Dhen {} failed, its command suggestions are off until restart")

	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	override fun uninstall() {
		channels = null
	}

	override fun bound() = channels

	@JvmStatic
	fun suggestions(command: String): List<String>? =
		guarded("command suggestions", null) { it.complete(command) }

	internal class Channels(bus: EventBus) {
		private val completions = bus.type<TabCompletionEvent>()

		fun complete(command: String): List<String>? {
			val event = TabCompletionEvent(command.removePrefix("/"))
			completions.dispatch(event)
			return event.offers()
		}
	}
}
