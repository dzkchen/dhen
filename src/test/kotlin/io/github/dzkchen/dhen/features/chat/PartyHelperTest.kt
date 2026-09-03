package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.MessageSendEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PartyHelperTest {
	private val manager = ModuleManager()

	@BeforeEach
	fun enable() {
		SkyBlockLocation.greeted()
		manager.register(PartyHelper)
		manager.enable(PartyHelper)
	}

	@AfterEach
	fun disable() {
		manager.unregister(PartyHelper)
		SkyBlockLocation.reset()
	}

	@Test
	fun `the whole row is the toggle because the module declares no settings`() {
		assertEquals("Party Helper", PartyHelper.name)
		assertEquals(Category.CHAT, PartyHelper.category)
		assertTrue(PartyHelper.settings.isEmpty())
		assertEquals(3, PartyHelper.subscriptionCount)
	}

	@Test
	fun `party list chatter passes through until the list is asked for`() {
		assertFalse(cancelled("-----------------------------"))
		assertFalse(cancelled("Party Members (2)"))
		assertFalse(cancelled(""))
	}

	@Test
	fun `asking for the list swallows every vanilla line it is about to replace`() {
		sent("pl")

		assertTrue(cancelled("-----------------------------"))
		assertTrue(cancelled("Party Members (2)"))
		assertTrue(cancelled("Party Leader: §b[MVP§d+§b] Alice §a● "))
		assertTrue(cancelled("Party Members: §7Bob §c● "))
		assertTrue(cancelled(""))
	}

	@Test
	fun `the second divider closes the window and later chat is left alone`() {
		sent("pl")

		assertTrue(cancelled("-----------------------------"))
		assertTrue(cancelled("-----------------------------"))
		assertFalse(cancelled(""))
	}

	@Test
	fun `a second request before the first answer keeps the later reply covered`() {
		sent("pl")
		sent("pl")

		assertTrue(cancelled("-----------------------------"))
		assertTrue(cancelled("-----------------------------"))
		assertTrue(cancelled("Party Members (2)"))
		assertTrue(cancelled("-----------------------------"))
		assertTrue(cancelled("-----------------------------"))
		assertFalse(cancelled(""))
	}

	@Test
	fun `both long spellings of the list command arm the window`() {
		sent("Party List")
		assertTrue(cancelled("-----------------------------"))

		sent("p list")
		assertTrue(cancelled("-----------------------------"))
	}

	@Test
	fun `a typed message that is not the list command arms nothing`() {
		sent("pl", command = false)
		sent("party")

		assertFalse(cancelled("-----------------------------"))
	}

	@Test
	fun `an empty party answers in full and closes the window`() {
		sent("pl")

		assertFalse(cancelled("You are not currently in a party."))
		assertFalse(cancelled(""))
	}

	@Test
	fun `leaving the server drops a list that never arrived`() {
		sent("pl")
		manager.eventBus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertFalse(cancelled(""))
	}

	@Test
	fun `switching the module off drops a list that never arrived`() {
		sent("pl")
		manager.disable(PartyHelper)
		manager.enable(PartyHelper)

		assertFalse(cancelled(""))
	}

	private fun sent(message: String, command: Boolean = true) {
		val event = MessageSendEvent()
		event.message = message
		event.isCommand = command
		manager.eventBus.type<MessageSendEvent>().dispatch(event)
	}

	private fun cancelled(line: String): Boolean {
		val event = ChatReceiveEvent()
		event.text = Component.literal(line)
		manager.eventBus.type<ChatReceiveEvent>().dispatch(event)
		return event.cancelled
	}
}
