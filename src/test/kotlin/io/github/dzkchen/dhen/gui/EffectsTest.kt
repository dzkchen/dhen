package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EffectsTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		Effects.reduced = false
	}

	@Test
	fun `a document without an effects block leaves the glass tier on`() {
		Effects.reduced = true

		Effects.read(JsonObject())

		assertFalse(Effects.reduced)
	}

	@Test
	fun `the stored flag survives a write and read round trip`() {
		Effects.reduced = true
		val written = Effects.writeInto(JsonObject())
		Effects.reduced = false

		Effects.read(written)

		assertTrue(Effects.reduced)
	}

	@Test
	fun `a malformed flag falls back to the glass tier`() {
		Effects.reduced = true

		Effects.read(document("""{"effects":{"reduced":"yes"}}"""))

		assertFalse(Effects.reduced)
	}

	@Test
	fun `the core document keeps the collapsed columns and the effects flag side by side`() {
		Effects.reduced = true

		val core = Effects.writeInto(ClickGuiView.writeInto(JsonObject(), setOf("DEV")))

		assertEquals("DEV", core.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
		assertTrue(core.getAsJsonObject("effects").get("reduced").asBoolean)
	}

	private fun document(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}
