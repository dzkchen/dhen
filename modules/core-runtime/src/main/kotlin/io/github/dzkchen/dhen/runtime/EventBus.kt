package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.DhenEvent
import kotlin.reflect.KClass

class EventBus(private val logger: AddonLogger) {
	private val handlers = LinkedHashMap<KClass<out DhenEvent>, LinkedHashMap<Long, (DhenEvent) -> Unit>>()
	private var nextId = 0L

	@Suppress("UNCHECKED_CAST")
	fun <T : DhenEvent> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription {
		val id = nextId++
		handlers.getOrPut(type) { LinkedHashMap() }[id] = handler as (DhenEvent) -> Unit
		return Subscription(type, id)
	}

	fun dispatch(event: DhenEvent) {
		val byId = handlers[event::class] ?: return
		for (handler in byId.values.toList()) {
			try {
				handler(event)
			} catch (t: Throwable) {
				logger.error("Event handler failed for ${event::class.simpleName}", t)
			}
		}
	}

	inner class Subscription(private val type: KClass<out DhenEvent>, private val id: Long) {
		fun cancel() {
			val byId = handlers[type] ?: return
			byId.remove(id)
			if (byId.isEmpty()) handlers.remove(type)
		}
	}
}
