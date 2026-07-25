package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiLayoutTest {
	@Test
	fun `write then read round-trips every panel`() {
		val layout = linkedMapOf(
			"DUNGEONS" to PanelState(40, 12, collapsed = false),
			"DEV" to PanelState(180, 90, collapsed = true)
		)
		assertEquals(layout, ClickGuiLayout.read(ClickGuiLayout.write(layout)))
	}

	@Test
	fun `read returns empty map when the panels key is absent`() {
		assertTrue(ClickGuiLayout.read(JsonObject()).isEmpty())
	}

	@Test
	fun `read skips malformed entries and defaults collapsed to false`() {
		val doc = JsonParser.parseString(
			"""{"panels":{
				"A":{"x":5,"y":6},
				"B":{"x":"nope","y":6,"collapsed":true},
				"C":"garbage",
				"D":{"x":1,"y":2,"collapsed":true}
			}}"""
		).asJsonObject

		val states = ClickGuiLayout.read(doc)
		assertEquals(PanelState(5, 6, collapsed = false), states["A"])
		assertNull(states["B"])
		assertNull(states["C"])
		assertEquals(PanelState(1, 2, collapsed = true), states["D"])
	}

	@Test
	fun `write nests panel entries under the panels key`() {
		val doc = ClickGuiLayout.write(linkedMapOf("DEV" to PanelState(1, 2, collapsed = false)))
		val entry = doc.getAsJsonObject("panels").getAsJsonObject("DEV")
		assertEquals(1, entry.get("x").asInt)
		assertEquals(2, entry.get("y").asInt)
		assertEquals(false, entry.get("collapsed").asBoolean)
	}
}
