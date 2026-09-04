package io.github.dzkchen.dhen.features.chat

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.tree.CommandNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandAliasesTest {
	@Test
	fun `an alias becomes the command it stands for`() {
		val book = book("x party warp")

		assertEquals("party warp", book.rewrite("x"))
	}

	@Test
	fun `whatever follows the alias is appended`() {
		val book = book("x party warp")

		assertEquals("party warp now please", book.rewrite("x now please"))
	}

	@Test
	fun `an alias is matched however it was typed`() {
		val book = book("x party warp")

		assertEquals("party warp", book.rewrite("X"))
	}

	@Test
	fun `the store keeps every alias in the order it was added`() {
		val entries = linkedMapOf("x" to "party warp", "s" to "showskill")

		assertEquals("x party warp\ns showskill", formatAliases(entries))
		assertEquals(entries, book(formatAliases(entries)).all())
	}

	@Test
	fun `a rewritten store is picked up without a restart`() {
		var stored = "x party warp"
		val book = AliasBook { stored }

		assertEquals("party warp", book.rewrite("x"))
		stored = "x party list"
		assertEquals("party list", book.rewrite("x"))
	}

	@Test
	fun `an alias nobody could type is refused`() {
		assertNotNull(aliasFault("", "party warp"))
		assertNotNull(aliasFault("two words", "party warp"))
		assertNotNull(aliasFault("/x", "party warp"))
		assertNotNull(aliasFault("dhen", "party warp"))
		assertNotNull(aliasFault("DH", "party warp"))
		assertNotNull(aliasFault("x", "   "))
		assertNull(aliasFault("x", "party warp"))
	}

	@Test
	fun `a broken stored line is skipped rather than read as an alias`() {
		val book = book("x party warp\nbroken\ny \n\nz p list")

		assertEquals(mapOf("x" to "party warp", "z" to "p list"), book.all())
	}

	@Test
	fun `an installed alias parses with everything after it as one argument`() {
		val dispatcher = CommandDispatcher<Any>()
		installAliases(dispatcher, listOf("x"))

		val parse = dispatcher.parse("x one two", Any())

		assertFalse(parse.reader.canRead())
		assertTrue(parse.context.nodes.size == 2)
	}

	@Test
	fun `unregistering a name clears it from all three node tables`() {
		val dispatcher = CommandDispatcher<Any>()
		dispatcher.register(literal<Any>("x").then(argument<Any, String>("message", StringArgumentType.word())))
		val node = FakeNodeAccess(dispatcher.root.getChild("x"))

		removeNode(node, "message")

		assertTrue(node.children.isEmpty())
		assertTrue(node.literals.isEmpty())
		assertTrue(node.arguments.isEmpty())
	}

	private class FakeNodeAccess(child: CommandNode<*>) : CommandNodeAccess {
		val children = linkedMapOf("message" to child)
		val literals = linkedMapOf("message" to child)
		val arguments = linkedMapOf("message" to child)

		override fun commandChildren(): MutableMap<String, CommandNode<*>> = children

		override fun commandLiterals(): MutableMap<String, CommandNode<*>> = literals

		override fun commandArguments(): MutableMap<String, CommandNode<*>> = arguments
	}

	private fun book(stored: String) = AliasBook { stored }
}
