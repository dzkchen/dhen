package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.gui.DhenTheme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ThemeStoreTest {
	@TempDir
	lateinit var config: Path

	private val themes: Path get() = ThemeFixture.folder(config)

	@AfterEach
	fun forgetDiscovered() {
		ThemeFixture.forgetDiscovered(config)
	}

	@Test
	fun `a themes directory that is empty or missing leaves only the built-in`() {
		assertEquals(ThemeStore.builtIn, ThemeStore.refresh(config))
		Files.createDirectories(config.resolve(ThemeStore.DIRECTORY))
		assertEquals(ThemeStore.builtIn, ThemeStore.refresh(config))
		assertSame(DhenTheme.DEFAULT, ThemeStore.find(ThemeStore.DEFAULT_ID)?.theme)
	}

	@Test
	fun `every theme folder on disk is discovered and published in a stable order`() {
		theme("ocean", """{"name":"Ocean","colors":{"canvas":"#112233"}}""")
		theme("Amber", """{"colors":{"canvas":"#445566"}}""")

		val found = ThemeStore.refresh(config)

		assertEquals(listOf(ThemeStore.DEFAULT_ID, "Amber", "ocean"), found.map { it.id })
		assertEquals(found, ThemeStore.themes)
		assertEquals(0xFF112233u.toInt(), ThemeStore.find("ocean")?.theme?.canvas)
		assertEquals("Ocean", ThemeStore.find("OCEAN")?.name)
		assertEquals("Amber", ThemeStore.find("Amber")?.name)
	}

	@Test
	fun `a manifest that is not JSON is skipped whole and its neighbours still load`() {
		theme("broken", "{ this is not json")
		theme("array", """["not","an","object"]""")
		theme("ocean", """{"colors":{"canvas":"#112233"}}""")

		val found = ThemeStore.refresh(config)

		assertEquals(listOf(ThemeStore.DEFAULT_ID, "ocean"), found.map { it.id })
	}

	@Test
	fun `a folder without a manifest is not a theme`() {
		Files.createDirectories(themes.resolve("notes"))
		Files.writeString(themes.resolve("loose.json"), "{}")

		assertEquals(ThemeStore.builtIn, ThemeStore.refresh(config))
	}

	@Test
	fun `a folder cannot take a built-in theme's name`() {
		theme(ThemeStore.DEFAULT_ID, """{"colors":{"canvas":"#112233"}}""")

		val found = ThemeStore.refresh(config)

		assertEquals(1, found.size)
		assertSame(DhenTheme.DEFAULT, found.single().theme)
	}

	@Test
	fun `discovery stops at the theme cap and keeps the same themes every run`() {
		for (i in 0..ThemeStore.MAX_THEMES) theme("theme-%03d".format(i), "{}")

		val found = ThemeStore.refresh(config)

		assertEquals(ThemeStore.MAX_THEMES + ThemeStore.builtIn.size, found.size)
		assertNotNull(ThemeStore.find("theme-000"))
		assertNull(ThemeStore.find("theme-%03d".format(ThemeStore.MAX_THEMES)))
		assertEquals(found.map { it.id }, ThemeStore.refresh(config).map { it.id })
	}

	@Test
	fun `a manifest too large to be one is not read`() {
		theme("bloated", """{"name":"${"x".repeat(300_000)}"}""")
		theme("ocean", "{}")

		assertEquals(listOf(ThemeStore.DEFAULT_ID, "ocean"), ThemeStore.refresh(config).map { it.id })
	}

	@Test
	fun `a theme that is no longer on disk is gone from the store`() {
		theme("ocean", "{}")
		ThemeStore.refresh(config)
		assertNotNull(ThemeStore.find("ocean"))

		Files.delete(themes.resolve("ocean").resolve(ThemeFormat.MANIFEST))
		ThemeStore.refresh(config)

		assertNull(ThemeStore.find("ocean"))
	}

	private fun theme(id: String, manifest: String) = ThemeFixture.write(config, id, manifest)
}
