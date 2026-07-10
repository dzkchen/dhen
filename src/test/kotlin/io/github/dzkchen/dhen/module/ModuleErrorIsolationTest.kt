package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleErrorIsolationTest {
	@Test
	fun `throwing handler does not stop other subscribers`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, clock = { 0L })
		val throwing = ThrowingModule()
		val counting = CountingModule()
		manager.registerAll(throwing, counting)
		manager.enable(throwing)
		manager.enable(counting)

		assertDoesNotThrow { events.dispatch(TestEvent()) }

		assertEquals(1, counting.calls)
		assertEquals(1, throwing.errorCount)
	}

	@Test
	fun `chat warn fires once per enable not per error and names the module`() {
		val notices = mutableListOf<Pair<Module, String>>()
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, { module, message -> notices += module to message }, { 0L })
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		repeat(3) { events.dispatch(TestEvent()) }

		assertTrue(module.enabled)
		assertEquals(3, module.errorCount)
		assertEquals(1, notices.size)
		assertSame(module, notices.single().first)
		assertTrue(notices.single().second.contains(module.name))
	}

	@Test
	fun `auto-disable triggers at threshold and resets on re-enable`() {
		val notices = mutableListOf<String>()
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, { _, message -> notices += message }, { 0L })
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) { events.dispatch(TestEvent()) }

		assertFalse(module.enabled)
		assertEquals(Module.ERROR_THRESHOLD, module.errorCount)
		assertEquals(2, notices.size)
		assertFalse(notices[0].contains("auto-disabled"))
		assertTrue(notices[1].contains("auto-disabled"))

		events.dispatch(TestEvent())
		assertEquals(Module.ERROR_THRESHOLD, module.errorCount)

		manager.enable(module)
		assertTrue(module.enabled)
		assertEquals(0, module.errorCount)

		notices.clear()
		repeat(Module.ERROR_THRESHOLD) { events.dispatch(TestEvent()) }

		assertFalse(module.enabled)
		assertEquals(Module.ERROR_THRESHOLD, module.errorCount)
		assertEquals(2, notices.size)
	}

	@Test
	fun `throwing notifier does not escape the bus`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, { _, _ -> throw IllegalStateException("chat down") }, { 0L })
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) {
			assertDoesNotThrow { events.dispatch(TestEvent()) }
		}

		assertFalse(module.enabled)
	}

	@Test
	fun `rolling window expiry resets error count`() {
		var now = 0L
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, clock = { now })
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD - 1) { events.dispatch(TestEvent()) }
		assertTrue(module.enabled)
		assertEquals(Module.ERROR_THRESHOLD - 1, module.errorCount)

		now += Module.ERROR_WINDOW_MS + 1
		events.dispatch(TestEvent())

		assertTrue(module.enabled)
		assertEquals(1, module.errorCount)
	}

	private class TestEvent : Event

	private class ThrowingModule(
		name: String = "Throwing Module"
	) : Module(
		name = name,
		category = Category.DEV,
		description = "Throws on every event."
	) {
		init {
			on<TestEvent> { throw RuntimeException("boom") }
		}
	}

	private class CountingModule(
		name: String = "Counting Module"
	) : Module(
		name = name,
		category = Category.QOL,
		description = "Counts test events."
	) {
		var calls = 0
			private set

		init {
			on<TestEvent> {
				calls++
			}
		}
	}
}
