package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiViewTest {
	@Test
	fun `write then read round-trips the collapsed categories in order`() {
		val collapsed = linkedSetOf("DUNGEONS", "DEV")

		assertEquals(collapsed.toList(), ClickGuiView.read(ClickGuiView.writeInto(JsonObject(), collapsed)).toList())
	}

	@Test
	fun `read returns an empty set when the block or its list is absent`() {
		assertTrue(ClickGuiView.read(JsonObject()).isEmpty())
		assertTrue(ClickGuiView.read(JsonParser.parseString("""{"clickgui":{}}""").asJsonObject).isEmpty())
		assertTrue(ClickGuiView.read(JsonParser.parseString("""{"clickgui":"garbage"}""").asJsonObject).isEmpty())
	}

	@Test
	fun `read keeps the names it understands and drops the rest`() {
		val doc = JsonParser.parseString("""{"clickgui":{"collapsed":["DEV",7,{"a":1},"MISC","DEV"]}}""").asJsonObject

		assertEquals(listOf("DEV", "MISC"), ClickGuiView.read(doc).toList())
	}

	@Test
	fun `writing leaves the blocks already in the document alone`() {
		val doc = Effects.writeInto(JsonObject())
		ClickGuiView.writeInto(doc, setOf("DEV"))

		assertEquals("DEV", doc.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
		assertTrue(doc.has("effects"))
	}
}
