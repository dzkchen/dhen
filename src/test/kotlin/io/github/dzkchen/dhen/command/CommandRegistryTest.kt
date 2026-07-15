package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
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

	private class TestModule : Module(
		name = "Test Module",
		category = Category.DEV,
		description = "Toggle target for command tests."
	)
}
