package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TextReplacerTest {
	@BeforeEach
	fun clearRows() {
		TextReplacer.resetSettings()
	}

	@Test
	fun `a stored row survives the round trip even when its text holds spaces`() {
		val rows = listOf("Dark Auction" to "Auction time", "hi" to "hello there")

		assertEquals(rows, storedReplacements(formatReplacements(rows)))
	}

	@Test
	fun `a row with no separator or no replacement is dropped instead of half read`() {
		val stored = formatReplacements(listOf("keep" to "kept")) + ROW_SEPARATOR + "broken" + ROW_SEPARATOR

		assertEquals(listOf("keep" to "kept"), storedReplacements(stored))
	}

	@Test
	fun `a json replacement parses into a component and plain text stays literal`() {
		val parsed = replacementComponent("""{"text":"Dhen","color":"red"}""")

		assertEquals("Dhen", parsed.string)
		assertEquals("red", parsed.style.color?.serialize())
		assertEquals("just words", replacementComponent("just words").string)
	}

	@Test
	fun `a broken json replacement still draws the text the player typed`() {
		assertEquals("""{"text":""", replacementComponent("""{"text":""").string)
	}

	@Test
	fun `adding the same search twice keeps one row holding the newer replacement`() {
		TextReplacer.add("cat", "dog")
		TextReplacer.add("cat", "fox")

		assertEquals(listOf("cat"), TextReplacer.finds())
		assertTrue(TextReplacer.list().single().contains("fox"))
	}

	@Test
	fun `removing a search that was never added changes nothing`() {
		TextReplacer.add("cat", "dog")

		assertEquals("Nothing replaces 'bird'.", TextReplacer.remove("bird"))
		assertEquals(listOf("cat"), TextReplacer.finds())
	}

	@Test
	fun `a search holding a separator is refused rather than corrupting the store`() {
		val refused = TextReplacer.add("two" + ROW_SEPARATOR + "lines", "one")

		assertTrue(refused.contains("cannot be searched for"))
		assertEquals(emptyList<String>(), TextReplacer.finds())
	}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}
	}
}
