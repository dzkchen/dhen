package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
		val notices = mutableListOf<Triple<Module, String, String?>>()
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(
			bus,
			{ module, message, copyText -> notices += Triple(module, message, copyText) },
			{ 0L }
		)
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		repeat(3) { events.dispatch(TestEvent()) }

		assertTrue(module.enabled)
		assertEquals(3, module.errorCount)
		assertEquals(1, notices.size)
		assertSame(module, notices.single().first)
		assertTrue(notices.single().second.contains(module.name))
		assertNotNull(module.lastErrorTrace)
		assertTrue(module.lastErrorTrace!!.contains("RuntimeException: boom"))
		assertSame(module.lastErrorTrace, notices.single().third)
	}

	@Test
	fun `auto-disable triggers at threshold and resets on re-enable`() {
		val notices = mutableListOf<String>()
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, { _, message, _ -> notices += message }, { 0L })
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
		assertNull(module.lastErrorTrace)

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
		val manager = ModuleManager(bus, { _, _, _ -> throw IllegalStateException("chat down") }, { 0L })
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

	@Test
	fun `concurrent error reports lose no count and warn exactly once`() {
		val notices = AtomicInteger()
		val bus = EventBus()
		val manager = ModuleManager(bus, { _, _, _ -> notices.incrementAndGet() }, { 0L })
		val module = ThrowingModule()
		manager.register(module)
		manager.enable(module)

		val ready = CountDownLatch(THREADS)
		val start = CountDownLatch(1)
		val done = CountDownLatch(THREADS)
		val pool = Executors.newFixedThreadPool(THREADS)
		try {
			repeat(THREADS) {
				pool.execute {
					ready.countDown()
					start.await()
					repeat(ERRORS_PER_THREAD) { module.reportError(QuietFailure()) }
					done.countDown()
				}
			}
			ready.await()
			start.countDown()
			assertTrue(done.await(30, TimeUnit.SECONDS))
		} finally {
			pool.shutdownNow()
		}

		assertEquals(THREADS * ERRORS_PER_THREAD, module.errorCount)
		assertEquals(2, notices.get())
	}

	@Test
	fun `auto-disable reaches the state listener`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, clock = { 0L })
		val module = ThrowingModule()
		manager.register(module)
		val disables = mutableListOf<Module>()
		manager.stateListener = { changed -> if (!changed.enabled) disables += changed }
		manager.enable(module)

		repeat(Module.ERROR_THRESHOLD) { events.dispatch(TestEvent()) }

		assertFalse(module.enabled)
		assertEquals(listOf<Module>(module), disables)
	}

	@Test
	fun `a throwing state listener does not escape the bus`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus, clock = { 0L })
		val module = ThrowingModule()
		manager.register(module)
		manager.stateListener = { throw IllegalStateException("disk full") }

		assertDoesNotThrow { manager.enable(module) }
		assertTrue(module.enabled)

		repeat(Module.ERROR_THRESHOLD) {
			assertDoesNotThrow { events.dispatch(TestEvent()) }
		}

		assertFalse(module.enabled)
	}

	private class TestEvent : Event

	private class QuietFailure : RuntimeException("boom", null, false, false)

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

	private companion object {
		const val THREADS = 8
		const val ERRORS_PER_THREAD = 250
	}
}
