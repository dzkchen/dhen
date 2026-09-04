package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatTweaksTest {
	@Test
	fun `the module lands in Chat with the ported controls and one hidden store`() {
		assertEquals(Category.CHAT, ChatTweaks.category)
		assertEquals(
			listOf(
				"Ctrl Click to Copy",
				"Copy Mode",
				"Mouse Button",
				"Copy Notification",
				"Remove Useless Messages",
				"Auto Dialogue",
				"Hide Implosion Messages",
				"Admin Output",
				"Hide Signing Warning",
				"Hide Message Indicators",
				"Long Commands",
				"Right Click Menu",
				"Right Click Copies",
				"Command Tooltip",
				"Hidden Lines"
			),
			ChatTweaks.settings.map { it.name }
		)
		assertFalse(ChatTweaks.settings.last().isVisible)
	}

	@Test
	fun `the bottom chat line is index zero and the cursor above it walks upward`() {
		assertEquals(0, hovered(mouseY = 176.0))
		assertEquals(1, hovered(mouseY = 167.0))
		assertEquals(2, hovered(mouseY = 158.0))
	}

	@Test
	fun `a scrolled chat resolves to the line the scrollbar has pushed under the cursor`() {
		assertEquals(7, hovered(mouseY = 176.0, scrollbarPos = 7))
	}

	@Test
	fun `chat scale and line spacing both move the line the cursor lands on`() {
		assertEquals(1, hovered(mouseY = 158.0, scale = 2.0))
		assertEquals(1, hovered(mouseY = 160.0, lineHeight = 13))
	}

	@Test
	fun `a wrapped entry spans every line up to and including its end marker`() {
		val endOfEntry = booleanArrayOf(true, false, false, true, false)

		assertEquals(0..2, chatEntrySpan(0, endOfEntry.size) { endOfEntry[it] })
		assertEquals(0..2, chatEntrySpan(1, endOfEntry.size) { endOfEntry[it] })
		assertEquals(0..2, chatEntrySpan(2, endOfEntry.size) { endOfEntry[it] })
		assertEquals(3..4, chatEntrySpan(3, endOfEntry.size) { endOfEntry[it] })
		assertEquals(3..4, chatEntrySpan(4, endOfEntry.size) { endOfEntry[it] })
	}

	@Test
	fun `line spacing widens a chat row the way vanilla does`() {
		assertEquals(9, chatLineHeight(0.0))
		assertEquals(13, chatLineHeight(0.5))
		assertEquals(18, chatLineHeight(1.0))
	}

	@Test
	fun `repeated blank lines collapse to the first one`() {
		val hider = ChatHider { "" }

		assertFalse(hider.hides(""))
		assertTrue(hider.hides(""))
		assertTrue(hider.hides("   "))
		assertFalse(hider.hides("Something happened."))
		assertFalse(hider.hides(""))
	}

	@Test
	fun `a hidden line does not free the next blank line to show`() {
		val hider = ChatHider { "^Noise$" }

		assertFalse(hider.hides(""))
		assertTrue(hider.hides("Noise"))
		assertTrue(hider.hides(""))
	}

	@Test
	fun `custom patterns match the whole stripped line and nothing less`() {
		val hider = ChatHider { "^You are sending commands too fast!$" }

		assertTrue(hider.hides("You are sending commands too fast!"))
		assertFalse(hider.hides("Warning: You are sending commands too fast! Slow down."))
	}

	@Test
	fun `a stored list survives a round trip and drops an unparsable rule`() {
		var stored = listOf("^one$", "^two$").joinToString(ROW_SEPARATOR)
		val hider = ChatHider { stored }

		assertEquals(listOf("^one$", "^two$"), hider.patterns())

		stored = listOf("^one$", "*broken", "^three$").joinToString(ROW_SEPARATOR)

		assertEquals(listOf("^one$", "^three$"), hider.patterns())
	}

	@Test
	fun `an invalid regex is rejected before it can be stored`() {
		assertTrue(validPattern("^ok$"))
		assertFalse(validPattern("*broken"))
	}

	@Test
	fun `the explosive shot line is summarised per enemy and then hidden`() {
		assertEquals(
			"Explosive shot did 2.5k damage per enemy.",
			explosiveShotSummary("Your Explosive Shot hit 4 enemies for 10,000 damage.")
		)
		assertEquals(
			"Explosive shot did 750 damage per enemy.",
			explosiveShotSummary("Your Explosive Shot hit 1 enemy for 750 damage.")
		)
		assertTrue(ChatHider { "" }.hides("Your Explosive Shot hit 4 enemies for 10,000 damage."))
	}

	@Test
	fun `the chat length limit lifts for commands and comes back for ordinary messages`() {
		val manager = ModuleManager()
		manager.register(ChatTweaks)
		try {
			manager.enable(ChatTweaks)

			assertEquals(Int.MAX_VALUE, ChatTweaks.chatInputLimit(""))
			assertEquals(Int.MAX_VALUE, ChatTweaks.chatInputLimit("/party invite Someone"))
			assertEquals(256, ChatTweaks.chatInputLimit("hello there"))
			assertEquals(256, ChatTweaks.chatInputLimit(" /party invite Someone"))
			assertEquals("/party invite Someone", ChatTweaks.untrimmedCommand("/party  invite   Someone "))
			assertNull(ChatTweaks.untrimmedCommand("hello there"))
		} finally {
			manager.unregister(ChatTweaks)
		}
	}

	@Test
	fun `the admin filter reads the translation key and the command block sender argument`() {
		val fromCommandBlock = Component.translatable("chat.type.admin", "@", "say hi")
		val fromOperator = Component.translatable("chat.type.admin", "Someone", "say hi")
		val literal = Component.literal("[Someone: say hi]")
		try {
			adminOutput("Shown")

			assertFalse(ChatTweaks.hidesAdminOutput(fromCommandBlock))

			adminOutput("Only Players")

			assertTrue(ChatTweaks.hidesAdminOutput(fromCommandBlock))
			assertFalse(ChatTweaks.hidesAdminOutput(fromOperator))

			adminOutput("Hidden")

			assertTrue(ChatTweaks.hidesAdminOutput(fromOperator))
			assertFalse(ChatTweaks.hidesAdminOutput(literal))
		} finally {
			adminOutput("Shown")
		}
	}

	private fun adminOutput(mode: String) {
		(ChatTweaks.settings.first { it.name == "Admin Output" } as SelectorSetting).value = mode
	}

	private fun hovered(
		mouseX: Double = 20.0,
		mouseY: Double = 176.0,
		screenHeight: Int = 220,
		scale: Double = 1.0,
		lineHeight: Int = 9,
		chatWidth: Int = 320,
		linesPerPage: Int = 10,
		scrollbarPos: Int = 0,
		lineCount: Int = 20
	): Int = hoveredChatLine(
		mouseX,
		mouseY,
		screenHeight,
		scale,
		lineHeight,
		chatWidth,
		linesPerPage,
		scrollbarPos,
		lineCount
	)
}
