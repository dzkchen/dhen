package io.github.dzkchen.dhen.features.chat

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatCommandPacksTest {
	private val replies = RecordedReplies()

	@BeforeEach
	fun clear() = ChatCommandRegistry.forget()

	@AfterEach
	fun forget() = ChatCommandRegistry.forget()

	@Test
	fun `a registered command answers on the channel it claims`() {
		register(entry("coords", channels = EVERY) { it.reply("x: 1, y: 2, z: 3") })

		assertTrue(answer("Party > Alice: !coords"))

		assertEquals(listOf("PARTY/Alice/x: 1, y: 2, z: 3"), replies.replied)
	}

	@Test
	fun `a second pack claiming a name never takes it`() {
		register(entry("warp", channels = PARTY) { it.send("p warp") })
		ChatCommandRegistry.register(
			CommandPack("Second", listOf(entry("warp", channels = PARTY) { it.send("nope") }))
		)

		assertTrue(answer("Party > Alice: !warp"))

		assertEquals(listOf("p warp"), replies.sent)
	}

	@Test
	fun `the same name on two channels is two commands`() {
		register(
			entry("invite", channels = PRIVATE) { it.send("p invite ${it.sender}") },
			entry("invite", channels = PARTY) { it.send("p invite ${it.argument}") }
		)

		answer("From Alice: !invite")
		answer("Party > Bob: !invite Carol")

		assertEquals(listOf("p invite Alice", "p invite Carol"), replies.sent)
	}

	@Test
	fun `a leader-only command stays quiet for a member while the gate is on`() {
		register(ChatCommandEntry(listOf("warp"), PARTY, leaderOnly = true) { it.send("p warp") })

		assertFalse(answer("Party > Alice: !warp", leader = false, enforceLeader = true))
		assertTrue(answer("Party > Alice: !warp", leader = true, enforceLeader = true))
		assertTrue(answer("Party > Alice: !warp", leader = false, enforceLeader = false))
	}

	@Test
	fun `a switched-off command answers nothing`() {
		var on = false
		register(entry("fps", channels = EVERY, enabled = { on }) { it.reply("FPS: 60") })

		assertFalse(answer("Party > Alice: !fps"))
		on = true
		assertTrue(answer("Party > Alice: !fps"))
	}

	@Test
	fun `arguments keep the case they were typed in`() {
		register(entry("kick", channels = PARTY) { it.send("p kick ${it.argument}") })

		answer("Party > Alice: !kick BobTheBuilder")

		assertEquals(listOf("p kick BobTheBuilder"), replies.sent)
	}

	@Test
	fun `an overlong argument is refused`() {
		register(entry("kick", channels = PARTY) { it.send("p kick ${it.argument ?: "nobody"}") })

		answer("Party > Alice: !kick abcdefghijklmnopq")

		assertEquals(listOf("p kick nobody"), replies.sent)
	}

	@Test
	fun `a blank argument counts as no argument`() {
		register(entry("kick", channels = PARTY) { it.send("p kick ${it.argument ?: "nobody"}") })

		answer("Party > Alice: !kick  Bob")

		assertEquals(listOf("p kick nobody"), replies.sent)
	}

	@Test
	fun `help lists the canonical name of every enabled command`() {
		register(
			entry("coords", "co", channels = EVERY) { },
			entry("time", channels = EVERY, enabled = { false }) { },
			entry("warp", channels = PARTY) { }
		)

		assertEquals(listOf("coords", "warp"), ChatCommandRegistry.answered(ChatChannel.PARTY))
		assertEquals(listOf("coords"), ChatCommandRegistry.answered(ChatChannel.GUILD))
	}

	@Test
	fun `disposing a pack frees the names it held`() {
		val handle = ChatCommandRegistry.register(
			CommandPack("First", listOf(entry("warp", channels = PARTY) { it.send("first") }))
		)
		handle.unsubscribe()
		register(entry("warp", channels = PARTY) { it.send("second") })

		answer("Party > Alice: !warp")

		assertEquals(listOf("second"), replies.sent)
	}

	private fun register(vararg entries: ChatCommandEntry) =
		ChatCommandRegistry.register(CommandPack("Test", entries.toList()))

	private fun entry(
		vararg names: String,
		channels: Set<ChatChannel>,
		enabled: () -> Boolean = { true },
		run: (ChatCommandContext) -> Unit
	) = ChatCommandEntry(names.toList(), channels, enabled = enabled, run = run)

	private fun answer(chat: String, leader: Boolean = true, enforceLeader: Boolean = true): Boolean {
		val line = chatCommandLine(chat) ?: return false
		return ChatCommandRegistry.answer(line, leader, enforceLeader, replies)
	}

	private class RecordedReplies : ChatCommandReplies {
		val replied = mutableListOf<String>()
		val sent = mutableListOf<String>()

		override fun reply(channel: ChatChannel, sender: String, message: String) {
			replied += "$channel/$sender/$message"
		}

		override fun send(command: String) {
			sent += command
		}
	}

	private companion object {
		private val EVERY = setOf(ChatChannel.PARTY, ChatChannel.GUILD, ChatChannel.PRIVATE)
		private val PARTY = setOf(ChatChannel.PARTY)
		private val PRIVATE = setOf(ChatChannel.PRIVATE)
	}
}
