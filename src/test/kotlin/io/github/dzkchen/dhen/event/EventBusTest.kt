package io.github.dzkchen.dhen.event

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventBusTest {
	@Test
	fun `priority order is honored`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val calls = mutableListOf<String>()

		bus.subscribe<TestEvent>(priority = 0) { calls += "middle" }
		bus.subscribe<TestEvent>(priority = 100) { calls += "high" }
		bus.subscribe<TestEvent>(priority = -100) { calls += "low" }
		bus.subscribe<TestEvent>(priority = 100) { calls += "second high" }

		events.dispatch(TestEvent())

		assertEquals(listOf("high", "second high", "middle", "low"), calls)
	}

	@Test
	fun `cancellation stops lower priority handlers`() {
		val bus = EventBus()
		val events = bus.type<TestCancellableEvent>()
		val calls = mutableListOf<String>()
		val event = TestCancellableEvent()

		bus.subscribe<TestCancellableEvent>(priority = 100) {
			calls += "high"
			it.cancel()
		}
		bus.subscribe<TestCancellableEvent>(priority = 0) { calls += "middle" }
		bus.subscribe<TestCancellableEvent>(priority = -100) { calls += "low" }

		events.dispatch(event)

		assertTrue(event.cancelled)
		assertEquals(listOf("high"), calls)
	}

	@Test
	fun `unsubscribe during dispatch is safe`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val calls = mutableListOf<String>()
		var lowerPriorityHandle: Handle? = null

		bus.subscribe<TestEvent>(priority = 100) {
			calls += "high"
			lowerPriorityHandle?.unsubscribe()
		}
		lowerPriorityHandle = bus.subscribe<TestEvent>(priority = 0) { calls += "middle" }
		bus.subscribe<TestEvent>(priority = -100) { calls += "low" }

		events.dispatch(TestEvent())
		events.dispatch(TestEvent())

		assertEquals(listOf("high", "low", "high", "low"), calls)
	}

	@Test
	fun `empty dispatch does nothing`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()

		assertDoesNotThrow { events.dispatch(TestEvent()) }
	}

	private class TestEvent : Event

	private class TestCancellableEvent : Event, Cancellable {
		override var cancelled: Boolean = false
	}
}
