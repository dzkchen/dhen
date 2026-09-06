package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
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

	private fun shown(content: Component): Boolean =
		!ChatSearch.hides(GuiMessage(0, content, null, GuiMessageSource.SYSTEM_SERVER, null))

	@Test
	fun `every word has to appear and the order between them does not matter`() {
		ChatSearch.ask("bob party")
		assertTrue(shown(Component.literal("Party > Bob: hi")))
		assertTrue(shown(Component.literal("Bob has left the party.")))
		assertFalse(shown(Component.literal("Party > Ann: hi")))
	}

	@Test
	fun `an empty query hides nothing`() {
		ChatSearch.ask("   ")
		assertTrue(shown(Component.literal("anything at all")))
	}

	@Test
	fun `colour codes and a repeat counter do not get between a line and its own words`() {
		ChatSearch.ask("bob hi")
		assertTrue(
			shown(
				Component.literal("Bob: hi").withStyle(ChatFormatting.AQUA).append(
					Component.literal(" (×4)").withStyle(ChatFormatting.GRAY)
				)
			)
		)
	}
}
