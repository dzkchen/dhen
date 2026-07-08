package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleManagerTest {
	@Test
	fun `disabled module receives no events`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus)
		val module = CountingModule()
		manager.register(module)

		events.dispatch(TestEvent())

		assertFalse(module.enabled)
		assertEquals(0, module.calls)
	}

	@Test
	fun `enable disable cycles do not add or lose subscriptions`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus)
		val module = CountingModule()
		manager.register(module)

		repeat(25) {
			manager.enable(module)
			manager.disable(module)
		}

		events.dispatch(TestEvent())
		assertEquals(0, module.calls)

		manager.enable(module)
		events.dispatch(TestEvent())
		events.dispatch(TestEvent())

		assertTrue(module.enabled)
		assertEquals(2, module.calls)
	}

	@Test
	fun `duplicate module name is rejected`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus)
		val module = CountingModule(name = "Sample")
		val duplicate = CountingModule(name = "sample")

		assertSame(module, manager.register(module))

		assertThrows(IllegalArgumentException::class.java) {
			manager.register(duplicate)
		}

		duplicate.setEnabled(true)
		events.dispatch(TestEvent())

		assertEquals(0, duplicate.calls)
	}

	@Test
	fun `registerAll rejects duplicate batch without binding any modules`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus)
		val module = CountingModule(name = "Batch Module")
		val duplicate = CountingModule(name = "batch module")

		assertThrows(IllegalArgumentException::class.java) {
			manager.registerAll(module, duplicate)
		}

		module.setEnabled(true)
		duplicate.setEnabled(true)
		events.dispatch(TestEvent())

		assertNull(manager["Batch Module"])
		assertEquals(0, module.calls)
		assertEquals(0, duplicate.calls)
	}

	@Test
	fun `registerAll failure does not bind earlier modules`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val firstManager = ModuleManager(EventBus())
		val secondManager = ModuleManager(bus)
		val alreadyBound = CountingModule(name = "Already Bound")
		val candidate = CountingModule(name = "Candidate")
		firstManager.register(alreadyBound)

		assertThrows(IllegalArgumentException::class.java) {
			secondManager.registerAll(candidate, alreadyBound)
		}

		candidate.setEnabled(true)
		events.dispatch(TestEvent())

		assertNull(secondManager["Candidate"])
		assertEquals(0, candidate.calls)
	}

	@Test
	fun `re-registering the same module does not remove its original subscriptions`() {
		val bus = EventBus()
		val events = bus.type<TestEvent>()
		val manager = ModuleManager(bus)
		val module = CountingModule()

		manager.register(module)
		manager.enable(module)

		assertThrows(IllegalArgumentException::class.java) {
			manager.register(module)
		}

		events.dispatch(TestEvent())

		assertEquals(1, module.calls)
	}

	@Test
	fun `modules cannot add event handlers after manager registration`() {
		val manager = ModuleManager(EventBus())
		val module = LateRegistrationModule()
		manager.register(module)

		assertThrows(IllegalArgumentException::class.java) {
			module.registerLate()
		}
	}

	@Test
	fun `module collections are exposed as snapshots`() {
		val manager = ModuleManager(EventBus())
		val first = CountingModule(name = "First")
		val second = CountingModule(name = "Second")
		manager.registerAll(first, second)
		val modulesSnapshot = manager.modules
		val categoriesSnapshot = manager.categories
		val categoryModulesSnapshot = manager.categories.getValue(Category.QOL)

		clearIfMutable(modulesSnapshot)
		clearIfMutable(categoriesSnapshot)
		clearIfMutable(categoryModulesSnapshot)

		assertSame(first, manager["First"])
		assertSame(second, manager["Second"])
		assertEquals(listOf(first, second), manager.modules.toList())
		assertEquals(listOf(first, second), manager.categories.getValue(Category.QOL))
	}

	private fun clearIfMutable(collection: Collection<Module>) {
		try {
			@Suppress("UNCHECKED_CAST")
			(collection as MutableCollection<Module>).clear()
		} catch (_: ClassCastException) {
		} catch (_: UnsupportedOperationException) {
		}
	}

	private fun clearIfMutable(map: Map<Category, List<Module>>) {
		try {
			@Suppress("UNCHECKED_CAST")
			(map as MutableMap<Category, List<Module>>).clear()
		} catch (_: ClassCastException) {
		} catch (_: UnsupportedOperationException) {
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

	private class TestEvent : Event

	private class LateRegistrationModule : Module(
		name = "Late Registration",
		category = Category.DEV,
		description = "Exposes late test registration."
	) {
		fun registerLate() {
			on<TestEvent> {}
		}
	}
}
