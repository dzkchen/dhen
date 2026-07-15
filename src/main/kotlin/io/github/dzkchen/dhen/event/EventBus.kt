package io.github.dzkchen.dhen.event

import java.util.concurrent.ConcurrentHashMap

class EventBus(
	nanoClock: NanoClock = NanoClock.SYSTEM
) {
	private val lock = Any()
	private val types = ConcurrentHashMap<Class<out Event>, EventType<out Event>>()
	private var nextOrder = 0L
	val profiler = EventProfiler(nanoClock)

	inline fun <reified T : Event> type(): EventType<T> =
		type(T::class.java)

	fun <T : Event> type(type: Class<T>): EventType<T> {
		@Suppress("UNCHECKED_CAST")
		val existing = types[type] as EventType<T>?
		if (existing != null) return existing

		return synchronized(lock) {
			@Suppress("UNCHECKED_CAST")
			val lockedExisting = types[type] as EventType<T>?
			if (lockedExisting != null) lockedExisting else {
				val created = EventType<T>(this, DeepProfiledEvent::class.java.isAssignableFrom(type))
				types[type] = created
				created
			}
		}
	}

	inline fun <reified T : Event> subscribe(priority: Int = 0, noinline handler: (T) -> Unit): Handle =
		type<T>().subscribe(priority, handler)

	fun <T : Event> subscribe(type: EventType<T>, priority: Int = 0, handler: (T) -> Unit): Handle =
		type.subscribe(priority, handler)

	class EventType<T : Event> internal constructor(
		private val bus: EventBus,
		private val deepOnly: Boolean
	) {
		@Volatile
		private var listeners = noListeners

		fun subscribe(priority: Int = 0, handler: (T) -> Unit): Handle =
			subscribe(priority, null, null, handler)

		internal fun subscribeProfiled(
			priority: Int,
			timing: HandlerTiming,
			active: HandlerGate,
			handler: (T) -> Unit
		): Handle = subscribe(priority, timing, active, handler)

		private fun subscribe(
			priority: Int,
			timing: HandlerTiming?,
			active: HandlerGate?,
			handler: (T) -> Unit
		): Handle {
			val listener = synchronized(bus.lock) {
				@Suppress("UNCHECKED_CAST")
				val listener = Listener(priority, bus.nextOrder++, timing, active, handler as (Event) -> Unit)
				val current = listeners
				val next = Array(current.size + 1) { index ->
					if (index < current.size) current[index] else listener
				}
				next.sortWith(listenerOrder)
				listeners = next
				listener
			}

			return Handle { unsubscribe(listener) }
		}

		fun dispatch(event: T) {
			val listeners = listeners
			if (listeners.size == 0) return

			if (event is Cancellable) {
				dispatchCancellable(event, listeners)
				return
			}

			var index = 0
			while (index < listeners.size) {
				val listener = listeners[index]
				if (listener.subscribed && (listener.active?.isActive() != false)) dispatch(listener, event)
				index++
			}
		}

		private fun dispatchCancellable(event: Event, listeners: Array<Listener>) {
			val cancellable = event as Cancellable
			var index = 0
			while (index < listeners.size) {
				if (cancellable.cancelled) return
				val listener = listeners[index]
				if (listener.subscribed && (listener.active?.isActive() != false)) dispatch(listener, event)
				index++
			}
		}

		private fun dispatch(listener: Listener, event: Event) {
			val timing = listener.timing
			if (timing == null || (deepOnly && !bus.profiler.deepMode)) {
				listener.handler(event)
				return
			}

			val started = bus.profiler.clock.nanoTime()
			try {
				listener.handler(event)
			} finally {
				timing.record(bus.profiler.clock.nanoTime() - started)
			}
		}

		private fun unsubscribe(listener: Listener) {
			synchronized(bus.lock) {
				if (!listener.subscribed) return
				listener.subscribed = false

				val current = listeners
				var removedIndex = 0
				while (current[removedIndex] !== listener) removedIndex++
				val next = Array(current.size - 1) { index ->
					current[if (index < removedIndex) index else index + 1]
				}
				listeners = next
			}
		}
	}

	private class Listener(
		val priority: Int,
		val order: Long,
		val timing: HandlerTiming?,
		val active: HandlerGate?,
		val handler: (Event) -> Unit
	) {
		@Volatile
		var subscribed: Boolean = true
	}

	private companion object {
		private val noListeners = emptyArray<Listener>()
		private val listenerOrder = Comparator<Listener> { left, right ->
			val priority = right.priority.compareTo(left.priority)
			if (priority != 0) priority else left.order.compareTo(right.order)
		}
	}
}
