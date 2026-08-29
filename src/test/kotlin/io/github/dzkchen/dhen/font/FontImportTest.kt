package io.github.dzkchen.dhen.font

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class FontImportTest {
	@TempDir
	lateinit var config: Path

	@TempDir
	lateinit var elsewhere: Path

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
	fun `a chosen file is copied in under its own name and the original is left alone`() {
		val source = source("Ocean")

		assertEquals("Ocean", FontStore.importFrom(source))

		assertTrue(Files.isRegularFile(fonts.resolve("Ocean.ttf")))
		assertTrue(Files.isRegularFile(source))
		assertEquals(listOf("Ocean"), FontStore.refresh().map { it.name })
		assertEquals(FontStore.DEFAULT_SIZE, FontStore.find("Ocean")!!.size)
		assertEquals(FontStore.DEFAULT_OVERSAMPLE, FontStore.find("Ocean")!!.oversample)
	}

	@Test
	fun `spaces and punctuation each become one separator, so an ordinary filename installs`() {
		assertEquals("Helvetica-Neue", FontStore.importFrom(source("Helvetica Neue")))
		assertEquals("My-Font", FontStore.importFrom(source("My  Font!!")))
		assertEquals("Roboto-Mono", FontStore.importFrom(source("...Roboto (Mono)...")))
	}

	@Test
	fun `a stem with nothing legal left in it installs as Font`() {
		assertEquals("Font", FontStore.importFrom(source("...")))
		assertEquals("Font-copy", FontStore.importFrom(source("!!!")))
	}

	@Test
	fun `an over-long stem is cut to the name limit and still leaves room for a copy suffix`() {
		val long = "L".repeat(64)

		assertEquals("L".repeat(32), FontStore.importFrom(source(long)))
		assertEquals("L".repeat(27) + "-copy", FontStore.importFrom(source(long)))
	}

	@Test
	fun `the bundled and vanilla names are never claimed`() {
		assertEquals("Inter-copy", FontStore.importFrom(source("Inter")))
		assertEquals("vanilla-copy", FontStore.importFrom(source("vanilla")))
	}

	@Test
	fun `an installed face is never overwritten, whatever its case`() {
		FontFixture.truetype(config, "Ocean")
		val installed = fonts.resolve("Ocean.ttf")
		val before = Files.readAllBytes(installed).toList()

		assertEquals("ocean-copy", FontStore.importFrom(source("ocean", FontFixture.TRUETYPE_TAG + ByteArray(8))))
		assertEquals("OCEAN-copy-2", FontStore.importFrom(source("OCEAN")))

		assertEquals(before, Files.readAllBytes(installed).toList())
	}

	@Test
	fun `a file that is not a truetype face is refused and nothing on disk changes`() {
		FontFixture.truetype(config, "Ocean")

		assertEquals(
			"only files ending in .ttf are fonts",
			assertThrows<IOException> { FontStore.importFrom(source("Serif", extension = ".otf")) }.message
		)
		assertEquals(
			"the game draws TrueType outlines only, not OpenType or collections",
			assertThrows<IOException> { FontStore.importFrom(source("Serif", "OTTO padding".toByteArray())) }.message
		)
		assertTrue(
			assertThrows<IOException> { FontStore.importFrom(source("Huge", ByteArray(17 * 1024 * 1024))) }
				.message!!.endsWith("bytes is too large to be a face")
		)
		assertEquals(
			"it is not a file",
			assertThrows<IOException> { FontStore.importFrom(elsewhere.resolve("Gone.ttf")) }.message
		)

		assertEquals(listOf("Ocean"), FontStore.refresh().map { it.name })
	}

	@Test
	fun `a name whose sidecar is still lying around is skipped so the face keeps the default tuning`() {
		FontFixture.sidecar(config, "Ocean", """{"size": 30}""")

		assertEquals("Ocean-copy", FontStore.importFrom(source("Ocean")))
		assertEquals(FontStore.DEFAULT_SIZE, FontStore.refresh().single().size)
	}

	@Test
	fun `a folder already holding every face the picker shows refuses the copy rather than hiding it`() {
		repeat(FontStore.MAX_FONTS) { FontFixture.truetype(config, "Face$it") }

		assertEquals(
			"the fonts folder already holds the ${FontStore.MAX_FONTS} faces Dhen shows",
			assertThrows<IOException> { FontStore.importFrom(source("Ocean")) }.message
		)

		assertFalse(Files.exists(fonts.resolve("Ocean.ttf")))
	}

	@Test
	fun `a copy that cannot finish leaves no partial file for the next scan`() {
		val source = source("Ocean")
		Files.createDirectories(fonts.resolve("Ocean.ttf.tmp").resolve("in the way"))

		assertThrows<IOException> { FontStore.importFrom(source) }

		assertFalse(Files.exists(fonts.resolve("Ocean.ttf")))
		assertEquals(emptyList<String>(), FontStore.refresh().map { it.name })
	}

	private fun source(
		stem: String,
		bytes: ByteArray = FontFixture.TRUETYPE_TAG + ByteArray(64),
		extension: String = FontStore.EXTENSION
	): Path {
		val folder = Files.createDirectories(elsewhere.resolve(stem.hashCode().toString()))
		return Files.write(folder.resolve(stem + extension), bytes)
	}
}
