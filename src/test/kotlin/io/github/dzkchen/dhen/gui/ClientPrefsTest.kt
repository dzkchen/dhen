package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.config.CorePersistence
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClientPrefsTest {
	@BeforeEach
	@AfterEach
	fun restoreDefaults() {
		Effects.reduced = false
		ClientPrefs.splash.value = true
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.sync()
	}

	@Test
	fun `every setting the tab shows survives a write and read round trip`() {
		ClientPrefs.accent.value = Color(TEAL)
		Effects.reduced = true
		ClientPrefs.splash.value = false
		val written = ClientPrefs.writeInto(JsonObject())
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		Effects.reduced = false
		ClientPrefs.splash.value = true

		ClientPrefs.read(written)

		assertEquals(TEAL, ClientPrefs.accent.value.argb)
		assertTrue(Effects.reduced)
		assertFalse(ClientPrefs.splash.value)
	}

	@Test
	fun `the HUD plate is a feature's own call and never a client preference`() {
		val names = ClientPrefs.sections.flatMap { section -> section.settings.map { it.name } }

		assertFalse(names.any { it.contains("HUD", ignoreCase = true) })
		assertFalse(names.any { it.contains("background", ignoreCase = true) })
	}

	@Test
	fun `the retired layout and arrow-key rows are gone from the tab`() {
		val names = ClientPrefs.sections.flatMap { section -> section.settings.map { it.name } }

		assertFalse(names.contains("Layout"))
		assertFalse(names.contains("Arrow keys"))
		assertEquals(
			listOf(ClientPrefs.splash),
			ClientPrefs.sections.single { it.title == "Client" }.settings
		)
	}

	@Test
	fun `reading the stored accent drives the palette slot`() {
		ClientPrefs.read(document("""{"client":{"Accent color":$TEAL}}"""))

		assertEquals(TEAL, DhenPalette.accent)
		assertEquals(DhenPalette.TEXT_ON_ACCENT, DhenPalette.textOnAccent)
	}

	@Test
	fun `a document without a client block leaves the defaults in place`() {
		ClientPrefs.read(JsonObject())

		assertEquals(DhenPalette.DEFAULT_ACCENT, ClientPrefs.accent.value.argb)
		assertEquals(DhenPalette.DEFAULT_ACCENT, DhenPalette.accent)
		assertFalse(Effects.reduced)
		assertTrue(ClientPrefs.splash.default)
		assertTrue(ClientPrefs.splash.value)
	}

	@Test
	fun `a malformed accent leaves the one already loaded`() {
		ClientPrefs.read(document("""{"client":{"Accent color":"pink"}}"""))

		assertEquals(DhenPalette.DEFAULT_ACCENT, ClientPrefs.accent.value.argb)
		assertEquals(DhenPalette.DEFAULT_ACCENT, DhenPalette.accent)
	}

	@Test
	fun `a stored accent is opaque however it was written`() {
		ClientPrefs.read(document("""{"client":{"Accent color":${0x2055D6C2}}}"""))

		assertEquals(0xFF, ClientPrefs.accent.value.argb ushr 24)
		assertEquals(0xFF, DhenPalette.accent ushr 24)
	}

	@Test
	fun `the core document keeps the collapsed columns and the client block side by side`() {
		Effects.reduced = true

		val core = CorePersistence.snapshot(ClickGuiState(linkedSetOf("DEV")))

		assertEquals("DEV", core.getAsJsonObject("clickgui").getAsJsonArray("collapsed")[0].asString)
		assertFalse(core.getAsJsonObject("clickgui").has("opened"))
		assertTrue(core.getAsJsonObject("client").get(Effects.REDUCED).asBoolean)
		assertTrue(core.getAsJsonObject("client").has("Accent color"))
	}

	@Test
	fun `the effects flag and its control share one piece of state`() {
		Effects.reducedSetting.value = true

		assertTrue(Effects.reduced)

		Effects.reduced = false

		assertFalse(Effects.reducedSetting.value)
	}

	private fun document(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

	private companion object {
		val TEAL = 0xFF55D6C2u.toInt()
	}
}
