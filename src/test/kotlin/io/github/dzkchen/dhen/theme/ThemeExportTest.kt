package io.github.dzkchen.dhen.theme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.gui.DhenTheme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class ThemeExportTest {
	@TempDir
	lateinit var config: Path

	@AfterEach
	fun forgetDiscoveredThemes() {
		ThemeFixture.forgetDiscovered(config)
	}

	@Test
	fun `a resolved theme survives export and re-parsing unchanged`() {
		val folder = ThemeExport.write(config, "probe", null, PROBE)

		assertEquals(PROBE, ThemeFormat.parse("probe", read(folder)).theme)
	}

	@Test
	fun `an exported manifest names every token so a stranger's defaults cannot move it`() {
		val document = read(ThemeExport.write(config, "probe", null, PROBE))
		val colors = document.getAsJsonObject("colors")
		val motion = document.getAsJsonObject("motion")

		assertEquals(TOKEN_COUNT, colors.size())
		assertEquals(MOTION_COUNT, motion.size())
		assertTrue(colors.entrySet().all { it.value.asString.matches(Regex("#[0-9A-F]{8}")) })
		assertEquals(ThemeFormat.SCHEMA, document.get("schema").asInt)
		assertEquals("probe", document.get("id").asString)
	}

	@Test
	fun `an export carries provenance but calls itself by the folder it landed in`() {
		val source = ThemeFormat.parse("ocean", json("""{"name":"Ocean","version":"1.2","authors":["ana","bo"]}"""))

		val document = read(ThemeExport.write(config, "reef", source, PROBE))

		assertEquals("reef", document.get("name").asString)
		assertEquals("1.2", document.get("version").asString)
		assertEquals(listOf("ana", "bo"), document.getAsJsonArray("authors").map { it.asString })
	}

	@Test
	fun `keys and tokens this build does not know are carried through an export`() {
		val source = ThemeFormat.parse("ocean", json("""{"shaders":{"blur":true},"colors":{"hologram":"#123456"}}"""))

		val document = read(ThemeExport.write(config, "ocean", source, PROBE))

		assertTrue(document.getAsJsonObject("shaders").get("blur").asBoolean)
		assertEquals("#123456", document.getAsJsonObject("colors").get("hologram").asString)
		assertEquals(TOKEN_COUNT + 1, document.getAsJsonObject("colors").size())
	}

	@Test
	fun `exporting onto a name already on disk writes a copy and leaves the original alone`() {
		ThemeFixture.write(config, "ocean", """{"colors":{"canvas":"#112233"}}""")

		val folder = ThemeExport.write(config, "ocean", null, PROBE)

		assertEquals("ocean-copy", folder.fileName.toString())
		assertEquals("ocean-copy", read(folder).get("id").asString)
		assertEquals(0xFF112233u.toInt(), ThemeFormat.parse("ocean", read(ThemeFixture.folder(config).resolve("ocean"))).theme.canvas)
	}

	@Test
	fun `exporting under a built-in's name writes a copy the store can still see`() {
		val folder = ThemeExport.write(config, ThemeStore.DEFAULT_ID, null, PROBE)
		val discovered = ThemeStore.refresh(config)

		assertEquals("${ThemeStore.DEFAULT_ID}-copy", folder.fileName.toString())
		assertNotNull(discovered.firstOrNull { it.id == "${ThemeStore.DEFAULT_ID}-copy" })
	}

	@Test
	fun `an unwritable themes directory fails instead of half-writing`() {
		Files.createDirectories(config)
		Files.writeString(config.resolve(ThemeStore.DIRECTORY), "not a directory")

		assertThrows(IOException::class.java) { ThemeExport.write(config, "probe", null, PROBE) }

		assertFalse(Files.exists(ThemeFixture.folder(config).resolve("probe")))
	}

	@Test
	fun `only a name that can be a folder is accepted`() {
		assertTrue(ThemeExport.legal("ocean"))
		assertTrue(ThemeExport.legal("Ocean-2_dark"))
		assertFalse(ThemeExport.legal(""))
		assertFalse(ThemeExport.legal(".."))
		assertFalse(ThemeExport.legal("a/b"))
		assertFalse(ThemeExport.legal("a.b"))
		assertFalse(ThemeExport.legal("a b"))
		assertFalse(ThemeExport.legal("x".repeat(ThemeExport.MAX_NAME + 1)))
	}

	@Test
	fun `an export leaves no temporary file behind`() {
		val folder = ThemeExport.write(config, "probe", null, PROBE)

		assertEquals(listOf(ThemeFormat.MANIFEST), Files.list(folder).use { paths -> paths.map { it.fileName.toString() }.toList() })
	}

	private fun read(folder: Path): JsonObject = json(Files.readString(folder.resolve(ThemeFormat.MANIFEST)))

	private fun json(text: String): JsonObject = JsonParser.parseString(text) as JsonObject

	private companion object {
		const val TOKEN_COUNT = 21
		const val MOTION_COUNT = 5

		val PROBE = DhenTheme.DEFAULT.copy(
			canvas = 0x80112233u.toInt(),
			accent = 0xFF55D6C2u.toInt(),
			glassSheen = 0x24445566u.toInt(),
			entryMillis = 321L,
			tabSlide = 7.5f
		)
	}
}
