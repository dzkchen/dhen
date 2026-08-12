package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiViewTest {
	@Test
	fun `write then read round-trips both sets in order`() {
		val state = ClickGuiState(linkedSetOf("DUNGEONS", "DEV"), linkedSetOf("MISC"))

		val loaded = ClickGuiView.read(ClickGuiView.writeInto(JsonObject(), state))

		assertEquals(listOf("DUNGEONS", "DEV"), loaded.collapsed.toList())
		assertEquals(listOf("MISC"), loaded.opened.toList())
	}

	@Test
	fun `read returns empty sets when the block or its lists are absent`() {
		assertTrue(ClickGuiView.read(JsonObject()).collapsed.isEmpty())
		assertTrue(ClickGuiView.read(JsonObject()).opened.isEmpty())
		assertTrue(ClickGuiView.read(document("""{"clickgui":{}}""")).opened.isEmpty())
		assertTrue(ClickGuiView.read(document("""{"clickgui":"garbage"}""")).collapsed.isEmpty())
	}

	@Test
	fun `read keeps the names it understands and drops the rest`() {
		val doc = document("""{"clickgui":{"collapsed":["DEV",7,{"a":1},"MISC","DEV"],"opened":["DEV",true]}}""")

		val loaded = ClickGuiView.read(doc)

		assertEquals(listOf("DEV", "MISC"), loaded.collapsed.toList())
		assertEquals(listOf("DEV"), loaded.opened.toList())
	}

	@Test
	fun `a file written before accordion existed loads with nothing opened`() {
		val loaded = ClickGuiView.read(document("""{"clickgui":{"collapsed":["DEV"]}}"""))

		assertEquals(listOf("DEV"), loaded.collapsed.toList())
		assertTrue(loaded.opened.isEmpty())
	}

	@Test
	fun `each mode reads its own set, and an unlisted category shows in columns but not in the accordion`() {
		val state = ClickGuiState(linkedSetOf("DEV"), linkedSetOf("MISC"))

		assertTrue(state.isBodyHidden("DEV", accordion = false))
		assertTrue(state.isBodyHidden("DEV", accordion = true))
		assertFalse(state.isBodyHidden("MISC", accordion = false))
		assertFalse(state.isBodyHidden("MISC", accordion = true))
		assertFalse(state.isBodyHidden("COMBAT", accordion = false))
		assertTrue(state.isBodyHidden("COMBAT", accordion = true))
	}

	@Test
	fun `toggling a header in one mode leaves the other mode alone`() {
		val state = ClickGuiState()

		state.toggle("DEV", accordion = true)

		assertEquals(listOf("DEV"), state.opened.toList())
		assertTrue(state.collapsed.isEmpty())

		state.toggle("DEV", accordion = true)

		assertTrue(state.opened.isEmpty())
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
