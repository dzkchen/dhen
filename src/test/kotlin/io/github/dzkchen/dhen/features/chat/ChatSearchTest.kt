package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatSearchTest {
	private val manager = ModuleManager()

	@BeforeEach
	fun open() {
		manager.register(ChatSearch)
		manager.enable(ChatSearch)
		ChatSearch.show()
	}

	@AfterEach
	fun shut() {
		ChatSearch.close()
		manager.unregister(ChatSearch)
	}

	@Test
	fun `every word has to appear and the order between them does not matter`() {
		ChatSearch.ask("bob party")
		assertTrue(ChatSearch.matches(Component.literal("Party > Bob: hi")))
		assertTrue(ChatSearch.matches(Component.literal("Bob has left the party.")))
		assertFalse(ChatSearch.matches(Component.literal("Party > Ann: hi")))
	}

	@Test
	fun `an empty query hides nothing`() {
		ChatSearch.ask("   ")
		assertTrue(ChatSearch.matches(Component.literal("anything at all")))
	}

	@Test
	fun `colour codes and a repeat counter do not get between a line and its own words`() {
		ChatSearch.ask("bob hi")
		assertTrue(
			ChatSearch.matches(
				Component.literal("Bob: hi").withStyle(ChatFormatting.AQUA).append(
					Component.literal(" (×4)").withStyle(ChatFormatting.GRAY)
				)
			)
		)
	}
}
