package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import org.slf4j.LoggerFactory

abstract class Module(
	val name: String,
	val category: Category,
	val description: String
) {
	@Volatile
	var enabled: Boolean = false
		private set

	@Volatile
	var errorCount: Int = 0
		private set

	private val registrations = mutableListOf<Registration<out Event>>()
	private var bound = false
	private var notifier: ModuleNotifier = ModuleNotifier.LogBacked
	private var clock: () -> Long = System::currentTimeMillis
	private var windowStart = 0L
	private var warned = false

	protected inline fun <reified T : Event> on(priority: Int = 0, noinline handler: (T) -> Unit) {
		register(T::class.java, priority, handler)
	}

	internal fun setEnabled(enabled: Boolean) {
		this.enabled = enabled
		if (enabled) resetErrorState()
	}

	internal fun toggle() {
		setEnabled(!enabled)
	}

	internal fun bind(eventBus: EventBus, notifier: ModuleNotifier, clock: () -> Long) {
		requireUnbound()
		bound = true
		this.notifier = notifier
		this.clock = clock
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

	private fun resetErrorState() {
		errorCount = 0
		warned = false
	}

	private fun onHandlerError(throwable: Throwable) {
		log.error("Module '{}' handler threw", name, throwable)

		val now = clock()
		if (errorCount == 0 || now - windowStart > ERROR_WINDOW_MS) {
			windowStart = now
			errorCount = 0
		}
		errorCount++

		if (!warned) {
			warned = true
			notifyQuietly("Module '$name' encountered an error.")
		}

		if (errorCount >= ERROR_THRESHOLD) {
			setEnabled(false)
			notifyQuietly("Module '$name' auto-disabled after repeated errors.")
		}
	}

	private fun notifyQuietly(message: String) {
		try {
			notifier.notify(this, message)
		} catch (throwable: Throwable) {
			log.error("Module '{}' notifier threw", name, throwable)
		}
	}

	private class Registration<T : Event>(
		private val type: Class<T>,
		private val priority: Int,
		private val handler: (T) -> Unit
	) {
		fun bind(eventBus: EventBus, module: Module) {
			eventBus.type(type).subscribe(priority) { event ->
				if (module.enabled) {
					try {
						handler(event)
					} catch (throwable: Throwable) {
						module.onHandlerError(throwable)
					}
				}
			}
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(Module::class.java)
		internal const val ERROR_THRESHOLD = 5
		internal const val ERROR_WINDOW_MS = 10_000L
	}
}
