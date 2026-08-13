package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.config.CorePersistence
import io.github.dzkchen.dhen.theme.ThemeFixture
import io.github.dzkchen.dhen.theme.ThemeFormat
import io.github.dzkchen.dhen.theme.ThemeStore
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClientPrefsTest {
	@TempDir
	lateinit var config: Path

	@BeforeEach
	@AfterEach
	fun restoreDefaults() {
		Effects.reduced = false
		ClientPrefs.splash.value = true
		ThemeFixture.forgetDiscovered(config)
		ClientPrefs.theme.value = ThemeStore.DEFAULT_ID
		ClientPrefs.adopt()
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
	fun `every theme folder on disk is offered by the picker and recolors the client at once`() {
		theme("ocean", """{"colors":{"canvas":"$OCEAN_CANVAS","accent":"$OCEAN_ACCENT"}}""")
		theme("amber", """{"colors":{"canvas":"$AMBER_CANVAS"}}""")

		pick("ocean")

		assertEquals(listOf(ThemeStore.DEFAULT_ID, "amber", "ocean"), ClientPrefs.theme.options)
		assertEquals(OCEAN_CANVAS_ARGB, DhenPalette.CANVAS)

		ClientPrefs.theme.value = "amber"
		ClientPrefs.sync()

		assertEquals(AMBER_CANVAS_ARGB, DhenPalette.CANVAS)
		assertEquals(DhenTheme.DEFAULT.surface, DhenPalette.SURFACE)
	}

	@Test
	fun `switching to a theme hands its own accent to the accent control`() {
		theme("ocean", """{"colors":{"canvas":"$OCEAN_CANVAS","accent":"$OCEAN_ACCENT"}}""")

		pick("ocean")

		assertEquals(TEAL, ClientPrefs.accent.value.argb)
		assertEquals(TEAL, DhenPalette.accent)

		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.sync()

		assertEquals(DhenPalette.DEFAULT_ACCENT, DhenPalette.accent)
		assertEquals(OCEAN_CANVAS_ARGB, DhenPalette.CANVAS)
	}

	@Test
	fun `the selected theme survives a write and read round trip`() {
		theme("ocean", """{"colors":{"canvas":"$OCEAN_CANVAS"}}""")
		pick("ocean")

		val written = ClientPrefs.writeInto(JsonObject())
		ClientPrefs.theme.value = ThemeStore.DEFAULT_ID
		ClientPrefs.sync()
		ClientPrefs.read(written)

		assertEquals("ocean", ClientPrefs.theme.value)
		assertEquals(OCEAN_CANVAS_ARGB, DhenPalette.CANVAS)
	}

	@Test
	fun `a theme that is not on disk falls back to the built-in without losing the choice`() {
		theme("ocean", """{"colors":{"canvas":"$OCEAN_CANVAS"}}""")
		pick("ocean")
		val written = ClientPrefs.writeInto(JsonObject())

		Files.delete(ThemeFixture.folder(config).resolve("ocean").resolve(ThemeFormat.MANIFEST))
		ThemeStore.refresh(config)
		ClientPrefs.read(written)
		ClientPrefs.adopt()

		assertEquals(ThemeStore.DEFAULT_ID, ClientPrefs.theme.value)
		assertEquals(DhenTheme.DEFAULT.canvas, DhenPalette.CANVAS)
		assertEquals("ocean", ClientPrefs.theme.preferred)
		assertEquals("ocean", ClientPrefs.writeInto(JsonObject()).getAsJsonObject("client").get("Theme").asString)
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
	fun `committing a preference that is not the accent keeps the resolved theme`() {
		ClientPrefs.accent.value = Color(TEAL)
		ClientPrefs.sync()
		val resolved = DhenTheme.active

		ClientPrefs.splash.value = false
		ClientPrefs.sync()

		assertSame(resolved, DhenTheme.active)
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

	private fun theme(id: String, manifest: String) = ThemeFixture.write(config, id, manifest)

	private fun pick(id: String) {
		ThemeStore.refresh(config)
		ClientPrefs.adopt()
		ClientPrefs.theme.value = id
		ClientPrefs.sync()
	}

	private companion object {
		val TEAL = 0xFF55D6C2u.toInt()
		const val OCEAN_CANVAS = "#112233"
		const val OCEAN_ACCENT = "#55D6C2"
		const val AMBER_CANVAS = "#443322"
		val OCEAN_CANVAS_ARGB = 0xFF112233u.toInt()
		val AMBER_CANVAS_ARGB = 0xFF443322u.toInt()
	}
}
