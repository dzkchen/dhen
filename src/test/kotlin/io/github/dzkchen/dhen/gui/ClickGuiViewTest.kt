package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiViewTest {
	@Test
	fun `write then read round-trips the collapsed names in order`() {
		val state = ClickGuiState(linkedSetOf("DUNGEONS", "DEV"))

		val loaded = ClickGuiView.read(ClickGuiView.writeInto(JsonObject(), state))

		assertEquals(listOf("DUNGEONS", "DEV"), loaded.collapsed.toList())
	}

	@Test
	fun `read returns an empty set when the block or its list is absent`() {
		assertTrue(ClickGuiView.read(JsonObject()).collapsed.isEmpty())
		assertTrue(ClickGuiView.read(document("""{"clickgui":{}}""")).collapsed.isEmpty())
		assertTrue(ClickGuiView.read(document("""{"clickgui":"garbage"}""")).collapsed.isEmpty())
	}

	@Test
	fun `read keeps the names it understands and drops the rest`() {
		val doc = document("""{"clickgui":{"collapsed":["DEV",7,{"a":1},"MISC","DEV"]}}""")

		val loaded = ClickGuiView.read(doc)

		assertEquals(listOf("DEV", "MISC"), loaded.collapsed.toList())
	}

	@Test
	fun `a file the accordion left an opened list in reads its collapsed names anyway`() {
		val loaded = ClickGuiView.read(document("""{"clickgui":{"collapsed":["DEV"],"opened":["MISC"]}}"""))

		assertEquals(listOf("DEV"), loaded.collapsed.toList())
	}

	@Test
	fun `a category shows its rows until it is listed as collapsed`() {
		val state = ClickGuiState(linkedSetOf("DEV"))

		assertTrue(state.isCollapsed("DEV"))
		assertFalse(state.isCollapsed("MISC"))
	}

	@Test
	fun `toggling a header collapses it and toggling again brings it back`() {
		val state = ClickGuiState()

		state.toggle("DEV")

		assertEquals(listOf("DEV"), state.collapsed.toList())

		state.toggle("DEV")

		assertTrue(state.collapsed.isEmpty())
	}

	@Test
	fun `writing leaves the blocks already in the document alone`() {
		val doc = ClientPrefs.writeInto(JsonObject())
		ClickGuiView.writeInto(doc, ClickGuiState(linkedSetOf("DEV")))

		assertEquals("DEV", doc.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
		assertTrue(doc.has("client"))
	}

	private fun document(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}
