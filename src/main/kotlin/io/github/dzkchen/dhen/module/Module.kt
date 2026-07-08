package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus

abstract class Module(
	val name: String,
	val category: Category,
	val description: String
) {
	@Volatile
	var enabled: Boolean = false
		private set

	private val registrations = mutableListOf<Registration<out Event>>()
	private var bound = false

	protected inline fun <reified T : Event> on(priority: Int = 0, noinline handler: (T) -> Unit) {
		register(T::class.java, priority, handler)
	}

	internal fun setEnabled(enabled: Boolean) {
		this.enabled = enabled
	}

	internal fun toggle() {
		setEnabled(!enabled)
	}

	internal fun bind(eventBus: EventBus) {
		requireUnbound()
		bound = true
		for (registration in registrations) registration.bind(eventBus, this)
	}

	internal fun requireUnbound() {
		require(!bound) { "Module '$name' is already bound to an event bus." }
	}

	@PublishedApi
	internal fun <T : Event> register(type: Class<T>, priority: Int, handler: (T) -> Unit) {
		require(!bound) { "Module '$name' event handlers must be registered before manager registration." }

		val registration = Registration(type, priority, handler)
		registrations += registration
	}

	private class Registration<T : Event>(
		private val type: Class<T>,
		private val priority: Int,
		private val handler: (T) -> Unit
	) {
		fun bind(eventBus: EventBus, module: Module) {
			eventBus.type(type).subscribe(priority) { event ->
				if (module.enabled) handler(event)
			}
		}
	}
}
