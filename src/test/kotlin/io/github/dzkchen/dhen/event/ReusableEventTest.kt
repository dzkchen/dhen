package io.github.dzkchen.dhen.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ReusableEventTest {
	private val events = ReusableEvent(::CountedEvent)

	@Test
	fun `back to back publishes hand out the same instance`() {
		val first = events.borrow()
		events.release(first)
		val second = events.borrow()
		events.release(second)

		assertSame(first, second)
	}

	@Test
	fun `a publish nested inside another gets its own instance`() {
		val outer = events.borrow()
		val inner = events.borrow()

		assertNotSame(outer, inner)
	}

	@Test
	fun `releasing a nested instance does not hand the shared one out twice`() {
		val outer = events.borrow()
		val inner = events.borrow()
		events.release(inner)

		assertNotSame(outer, events.borrow())
	}

	@Test
	fun `the shared instance comes back once the outermost publish is done`() {
		val outer = events.borrow()
		events.release(events.borrow())
		events.release(outer)

		assertSame(outer, events.borrow())
	}

	@Test
	fun `a spare is only built when one is needed`() {
		CountedEvent.built = 0
		val fresh = ReusableEvent(::CountedEvent)
		fresh.release(fresh.borrow())
		fresh.release(fresh.borrow())

		assertEquals(1, CountedEvent.built)
	}

	@Test
	fun `release resets shared and nested instances`() {
		val fresh = ReusableEvent(::ResettableEvent, ResettableEvent::reset)
		val outer = fresh.borrow()
		val inner = fresh.borrow()
		outer.value = 1
		inner.value = 2

		fresh.release(inner)
		fresh.release(outer)

		assertEquals(0, outer.value)
		assertEquals(0, inner.value)
	}
}

private class CountedEvent : Event {
	init {
		built++
	}

	companion object {
		var built = 0
	}
}

private class ResettableEvent : Event {
	var value = 0

	fun reset() {
		value = 0
	}
}
