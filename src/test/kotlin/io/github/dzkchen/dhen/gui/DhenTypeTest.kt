package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.chat.FontDescription
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DhenTypeTest {
	@AfterEach
	fun resetSeam() {
		DhenType.invalidateMeasurements()
		DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false)
	}

	@Test
	fun `styled text carries the bundled font`() {
		val font = DhenType.styled("Combat").style.font
		assertEquals(FontDescription.Resource(DhenType.fontId), font)
	}

	@Test
	fun `repeating a string reuses its styled component`() {
		val first = DhenType.styled("Combat")
		assertSame(first, DhenType.styled("Combat"))
		assertSame(first, DhenType.styled(StringBuilder("Combat").toString()))
	}

	@Test
	fun `chat feedback styling stays off the cached path`() {
		val cached = DhenType.styled("Toggled Test Module")
		val sent = DhenType.component("Toggled Test Module")

		assertNotSame(cached, sent)
		assertEquals(cached.style, sent.style)
	}

	@Test
	fun `a resource reload keeps the styled components it only re-measures`() {
		val before = DhenType.styled("Combat")
		DhenType.invalidateMeasurements()

		assertSame(before, DhenType.styled("Combat"))
	}

	@Test
	fun `the cache never grows past its limit`() {
		val cold = DhenType.styled("row 0")
		for (row in 1..DhenType.CACHE_LIMIT) DhenType.styled("row $row")

		assertNotSame(cold, DhenType.styled("row 0"))
	}

	@Test
	fun `a string still in use survives a flood of one-off strings`() {
		val label = DhenType.styled("Strength")
		for (value in 1..4 * DhenType.CACHE_LIMIT) {
			DhenType.styled("$value.25")
			DhenType.styled("Strength")
		}

		assertSame(label, DhenType.styled("Strength"))
	}

	@Test
	fun `flipping a vanilla font option asks for one invalidation`() {
		DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false)

		assertTrue(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = false))
		assertFalse(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = false))
		assertTrue(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = true))
		assertFalse(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = true))
		assertTrue(DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false))
	}

	@Test
	fun `no dhen surface draws or measures text outside the seam`() {
		val scanned = SOURCE_ROOT.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
		assertTrue(scanned.any { it.name == SEAM }) { "scan missed the sources at ${SOURCE_ROOT.absolutePath}" }

		val offenders = scanned.asSequence()
			.filter { it.name != SEAM }
			.flatMap { file ->
				file.readLines().asSequence().mapIndexedNotNull { index, line ->
					if (RAW_TEXT.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
				}
			}
			.toList()

		assertTrue(offenders.isEmpty()) {
			"Dhen text must go through $SEAM, but these bypass it:\n${offenders.joinToString("\n")}"
		}
	}

	@Test
	fun `the font definition sits where the font id resolves it`() {
		assertEquals("assets/${DhenType.fontId.namespace}/font/${DhenType.fontId.path}.json", DEFINITION)
	}

	@Test
	fun `the font definition declares the bundled truetype provider first`() {
		val providers = definition().getAsJsonArray("providers")
		assertTrue(providers.size() > 1, "the definition needs vanilla fallbacks behind the bundled face")

		val ttf = providers[0].asJsonObject
		assertEquals("ttf", ttf.get("type").asString)
		assertEquals("${DhenType.fontId.namespace}:$FACE", ttf.get("file").asString)
		assertTrue(ttf.get("size").asFloat > 0f, "a zero size bakes invisible glyphs")
		assertTrue(ttf.get("oversample").asFloat >= 1f, "oversample below 1 blurs the face")
	}

	@Test
	fun `the fallbacks behind the bundled face are vanilla references`() {
		val providers = definition().getAsJsonArray("providers")

		for (index in 1 until providers.size()) {
			val fallback = providers[index].asJsonObject
			assertEquals("reference", fallback.get("type").asString)
			assertTrue(fallback.get("id").asString.startsWith("minecraft:"))
		}
	}

	@Test
	fun `the bundled face is a truetype file the vanilla provider accepts`() {
		val face = resource("assets/${DhenType.fontId.namespace}/font/$FACE")

		assertTrue(face.size > MINIMUM_FACE_BYTES, "the bundled face is too small to be a real font")
		assertEquals(TRUETYPE_TAG.toList(), face.take(TRUETYPE_TAG.size))
	}

	private fun definition(): JsonObject =
		JsonParser.parseString(String(resource(DEFINITION), Charsets.UTF_8)).asJsonObject

	private fun resource(path: String): ByteArray {
		val stream = javaClass.classLoader.getResourceAsStream(path)
		assertNotNull(stream, "missing bundled resource: $path")
		return stream!!.use { it.readBytes() }
	}

	private companion object {
		const val SEAM = "DhenType.kt"
		const val DEFINITION = "assets/dhen/font/inter.json"
		const val FACE = "inter.ttf"
		const val MINIMUM_FACE_BYTES = 1024
		val TRUETYPE_TAG = byteArrayOf(0x00, 0x01, 0x00, 0x00)
		val SOURCE_ROOT = File("src/main/kotlin")
		val RAW_TEXT = Regex("""graphics\.text\(|font\.width\(|font\.lineHeight""")
	}
}
