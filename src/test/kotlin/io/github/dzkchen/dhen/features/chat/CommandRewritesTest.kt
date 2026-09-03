package io.github.dzkchen.dhen.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandRewritesTest {
	private val warps = setOf("hub", "castle", "jerry", "barn")

	@Test
	fun `a bare warp name becomes a warp command`() {
		assertEquals("warp castle", shortenedWarp("castle", warps, null))
	}

	@Test
	fun `the blocked warp is left for the server`() {
		assertNull(shortenedWarp("jerry", warps, "jerry"))
		assertEquals("warp jerry", shortenedWarp("jerry", warps, "barn"))
	}

	@Test
	fun `a command that is not a warp is left alone`() {
		assertNull(shortenedWarp("pets", warps, null))
	}

	@Test
	fun `a spelled out item name becomes an item id`() {
		assertEquals("viewrecipe SOME_ITEM 1", viewRecipeCommand("viewrecipe some item") { null })
	}

	@Test
	fun `a trailing number becomes the recipe page`() {
		assertEquals("viewrecipe SOME_ITEM 2", viewRecipeCommand("viewrecipe some item 2") { null })
	}

	@Test
	fun `a trailing number that belongs to the name stays in it`() {
		assertEquals(
			"viewrecipe ENCHANTED_BOOK_2 1",
			viewRecipeCommand("viewrecipe enchanted book 2") { name ->
				if (name == "enchanted book 2") "ENCHANTED_BOOK_2" else null
			}
		)
	}

	@Test
	fun `a recipe command that already reads as an id is left alone`() {
		assertNull(viewRecipeCommand("viewrecipe SOME_ITEM 1") { null })
	}

	@Test
	fun `another command is not mistaken for a recipe`() {
		assertNull(viewRecipeCommand("viewrecipes some item") { null })
	}

	@Test
	fun `underscores in a sack item become spaces again`() {
		assertEquals("gfs ENCHANTED LAPIS 5", sackCommand("gfs ENCHANTED_LAPIS 5") { it == "ENCHANTED LAPIS" })
	}

	@Test
	fun `an unknown sack item is sent as it was typed`() {
		assertNull(sackCommand("gfs SOME_THING 5") { false })
	}

	@Test
	fun `a sack command without an amount is left alone`() {
		assertNull(sackCommand("gfs ENCHANTED_LAPIS") { true })
	}

	@Test
	fun `the held warp commands keep the destination they were given`() {
		assertEquals("is", heldWarpCommand(listOf("is")))
		assertEquals("warp hub", heldWarpCommand(listOf("hub")))
		assertEquals("warp forge", heldWarpCommand(listOf("warpforge")))
		assertEquals("warp castle", heldWarpCommand(listOf("warp", "castle")))
		assertNull(heldWarpCommand(listOf("warp")))
	}

	@Test
	fun `the inviter is read out of a whole invite block`() {
		val block = "-----------------------------------------------------\n" +
			"[MVP+] Someone has invited you to join their party!\n" +
			"You have 60 seconds to accept.\n" +
			"-----------------------------------------------------"

		assertEquals("Someone", PARTY_INVITE.find(block)?.groupValues?.get(1))
	}

	@Test
	fun `the inviter is read out of the invite line`() {
		assertEquals("Someone", PARTY_INVITE.find("[MVP+] Someone has invited you to join their party!")?.groupValues?.get(1))
		assertEquals("Someone", PARTY_INVITE.find("Someone has invited you to join their party!")?.groupValues?.get(1))
	}

	@Test
	fun `an expired invite names the player it expired for`() {
		assertEquals(
			"Someone",
			INVITE_EXPIRED.find("The party invite from [MVP++] Someone has expired.")?.groupValues?.get(1)
		)
	}

	@Test
	fun `the cooldown line yields the seconds left`() {
		assertEquals("4", COMMAND_COOLDOWN.find("You may only use this command after 4s on the server!")?.groupValues?.get(1))
		assertNull(COMMAND_COOLDOWN.find("You may only use this command after a while on the server!"))
	}
}
