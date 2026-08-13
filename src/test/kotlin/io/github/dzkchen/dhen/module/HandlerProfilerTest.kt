package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.DeepProfiledEvent
import io.github.dzkchen.dhen.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HandlerProfilerTest {
	@Test
	fun `normal module handlers are timed only while enabled`() {
		val times = ArrayDeque(listOf(100L, 160L))
		val manager = ModuleManager(nanoClock = { times.removeFirst() })
		val module = CountingModule()
		manager.register(module)
		val events = manager.eventBus.type<TestEvent>()

		events.dispatch(TestEvent())
		manager.enable(module)
		events.dispatch(TestEvent())
		manager.disable(module)
		events.dispatch(TestEvent())

		val timing = module.handlerTimings.single().snapshot()
		assertEquals(1, module.calls)
		assertEquals(1, timing.totalInvocations)
		assertEquals(1, timing.sampleCount)
		assertEquals(60, timing.averageNanos)
		assertEquals(60, timing.maxNanos)
		assertEquals(0, times.size)
	}

	@Test
	fun `deep events run normally but are timed only in deep mode`() {
		val times = ArrayDeque(listOf(20L, 95L))
		val manager = ModuleManager(nanoClock = { times.removeFirst() })
		val module = DeepModule()
		manager.register(module)
		manager.enable(module)
		val events = manager.eventBus.type<HotEvent>()

		events.dispatch(HotEvent())
		assertEquals(0, module.handlerTimings.single().snapshot().totalInvocations)

		manager.profiler.deepMode = true
		events.dispatch(HotEvent())

		val timing = module.handlerTimings.single().snapshot()
		assertEquals(2, module.calls)
		assertEquals(1, timing.totalInvocations)
		assertEquals(75, timing.averageNanos)
	}

	@Test
	fun `handler timing keeps a bounded rolling window`() {
		var invocation = 0L
		var starting = true
		val manager = ModuleManager(
			nanoClock = {
				if (starting) {
					starting = false
					0L
				} else {
					starting = true
					++invocation
				}
			}
		)
		val module = CountingModule()
		manager.register(module)
		manager.enable(module)

		repeat(130) { manager.eventBus.type<TestEvent>().dispatch(TestEvent()) }

		val timing = module.handlerTimings.single().snapshot()
		assertEquals(130, timing.totalInvocations)
		assertEquals(128, timing.sampleCount)
		assertEquals(66, timing.averageNanos)
		assertEquals(130, timing.maxNanos)
	}

	private class TestEvent : Event
	private class HotEvent : DeepProfiledEvent

	private class CountingModule : Module("Counting", Category.DEV, "Counts events.") {
		var calls = 0
			private set

		init {
			on<TestEvent> { calls++ }
		}
	}

	private class DeepModule : Module("Deep", Category.DEV, "Counts hot events.") {
		var calls = 0
			private set

		init {
			on<HotEvent> { calls++ }
		}
	}
}
