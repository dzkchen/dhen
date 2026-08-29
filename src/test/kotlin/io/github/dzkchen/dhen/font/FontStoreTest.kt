package io.github.dzkchen.dhen.font

import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FontStoreTest {
	@TempDir
	lateinit var config: Path

	private val fonts: Path get() = config.resolve(FontStore.DIRECTORY)

	@BeforeEach
	fun readTheTempFolder() {
		FontStore.resetForTest()
		FontStore.install(config)
	}

	@AfterEach
	fun forgetDiscovered() {
		FontStore.resetForTest()
	}

	@Test
	fun `a missing or empty fonts folder leaves Inter as the only choice`() {
		assertEquals(emptyList<FontFace>(), FontStore.refresh())
		assertEquals(listOf(FontStore.INTER), FontStore.names)

		Files.createDirectories(fonts)

		assertEquals(emptyList<FontFace>(), FontStore.refresh())
		assertEquals(listOf(FontStore.INTER), FontStore.names)
	}

	@Test
	fun `every truetype file is discovered, named by its file and published in a stable order`() {
		face("Ocean")
		face("amber")

		val found = FontStore.refresh()

		assertEquals(listOf("amber", "Ocean"), found.map { it.name })
		assertEquals(listOf(FontStore.INTER, "amber", "Ocean"), FontStore.names)
		assertEquals("ocean", FontStore.find("OCEAN")?.id)
	}

	@Test
	fun `the first scan happens on first use, without a refresh`() {
		face("Lazy")

		assertEquals(listOf("Lazy"), FontStore.faces().map { it.name })
		assertNotNull(FontStore.find("Lazy"))
	}

	@Test
	fun `an OpenType or collection file is rejected and its neighbours still load`() {
		face("Good")
		write("Otf", byteArrayOf(0x4F, 0x54, 0x54, 0x4F))
		write("Collection", byteArrayOf(0x74, 0x74, 0x63, 0x66))
		write("Tiny", byteArrayOf(0x00, 0x01))

		assertEquals(listOf("Good"), FontStore.refresh().map { it.name })
	}

	@Test
	fun `a mis-named or reserved file is skipped and its neighbours still load`() {
		face("Good")
		face("has space")
		face("x".repeat(33))
		face(FontStore.INTER)
		face(FontStore.VANILLA)

		assertEquals(listOf("Good"), FontStore.refresh().map { it.name })
	}

	@Test
	fun `two files whose names differ only by case keep the first alone`() {
		face("Twice")
		face("twice")

		assertEquals(1, FontStore.refresh().size)
	}

	@Test
	fun `nothing but a truetype file is read out of the folder`() {
		face("Good")
		Files.writeString(fonts.resolve("notes.txt"), "not a font")
		Files.createDirectories(fonts.resolve("Folder.ttf"))

		assertEquals(listOf("Good"), FontStore.refresh().map { it.name })
	}

	@Test
	fun `discovery stops at the cap and the rest are ignored`() {
		for (index in 0..FontStore.MAX_FONTS) face("face-${'a' + index / 26}${'a' + index % 26}")

		assertEquals(FontStore.MAX_FONTS, FontStore.refresh().size)
	}

	@Test
	fun `a face with no sidecar bakes at the bundled face's values`() {
		face("Plain")

		val found = FontStore.refresh().single()

		assertEquals(FontStore.DEFAULT_SIZE, found.size)
		assertEquals(FontStore.DEFAULT_OVERSAMPLE, found.oversample)
		assertEquals(0.0, found.shiftX)
		assertEquals(0.0, found.shiftY)
	}

	@Test
	fun `a sidecar tunes only the keys it carries`() {
		face("Tuned")
		sidecar("Tuned", """{"size":12.0,"shift":[1.0,-2.0]}""")

		val found = FontStore.refresh().single()

		assertEquals(12.0, found.size)
		assertEquals(FontStore.DEFAULT_OVERSAMPLE, found.oversample)
		assertEquals(1.0, found.shiftX)
		assertEquals(-2.0, found.shiftY)
	}

	@Test
	fun `an unreadable key keeps the bundled value and the rest of the sidecar still applies`() {
		face("Half")
		sidecar("Half", """{"size":"big","oversample":2.0,"shift":[0.0]}""")

		val found = FontStore.refresh().single()

		assertEquals(FontStore.DEFAULT_SIZE, found.size)
		assertEquals(2.0, found.oversample)
		assertEquals(0.0, found.shiftY)
	}

	@Test
	fun `an out-of-range key keeps the bundled value`() {
		face("Wild")
		sidecar("Wild", """{"size":0.0,"oversample":9999.0}""")

		val found = FontStore.refresh().single()

		assertEquals(FontStore.DEFAULT_SIZE, found.size)
		assertEquals(FontStore.DEFAULT_OVERSAMPLE, found.oversample)
	}

	@Test
	fun `a sidecar that is not a JSON object leaves the face at the bundled values`() {
		face("Broken")
		sidecar("Broken", "{ this is not json")

		val found = FontStore.refresh().single()

		assertEquals(FontStore.DEFAULT_SIZE, found.size)
	}

	@Test
	fun `a font that leaves the folder stops being found`() {
		face("Gone")
		FontStore.refresh()
		Files.delete(fonts.resolve("Gone${FontStore.EXTENSION}"))

		assertEquals(emptyList<FontFace>(), FontStore.refresh())
		assertNull(FontStore.find("Gone"))
		assertEquals(listOf(FontStore.INTER), FontStore.names)
	}

	@Test
	fun `the served definition points at the served face and keeps the vanilla fallbacks behind it`() {
		face("Serif")
		sidecar("Serif", """{"size":8.0,"oversample":2.0,"shift":[0.0,1.0]}""")
		val found = FontStore.refresh().single()

		val document = JsonParser.parseString(String(DhenFontPack.definition(found), Charsets.UTF_8)).asJsonObject
		val providers = document.getAsJsonArray("providers")
		val ttf = providers[0].asJsonObject

		assertEquals("ttf", ttf.get("type").asString)
		assertEquals("dhen:user/serif.ttf", ttf.get("file").asString)
		assertEquals(8.0, ttf.get("size").asDouble)
		assertEquals(2.0, ttf.get("oversample").asDouble)
		assertEquals(1.0, ttf.getAsJsonArray("shift")[1].asDouble)
		assertTrue(providers.size() > 1)
		for (index in 1 until providers.size()) {
			assertEquals("reference", providers[index].asJsonObject.get("type").asString)
			assertTrue(providers[index].asJsonObject.get("id").asString.startsWith("minecraft:"))
		}
		assertEquals("font/user/serif.json", DhenFontPack.definitionPath(found))
		assertEquals("font/user/serif.ttf", DhenFontPack.facePath(found))
	}

	private fun face(name: String) = FontFixture.truetype(config, name)

	private fun write(name: String, bytes: ByteArray) = FontFixture.write(config, name, bytes)

	private fun sidecar(name: String, text: String) = FontFixture.sidecar(config, name, text)
}
