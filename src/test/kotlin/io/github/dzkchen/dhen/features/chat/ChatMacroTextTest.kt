package io.github.dzkchen.dhen.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMacroTextTest {
	@Test
	fun `a ranked party line yields its sender and body`() {
		val line = chatCommandLine("Party > [MVP+] Alice: !coords")

		assertEquals(ChatChannel.PARTY, line?.channel)
		assertEquals("Alice", line?.sender)
		assertEquals("!coords", line?.body)
	}

	@Test
	fun `the party rank marks survive the read`() {
		assertEquals("Bob", chatCommandLine("Party > [MVP++] Bob ⚒: !warp")?.sender)
		assertEquals("Bob", chatCommandLine("Party > Bob ቾ: !warp")?.sender)
	}

	@Test
	fun `a guild line skips the guild rank that follows the name`() {
		val line = chatCommandLine("Guild > [VIP] Carol [Officer]: !tps")

		assertEquals(ChatChannel.GUILD, line?.channel)
		assertEquals("Carol", line?.sender)
		assertEquals("!tps", line?.body)
	}

	@Test
	fun `a direct message reads as the private channel`() {
		val line = chatCommandLine("From [MVP+] Dave: !invite")

		assertEquals(ChatChannel.PRIVATE, line?.channel)
		assertEquals("Dave", line?.sender)
		assertEquals("!invite", line?.body)
	}

	@Test
	fun `chat without a leading bang is not a command`() {
		assertNull(chatCommandLine("Party > Alice: coords please"))
		assertNull(chatCommandLine("Alice: !coords"))
		assertNull(chatCommandLine("A very ordinary chat line"))
	}

	@Test
	fun `the end of a run is recognised for dungeons and kuudra`() {
		assertTrue(END_OF_RUN.matches(" ".repeat(29) + "> EXTRA STATS <"))
		assertTrue(
			END_OF_RUN.matches(
				"[NPC] Elle: Good job everyone. A hard fought battle come to an end." +
					" Let's get out of here before we run into any more trouble!"
			)
		)
		assertFalse(END_OF_RUN.matches("> EXTRA STATS <"))
	}

	@Test
	fun `queue words map to the instance the server expects`() {
		assertEquals("CATACOMBS_FLOOR_SEVEN", queueInstance("f7", null))
		assertEquals("CATACOMBS_FLOOR_ENTRANCE", queueInstance("f0", null))
		assertEquals("MASTER_CATACOMBS_FLOOR_ONE", queueInstance("m1", null))
		assertEquals("KUUDRA_INFERNAL", queueInstance("t5", null))
	}

	@Test
	fun `a floor split off the word is read from the argument`() {
		assertEquals("CATACOMBS_FLOOR_FIVE", queueInstance("f", "5"))
		assertNull(queueInstance("f", null))
		assertNull(queueInstance("f", "nine"))
	}

	@Test
	fun `floors outside the tiers a run has are refused`() {
		assertNull(queueInstance("f8", null))
		assertNull(queueInstance("m0", null))
		assertNull(queueInstance("t6", null))
	}

	@Test
	fun `every queue word the pack claims resolves or is a bare prefix`() {
		assertEquals(listOf("f", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7"), queueCommandNames('f'))
		assertEquals(listOf("m", "m1", "m2", "m3", "m4", "m5", "m6", "m7"), queueCommandNames('m'))
		assertEquals(listOf("t", "t1", "t2", "t3", "t4", "t5"), queueCommandNames('t'))
	}

	@Test
	fun `emotes replace whole words only`() {
		assertEquals("hello ❤ world", emoted("hello <3 world"))
		assertEquals("ｅｚ", emoted("ez"))
		assertNull(emoted("breeze"))
		assertNull(emoted("nothing here"))
	}

	@Test
	fun `emote replacement keeps the spacing it was given`() {
		assertEquals("a  ❤", emoted("a  <3"))
	}

	@Test
	fun `only the chat commands carry emotes`() {
		assertTrue(emotable("hello <3", isCommand = false))
		assertTrue(emotable("pc hello <3", isCommand = true))
		assertTrue(emotable("w Alice <3", isCommand = true))
		assertFalse(emotable("warp dungeon_hub", isCommand = true))
		assertFalse(emotable("party warp", isCommand = true))
	}
}
