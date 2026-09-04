package io.github.dzkchen.dhen.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandSuggestionTreeTest {
	private val members = listOf("Alice", "alfred", "Bob")
	private val island = listOf("Carol", "Alice")

	private val tree = SuggestionTree(
		listOf(
			SuggestionCommand(
				listOf("p", "party"),
				branches = listOf(
					SuggestionBranch(listOf("invite")) { island },
					SuggestionBranch(listOf("kick", "transfer")) { members }
				),
				offers = { listOf("list", "warp") + island }
			),
			SuggestionCommand(listOf("warp")) { listOf("hub", "castle") },
			SuggestionCommand(listOf("gfs"), matchesAnywhere = true) { listOf("ENCHANTED_LAPIS", "SUGAR_CANE") }
		)
	)

	@Test
	fun `the first argument offers every branch word and the command's own words`() {
		assertEquals(
			listOf("invite", "kick", "transfer", "list", "warp", "Carol", "Alice"),
			tree.suggestions("p ")
		)
	}

	@Test
	fun `a command that matches anywhere offers a name by its middle`() {
		assertEquals(listOf("ENCHANTED_LAPIS"), tree.suggestions("gfs lapis"))
		assertEquals(listOf("SUGAR_CANE"), tree.suggestions("gfs sugar"))
	}

	@Test
	fun `a second word picks its branch`() {
		assertEquals(listOf("Alice", "alfred"), tree.suggestions("party kick Al"))
	}

	@Test
	fun `a branch is matched however it was typed`() {
		assertEquals(listOf("Carol", "Alice"), tree.suggestions("P INVITE "))
	}

	@Test
	fun `the same name is never offered twice`() {
		assertEquals(listOf("Alice"), tree.suggestions("p Alice"))
	}
}
