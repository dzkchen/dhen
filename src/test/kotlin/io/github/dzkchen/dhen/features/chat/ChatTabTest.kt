package io.github.dzkchen.dhen.features.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatTabTest {
	@Test
	fun `the user tab claims a line somebody typed, with or without ranks and channel prefixes`() {
		assertTrue(ChatTab.USER.claims("Bob: hi"))
		assertTrue(ChatTab.USER.claims("[MVP+] Bob: hi"))
		assertTrue(ChatTab.USER.claims("[MVP++] [ADMIN] Bob: hi"))
		assertTrue(ChatTab.USER.claims("Party > [VIP] Bob: hi"))
		assertTrue(ChatTab.USER.claims("Guild > Bob [Officer]: hi"))
		assertTrue(ChatTab.USER.claims("To Bob: hi"))
		assertTrue(ChatTab.USER.claims("From [MVP+] Bob: hi"))
		assertTrue(ChatTab.USER.claims("Friend > Bob: hi"))
	}

	@Test
	fun `the party tab claims chat, invites, joins, leaves and disbands`() {
		assertTrue(ChatTab.PARTY.claims("Party > [VIP] Bob: hi"))
		assertTrue(ChatTab.PARTY.claims("P > Bob: hi"))
		assertTrue(ChatTab.PARTY.claims("[MVP+] Bob has invited you to join their party!"))
		assertTrue(ChatTab.PARTY.claims("Bob invited Ann to the party! They have 60 seconds to accept."))
		assertTrue(ChatTab.PARTY.claims("[MVP+] Bob has disbanded the party!"))
		assertTrue(ChatTab.PARTY.claims("The party was transferred to Bob because Ann left"))
		assertTrue(ChatTab.PARTY.claims("Bob joined the party."))
		assertTrue(ChatTab.PARTY.claims("Bob has left the party."))
		assertTrue(ChatTab.PARTY.claims("Bob has been removed from the party."))
		assertTrue(ChatTab.PARTY.claims("You are now in the PARTY channel"))
	}

	@Test
	fun `the guild, private and coop tabs claim their own prefixes`() {
		assertTrue(ChatTab.GUILD.claims("Guild > Bob: hi"))
		assertTrue(ChatTab.GUILD.claims("G > Bob: hi"))
		assertTrue(ChatTab.PM.claims("To Bob: hi"))
		assertTrue(ChatTab.PM.claims("From Bob: hi"))
		assertTrue(ChatTab.PM.claims("Friend > Bob: hi"))
		assertTrue(ChatTab.COOP.claims("Co-op > Bob: hi"))
		assertTrue(ChatTab.COOP.claims("You are now in the SKYBLOCK CO-OP channel"))
	}

	@Test
	fun `a status line that reads the same in every channel stays in the all tab`() {
		val ambiguous = "You're already in this channel!"
		assertEquals(
			listOf(ChatTab.ALL),
			ChatTab.entries.filter { it.claims(ambiguous) }
		)
	}

	@Test
	fun `a party banner's opening separator waits for the block, then belongs to it`() {
		val older = message(2, "Bob: hi")
		val opening = message(7, "-----------------------")
		val invite = message(7, "[MVP+] Bob has invited you to join their party!")

		val onArrival = listOf(older)
		assertEquals(
			SEPARATOR_UNSETTLED,
			separatorBelongs(onArrival, opening, positionOf(onArrival, opening), ChatTab.PARTY)
		)

		val settled = listOf(invite, opening, older)
		assertEquals(
			SEPARATOR_SHOWN,
			separatorBelongs(settled, opening, positionOf(settled, opening), ChatTab.PARTY)
		)
	}

	@Test
	fun `a closing separator belongs on arrival because its block is already in the history`() {
		val older = message(2, "Bob: hi")
		val opening = message(7, "-----------------------")
		val invite = message(7, "[MVP+] Bob has invited you to join their party!")
		val closing = message(7, "-----------------------")

		val history = listOf(invite, opening, older)
		assertEquals(
			SEPARATOR_SHOWN,
			separatorBelongs(history, closing, positionOf(history, closing), ChatTab.PARTY)
		)
	}

	@Test
	fun `a separator whose tick holds nothing the tab claims stays hidden`() {
		val chatter = message(7, "Guild > Bob: hi")
		val separator = message(7, "-----------------------")

		val history = listOf(chatter)
		assertEquals(
			SEPARATOR_HIDDEN,
			separatorBelongs(history, separator, positionOf(history, separator), ChatTab.PARTY)
		)
	}

	private fun message(tick: Int, text: String): GuiMessage =
		GuiMessage(tick, Component.literal(text), null, GuiMessageSource.SYSTEM_SERVER, null)
}
