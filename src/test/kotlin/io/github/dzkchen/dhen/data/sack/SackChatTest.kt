package io.github.dzkchen.dhen.data.sack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SackChatTest {
	@Test
	fun `an added hover reads every row, its grouped amount and its item name`() {
		val changes = HashMap<String, Long>()
		readSackChanges(
			"""
			Added:
			 +1,024 Enchanted Cobblestone (Mining Sack)
			 +7 Coal (Mining Sack)
			 +64 Jungle Wood (Foraging Sack, Wood Sack)
			""".trimIndent(),
			changes
		)
		assertEquals(1024L, changes["Enchanted Cobblestone"])
		assertEquals(7L, changes["Coal"])
		assertEquals(64L, changes["Jungle Wood"])
		assertEquals(3, changes.size)
	}

	@Test
	fun `a removed hover reads negative amounts`() {
		val changes = HashMap<String, Long>()
		readSackChanges(
			"""
			Removed:
			 -2,500 Cobblestone (Mining Sack)
			 -1 Enchanted Coal (Mining Sack)
			""".trimIndent(),
			changes
		)
		assertEquals(-2500L, changes["Cobblestone"])
		assertEquals(-1L, changes["Enchanted Coal"])
	}

	@Test
	fun `an item added and removed in the same message keeps only the difference`() {
		val changes = HashMap<String, Long>()
		readSackChanges(" +10 Coal (Mining Sack)", changes)
		readSackChanges(" -4 Coal (Mining Sack)", changes)
		assertEquals(6L, changes["Coal"])
		assertEquals(1, changes.size)
	}

	@Test
	fun `a hover with no rows leaves the changes empty`() {
		val changes = HashMap<String, Long>()
		readSackChanges("Added: and 4 other items", changes)
		assertTrue(changes.isEmpty())
	}
}
