package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class CommandRegistryTest {
	private fun registry(): CommandRegistry<Any> =
		CommandRegistry(ModuleManager()) { _, message -> captured += message }

	private val captured = mutableListOf<String>()

	@Test
	fun `duplicate name is rejected`() {
		val registry = registry()
		registry.register("foo", owner = "test") {}

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("foo", owner = "other") {}
		}
	}

	@Test
	fun `alias colliding with an existing literal is rejected`() {
		val registry = registry()
		registry.register("alpha", "beta", owner = "test") {}

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("gamma", "beta", owner = "other") {}
		}
	}

	@Test
	fun `reserved core names are rejected`() {
		val registry = registry()

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("dhen", owner = "test") {}
		}
		assertThrows(IllegalArgumentException::class.java) {
			registry.register("cmd", "dh", owner = "test") {}
		}
	}

	@Test
	fun `handle unregisters the command`() {
		val registry = registry()
		val handle = registry.register("solo", owner = "test") {
			executes { Command.SINGLE_SUCCESS }
		}
		assertEquals(listOf("solo"), registry.commands.map { it.name })

		handle.unsubscribe()

		assertTrue(registry.commands.isEmpty())

		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)
		assertNull(dispatcher.root.getChild("solo"))
		assertNotNull(dispatcher.root.getChild("dhen"))
	}

	@Test
	fun `two argument constructor accepts positional feedback for compatibility`() {
		val feedback: (Any, String) -> Unit = { _, message -> captured += message }
		val registry = CommandRegistry(ModuleManager(), feedback)
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen", Any())

		assertTrue(captured.single().contains("Dhen commands"))
	}

	@Test
	fun `registered command installs and executes under all its literals`() {
		val registry = registry()
		var runs = 0
		registry.register("greet", "g", owner = "test") {
			executes { runs++; Command.SINGLE_SUCCESS }
		}
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		assertNotNull(dispatcher.root.getChild("greet"))
		assertNotNull(dispatcher.root.getChild("g"))
		dispatcher.execute("greet", Any())
		dispatcher.execute("g", Any())
		assertEquals(2, runs)
	}

	@Test
	fun `module suggestions filter by the typed prefix`() {
		val manager = ModuleManager()
		manager.register(TestModule())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		fun suggestions(input: String): List<String> =
			dispatcher.getCompletionSuggestions(dispatcher.parse(input, Any())).get().list.map { it.text }

		assertEquals(listOf("Test_Module"), suggestions("dhen module Te"))
		assertTrue(suggestions("dhen module Zz").isEmpty())
	}

	@Test
	fun `module toggle flips state and reports through both roots`() {
		val manager = ModuleManager()
		val module = manager.register(TestModule())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen module Test_Module toggle", Any())
		assertTrue(module.enabled)
		assertEquals("Toggled Test Module: enabled", captured.last())

		dispatcher.execute("dh module Test_Module toggle", Any())
		assertFalse(module.enabled)
		assertEquals("Toggled Test Module: disabled", captured.last())
	}

	@Test
	fun `unknown module name reports without throwing`() {
		val registry = registry()
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen module Nope toggle", Any())

		assertEquals("No module named 'Nope'.", captured.last())
	}

	@Test
	fun `debug reports live module counters and handler timing`() {
		val times = ArrayDeque(listOf(10L, 60L))
		val bus = EventBus { times.removeFirst() }
		val manager = ModuleManager(bus)
		val module = manager.register(DebugModule())
		manager.enable(module)
		bus.type<DebugEvent>().dispatch(DebugEvent())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug", Any())

		assertEquals("Dhen debug: deep profiling off", captured[0])
		assertEquals(
			"Debug Module: subscriptions=1, keybinds=1, errors=1, config=modules:v${ModulePersistence.version}",
			captured[1]
		)
		assertEquals("  DebugEvent: calls=1, rollingAvg=50ns, rollingMax=50ns, samples=1", captured[2])
	}

	@Test
	fun `debug command controls deep profiling explicitly`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug deep on", Any())
		assertTrue(manager.eventBus.profiler.deepMode)
		assertEquals("Deep profiling enabled.", captured.last())

		dispatcher.execute("dh debug deep off", Any())
		assertFalse(manager.eventBus.profiler.deepMode)
		assertEquals("Deep profiling disabled.", captured.last())
	}

	private class TestModule : Module(
		name = "Test Module",
		category = Category.DEV,
		description = "Toggle target for command tests."
	)

	private class DebugEvent : Event

	private class DebugModule : Module(
		name = "Debug Module",
		category = Category.DEV,
		description = "Debug fixture."
	) {
		@Suppress("unused")
		private val keybind by KeybindSetting("Action", GLFW.GLFW_KEY_K)

		init {
			on<DebugEvent> { error("count this failure") }
		}
	}
}
