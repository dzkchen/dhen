package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiSearchTest {
	@Test
	fun `an empty query matches every module`() {
		assertTrue(ClickGuiSearch.matches("", "Auto Sprint", "Holds sprint for you."))
		assertTrue(ClickGuiSearch.matches("", "", ""))
	}

	@Test
	fun `a name match ignores case and matches inside the name`() {
		assertTrue(ClickGuiSearch.matches("sprint", "Auto Sprint", ""))
		assertTrue(ClickGuiSearch.matches("AUTO", "Auto Sprint", ""))
		assertTrue(ClickGuiSearch.matches("o Sp", "Auto Sprint", ""))
	}

	@Test
	fun `a description match is enough on its own`() {
		assertTrue(ClickGuiSearch.matches("keyboard", "Test Overlay", "Second placeholder for keyboard navigation."))
		assertFalse(ClickGuiSearch.matches("keyboard", "Test Overlay", "Second placeholder."))
	}

	@Test
	fun `a query matching neither field is filtered out`() {
		assertFalse(ClickGuiSearch.matches("dungeon", "Auto Sprint", "Holds sprint for you."))
	}

	@Test
	fun `whitespace is a literal search character`() {
		assertTrue(ClickGuiSearch.matches("auto s", "Auto Sprint", ""))
		assertFalse(ClickGuiSearch.matches("autos", "Auto Sprint", ""))
	}
}
