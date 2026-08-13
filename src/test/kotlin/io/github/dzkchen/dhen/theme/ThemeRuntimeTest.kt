package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenTheme
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ThemeRuntimeTest {
	@TempDir
	lateinit var config: Path

	private val said = mutableListOf<String>()
	private val revealed = mutableListOf<Path>()
	private var persisted = 0

	private lateinit var themes: ThemeRuntime

	@BeforeEach
	fun buildRuntime() {
		themes = ThemeRuntime(
			config,
			CoroutineScope(Dispatchers.Unconfined),
			Dispatchers.Unconfined,
			{ persisted++ },
			{ said.add(it) },
			{ revealed.add(it) }
		)
	}

	@BeforeEach
	@AfterEach
	fun restoreDefaults() {
		ThemeFixture.forgetDiscovered(config)
		ClientPrefs.theme.value = ThemeStore.DEFAULT_ID
		ClientPrefs.adopt()
		ClientPrefs.accent.value = Color(DhenPalette.DEFAULT_ACCENT)
		ClientPrefs.sync()
	}

	@AfterEach
	fun unwireButtons() {
		ClientPrefs.reload.reset()
		ClientPrefs.browse.reset()
	}

	@Test
	fun `reload reads the folder and reports what it drew`() {
		ThemeFixture.write(config, "ocean", """{"colors":{"canvas":"#112233"}}""")

		themes.reload { said += it }

		assertEquals(ThemeFixture.ids("ocean"), ClientPrefs.theme.options)
		assertTrue(said.single().contains("Read ${ThemeStore.builtIn.size + 1} themes"))
	}

	@Test
	fun `use switches the theme, persists it, and names the one it switched to`() {
		ThemeFixture.write(config, "ocean", """{"colors":{"canvas":"#112233"}}""")
		themes.reload()

		val message = themes.select("OCEAN")

		assertEquals("Theme set to 'ocean'.", message)
		assertEquals("ocean", ClientPrefs.theme.value)
		assertEquals(0xFF112233u.toInt(), DhenPalette.CANVAS)
		assertEquals(1, persisted)
	}

	@Test
	fun `use names the theme it could not find and changes nothing`() {
		val message = themes.select("nope")

		assertEquals("No theme named 'nope'. Try /dhen theme list.", message)
		assertEquals(ThemeStore.DEFAULT_ID, ClientPrefs.theme.value)
		assertEquals(0, persisted)
	}

	@Test
	fun `list marks the theme on screen`() {
		ThemeFixture.write(config, "ocean", """{}""")
		themes.reload()
		themes.select("ocean")

		assertEquals("Themes: ${ThemeStore.builtIn.joinToString(", ") { it.id }}, ocean (active).", themes.summary())
	}

	@Test
	fun `export writes what is on screen and offers it in the picker`() {
		ClientPrefs.accent.value = Color(TEAL)
		ClientPrefs.sync()

		themes.export("mine") { said += it }

		assertEquals("Exported the theme on screen to themes/mine/theme.json.", said.single())
		assertTrue(ClientPrefs.theme.options.contains("mine"))
		assertEquals(TEAL, ThemeStore.find("mine")!!.theme.accent)
		assertEquals(DhenTheme.DEFAULT.canvas, ThemeStore.find("mine")!!.theme.canvas)
	}

	@Test
	fun `export with no name uses the theme that is selected`() {
		ThemeFixture.write(config, "ocean", """{"colors":{"canvas":"#112233"}}""")
		themes.reload()
		themes.select("ocean")

		themes.export(null) { said += it }

		assertEquals("Exported the theme on screen to themes/ocean-copy/theme.json.", said.single())
		assertEquals(0xFF112233u.toInt(), ThemeStore.find("ocean-copy")!!.theme.canvas)
	}

	@Test
	fun `export names the file and the reason when it cannot write`() {
		Files.writeString(Files.createDirectories(config).resolve(ThemeStore.DIRECTORY), "not a directory")

		themes.export("mine") { said += it }

		assertTrue(said.single().startsWith("Could not write themes/mine/theme.json: "), said.single())
	}

	@Test
	fun `export refuses a name that cannot be a folder`() {
		themes.export("../escape") { said += it }

		assertTrue(said.single().startsWith("'../escape' cannot name a theme folder"), said.single())
		assertFalse(Files.exists(ThemeFixture.folder(config)))
	}

	@Test
	fun `the themes folder is created before it is shown`() {
		themes.browse { said += it }

		assertEquals(listOf(ThemeFixture.folder(config)), revealed)
		assertTrue(Files.isDirectory(ThemeFixture.folder(config)))
		assertTrue(said.isEmpty())
	}

	@Test
	fun `a folder that cannot be opened reports instead of throwing`() {
		Files.writeString(Files.createDirectories(config).resolve(ThemeStore.DIRECTORY), "not a directory")

		themes.browse { said += it }

		assertTrue(said.single().startsWith("Could not open themes: "), said.single())
		assertTrue(revealed.isEmpty())
	}

	@Test
	fun `the picker's own buttons answer in chat the moment the runtime is built`() {
		ClientPrefs.browse.value()
		ClientPrefs.reload.value()

		assertEquals(listOf(ThemeFixture.folder(config)), revealed)
		assertTrue(said.single().startsWith("Read ${ThemeStore.builtIn.size} themes"), said.toString())
	}

	private companion object {
		val TEAL = 0xFF55D6C2u.toInt()
	}
}
