package io.github.dzkchen.dhen.features.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoreboardLoggerTest {
	@Test
	fun `a line that did not move is left out of the changed-line block`() {
		val before = listOf("SKYBLOCK", "The Catacombs", "Cleared: 40%")
		val after = listOf("SKYBLOCK", "The Catacombs", "Cleared: 55%")

		assertEquals(listOf("3. Cleared: 55%"), ScoreboardLogger.changes(before, after))
	}

	@Test
	fun `a line the sidebar dropped is written as removed at its old place`() {
		val before = listOf("SKYBLOCK", "Deaths: 1", "Cleared: 40%")
		val after = listOf("SKYBLOCK", "Deaths: 1")

		assertEquals(listOf("3. (removed)"), ScoreboardLogger.changes(before, after))
	}

	@Test
	fun `a sidebar that grew reports the new rows at their new numbers`() {
		val before = listOf("SKYBLOCK")
		val after = listOf("SKYBLOCK", "Deaths: 1", "Cleared: 40%")

		assertEquals(listOf("2. Deaths: 1", "3. Cleared: 40%"), ScoreboardLogger.changes(before, after))
	}
}
