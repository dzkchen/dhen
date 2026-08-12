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
	fun `the core document keeps panel layout and the effects flag side by side`() {
		Effects.reduced = true
		val layout = mutableMapOf("Dev" to PanelState(x = 12, y = 34, collapsed = true))

		val core = Effects.writeInto(ClickGuiLayout.write(layout))

		assertEquals(12, core.getAsJsonObject("panels").getAsJsonObject("Dev").get("x").asInt)
		assertTrue(core.getAsJsonObject("effects").get("reduced").asBoolean)
	}

	private fun document(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}
