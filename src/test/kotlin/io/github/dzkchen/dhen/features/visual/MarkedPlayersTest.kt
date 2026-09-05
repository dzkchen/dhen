package io.github.dzkchen.dhen.features.visual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkedPlayersTest {
	@Test
	fun `a marked name is coloured wherever it stands on its own`() {
		assertEquals(
			"§7Party > §eyellowrose§r: hello",
			highlightNames("§7Party > yellowrose: hello", setOf("yellowrose"), YELLOW)
		)
	}

	@Test
	fun `a marked name is left alone inside a longer word`() {
		assertEquals(
			"§7Party > yellowrosette: hello",
			highlightNames("§7Party > yellowrosette: hello", setOf("yellowrose"), YELLOW)
		)
		assertEquals(
			"§7Party > xyellowrose: hello",
			highlightNames("§7Party > xyellowrose: hello", setOf("yellowrose"), YELLOW)
		)
		assertEquals(
			"§7Party > yellow_rose: hello",
			highlightNames("§7Party > yellow_rose: hello", setOf("rose"), YELLOW)
		)
	}

	@Test
	fun `every standalone appearance in one line is coloured`() {
		assertEquals(
			"§eBob§r invited §eBob§r",
			highlightNames("Bob invited Bob", setOf("bob"), YELLOW)
		)
	}

	@Test
	fun `a colour code in front of the name is not part of the name`() {
		assertEquals(
			"§2Guild > §b§eBob§r§f: hi",
			highlightNames("§2Guild > §bBob§f: hi", setOf("bob"), YELLOW)
		)
	}

	@Test
	fun `a line with no marked name comes back untouched`() {
		val line = "§7Party > someone: hello"

		assertEquals(line, highlightNames(line, setOf("yellowrose"), YELLOW))
	}

	private companion object {
		const val YELLOW = "§e"
	}
}
