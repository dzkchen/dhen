package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.CorePersistence
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import io.github.dzkchen.dhen.json
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.theme.ThemeFixture
import io.github.dzkchen.dhen.theme.ThemeFormat
import io.github.dzkchen.dhen.theme.ThemeStore
import io.github.dzkchen.dhen.util.Color
import net.minecraft.network.chat.FontDescription
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
		DhenFont.resetForTest()
		Effects.reduced = false
		ClientPrefs.splash.value = true
		ClientPrefs.dhenFont.value = true
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
		ClientPrefs.dhenFont.value = false
		val written = ClientPrefs.writeInto(JsonObject())
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		Effects.reduced = false
		ClientPrefs.splash.value = true
		ClientPrefs.dhenFont.value = true

		ClientPrefs.read(written)

		assertEquals(TEAL, ClientPrefs.accent.value.argb)
		assertTrue(Effects.reduced)
		assertFalse(ClientPrefs.splash.value)
		assertFalse(ClientPrefs.dhenFont.value)
	}

	@Test
	fun `every theme folder on disk is offered by the picker and recolors the client at once`() {
		theme("ocean", """{"colors":{"canvas":"$OCEAN_CANVAS","accent":"$OCEAN_ACCENT"}}""")
		theme("amber", """{"colors":{"canvas":"$AMBER_CANVAS"}}""")

		pick("ocean")

		assertEquals(ThemeFixture.ids("amber", "ocean"), ClientPrefs.theme.options)
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
			ClientPrefs.sections.single { it.title == "Client" }.settings.filter { it.isVisible }
		)
	}

	@Test
	fun `the Dhen font toggle defaults on in Appearance`() {
		assertTrue(ClientPrefs.dhenFont.default)
		assertTrue(ClientPrefs.dhenFont.on)
		assertTrue(ClientPrefs.sections.single { it.title == "Appearance" }.settings.contains(ClientPrefs.dhenFont))
	}

	@Test
	fun `the profile proxy address is remembered but never drawn`() {
		ClientPrefs.read(json("""{"client":{"Profile proxy":"https://proxy.example.com"}}"""))

		assertEquals("https://proxy.example.com", ClientPrefs.profileProxy.value)
		assertFalse(ClientPrefs.profileProxy.isVisible)
		assertEquals(
			"https://proxy.example.com",
			ClientPrefs.writeInto(JsonObject()).getAsJsonObject("client").get("Profile proxy").asString
		)
		ClientPrefs.profileProxy.reset()
	}

	@Test
	fun `only an address with a site behind it is accepted as the profile proxy`() {
		val diagnostics = Diagnostics(ModuleManager())

		assertEquals(
			"A proxy address has to look like https://example.com, so 'proxy.example.com' was not saved.",
			diagnostics.profileProxy("proxy.example.com") {}
		)
		assertEquals(
			"A proxy address has to look like https://example.com, so 'http://' was not saved.",
			diagnostics.profileProxy("http://") {}
		)
		assertEquals("", ClientPrefs.profileProxy.value)
		assertEquals(
			"A proxy address has to look like https://example.com, so 'https://proxy.example.com/api?key=abc' was not saved.",
			diagnostics.profileProxy("https://proxy.example.com/api?key=abc") {}
		)
		assertEquals("", ClientPrefs.profileProxy.value)
		assertEquals("Profile proxy set to proxy.example.com.", diagnostics.profileProxy("HTTPS://proxy.example.com") {})
		assertEquals("HTTPS://proxy.example.com", ClientPrefs.profileProxy.value)
	}

	@Test
	fun `asking for the proxy address does not wipe it, and clearing it is asked for by name`() {
		val diagnostics = Diagnostics(ModuleManager())
		diagnostics.profileProxy("https://proxy.example.com") {}

		assertEquals(
			"The profile proxy address is https://proxy.example.com. Type 'url clear' to forget it.",
			diagnostics.profileProxyShown()
		)
		assertEquals("https://proxy.example.com", ClientPrefs.profileProxy.value)
		assertEquals("Cleared the profile proxy address.", diagnostics.profileProxyCleared())
		assertEquals("", ClientPrefs.profileProxy.value)
		assertEquals(
			"No profile proxy address is saved. Type one after 'url' to set it.",
			diagnostics.profileProxyShown()
		)
	}

	@Test
	fun `an address longer than the setting holds is refused rather than clipped`() {
		val diagnostics = Diagnostics(ModuleManager())
		val tooLong = "https://proxy.example.com/" + "x".repeat(ClientPrefs.profileProxy.maxLength)

		assertEquals(
			"A proxy address can be at most ${ClientPrefs.profileProxy.maxLength} characters, so that one was not saved.",
			diagnostics.profileProxy(tooLong) {}
		)
		assertEquals("", ClientPrefs.profileProxy.value)
	}

	@Test
	fun `reading the stored accent drives the palette slot`() {
		ClientPrefs.read(json("""{"client":{"Accent color":$TEAL}}"""))

		assertEquals(TEAL, DhenPalette.accent)
		assertEquals(DhenTheme.DEFAULT.withAccent(TEAL).accentForeground, DhenPalette.accentForeground)
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
		assertTrue(ClientPrefs.dhenFont.value)
		assertEquals(FontDescription.Resource(DhenType.fontId), DhenFont.resolve(FontDescription.DEFAULT))
	}

	@Test
	fun `a client block missing the Dhen font key resolves it to on`() {
		ClientPrefs.dhenFont.value = false
		ClientPrefs.sync()

		ClientPrefs.read(json("""{"client":{"Splash screen":false}}"""))

		assertTrue(ClientPrefs.dhenFont.value)
		assertEquals(FontDescription.Resource(DhenType.fontId), DhenFont.resolve(FontDescription.DEFAULT))
	}

	@Test
	fun `a malformed accent leaves the one already loaded`() {
		ClientPrefs.read(json("""{"client":{"Accent color":"pink"}}"""))

		assertEquals(DhenPalette.DEFAULT_ACCENT, ClientPrefs.accent.value.argb)
		assertEquals(DhenPalette.DEFAULT_ACCENT, DhenPalette.accent)
	}

	@Test
	fun `a stored accent is opaque however it was written`() {
		ClientPrefs.read(json("""{"client":{"Accent color":${0x2055D6C2}}}"""))

		assertEquals(0xFF, ClientPrefs.accent.value.argb ushr 24)
		assertEquals(0xFF, DhenPalette.accent ushr 24)
	}

	@Test
	fun `the core document keeps the collapsed columns and the client block side by side`() {
		Effects.reduced = true

		val core = CorePersistence.snapshot(ClickGuiState(linkedSetOf("DEV")), welcomeShown = false)

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
