package io.github.dzkchen.dhen.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatLinesTest {
	@Test
	fun `a counted line keys on the text without its counter`() {
		assertEquals("Party > Bob: hi", stripRepeatSuffix("Party > Bob: hi (×3)"))
	}

	@Test
	fun `only digits between the marker and the bracket count as a counter`() {
		assertEquals("Bob: hi (×3a)", stripRepeatSuffix("Bob: hi (×3a)"))
		assertEquals("Bob: hi (×3) more", stripRepeatSuffix("Bob: hi (×3) more"))
		assertEquals("Bob: hi", stripRepeatSuffix("Bob: hi"))
	}

	@Test
	fun `a line that is nothing but a counter is left alone`() {
		assertEquals(" (×2)", stripRepeatSuffix(" (×2)"))
	}

	@Test
	fun `a rule of five or more separator characters is a separator`() {
		assertTrue(isSeparatorLine("-----"))
		assertTrue(isSeparatorLine("▬▬▬▬▬▬▬▬"))
		assertFalse(isSeparatorLine("----"))
		assertFalse(isSeparatorLine("Bob: hi"))
	}

	@Test
	fun `a banner with a title between two rules is a separator`() {
		assertTrue(isSeparatorLine("----- Party Finder -----"))
		assertFalse(isSeparatorLine("-- P --"))
		assertFalse(isSeparatorLine("Bob: ----- hi"))
	}

	@Test
	fun `a separator that has already been counted still reads as a separator`() {
		assertTrue(isSeparatorLine("-------- (×2)"))
	}
}
