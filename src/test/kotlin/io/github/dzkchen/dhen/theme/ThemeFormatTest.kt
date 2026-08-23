package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.gui.DhenTheme
import io.github.dzkchen.dhen.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeFormatTest {
	@Test
	fun `the tokens these cases walk are the tokens the format actually writes`() {
		val written = ThemeFormat.document("probe", null, DhenTheme.DEFAULT)

		assertEquals(written.getAsJsonObject("colors").keySet(), COLOR_TOKENS.keys)
		assertEquals(written.getAsJsonObject("motion").keySet(), MOTION_TOKENS.keys)
	}

	@Test
	fun `every color token drives its own slot`() {
		for ((token, read) in COLOR_TOKENS) {
			val theme = parse("""{"colors":{"$token":"$PROBE_HEX"}}""").theme

			assertEquals(PROBE, read(theme), token)
			assertEquals(1, COLOR_TOKENS.values.count { it(theme) != it(DhenTheme.DEFAULT) }, token)
		}
	}

	@Test
	fun `every motion token drives its own slot`() {
		for ((token, read) in MOTION_TOKENS) {
			val theme = parse("""{"motion":{"$token":$PROBE_MOTION}}""").theme

			assertEquals(PROBE_MOTION, read(theme), token)
			assertEquals(1, MOTION_TOKENS.values.count { it(theme) != it(DhenTheme.DEFAULT) }, token)
		}
	}

	@Test
	fun `a theme that names no token at all is the built-in default`() {
		assertEquals(DhenTheme.DEFAULT, parse("{}").theme)
	}

	@Test
	fun `a partial theme fills the rest from the default`() {
		val theme = parse("""{"colors":{"canvas":"$PROBE_HEX"},"motion":{"tabMillis":40}}""").theme

		assertEquals(PROBE, theme.canvas)
		assertEquals(40L, theme.tabMillis)
		assertEquals(DhenTheme.DEFAULT.surface, theme.surface)
		assertEquals(DhenTheme.DEFAULT.entryMillis, theme.entryMillis)
	}

	@Test
	fun `a color without alpha lands opaque and one with alpha keeps it`() {
		val theme = parse("""{"colors":{"canvas":"#112233","surface":"#44112233","border":"#80112233","accent":"#FF112233"}}""").theme

		assertEquals(0xFF112233u.toInt(), theme.canvas)
		assertEquals(0x44112233u.toInt(), theme.surface)
		assertEquals(0x80112233u.toInt(), theme.border)
		assertEquals(0xFF112233u.toInt(), theme.accent)
	}

	@Test
	fun `the order tokens are declared in does not change what they resolve to`() {
		val forward = parse("""{"colors":{"accent":"#FFFFFF","surface":"#010203"}}""").theme
		val reversed = parse("""{"colors":{"surface":"#010203","accent":"#FFFFFF"}}""").theme

		assertEquals(forward, reversed)
		assertEquals(forward.accentMuted, reversed.accentMuted)
		assertEquals(forward.accentForeground, reversed.accentForeground)
		assertNotEquals(DhenTheme.DEFAULT.accentMuted, forward.accentMuted)
	}

	@Test
	fun `an unreadable color costs its own token and nothing else`() {
		val malformed = listOf(
			"\"112233\"", "\"0112233\"", "\"#11223\"", "\"#nothex\"", "\"#-11223\"",
			"\"#ＦＦＦＦＦＦ\"", "\"#٣٣٣٣٣٣\"", "16", "null", "{}"
		)
		for (bad in malformed) {
			val theme = parse("""{"colors":{"canvas":$bad,"surface":"$PROBE_HEX"}}""").theme

			assertEquals(DhenTheme.DEFAULT.canvas, theme.canvas, bad)
			assertEquals(PROBE, theme.surface, bad)
		}
	}

	@Test
	fun `an unreadable motion value costs its own token and nothing else`() {
		for (bad in listOf("-1", "10001", "\"200\"", "null", "[]")) {
			val theme = parse("""{"motion":{"tabMillis":$bad,"toggleMillis":$PROBE_MOTION}}""").theme

			assertEquals(DhenTheme.DEFAULT.tabMillis, theme.tabMillis, bad)
			assertEquals(PROBE_MOTION.toLong(), theme.toggleMillis, bad)
		}
	}

	@Test
	fun `a token block that is not a block of tokens costs only itself`() {
		val theme = parse("""{"colors":["#112233"],"motion":{"tabMillis":40}}""").theme

		assertEquals(DhenTheme.DEFAULT.canvas, theme.canvas)
		assertEquals(40L, theme.tabMillis)
	}

	@Test
	fun `a theme written for a later Dhen still loads what this one knows`() {
		val document = """{"schema":99,"colors":{"canvas":"$PROBE_HEX","hologram":"#123456"},"shaders":{"blur":true}}"""
		val entry = parse(document)

		assertEquals(PROBE, entry.theme.canvas)
		assertEquals(99, entry.schema)
		assertEquals(1, COLOR_TOKENS.values.count { it(entry.theme) != it(DhenTheme.DEFAULT) })
	}

	@Test
	fun `the document a theme was parsed from is kept for export`() {
		val document = parse("""{"name":"Ocean","shaders":{"blur":true}}""").document

		assertNotNull(document)
		assertTrue(document!!.has("shaders"))
		assertEquals("Ocean", document.get("name").asString)
	}

	@Test
	fun `a manifest cannot rename the folder it lives in`() {
		val entry = parse("""{"id":"somewhere-else","name":"Ocean"}""")

		assertEquals(ID, entry.id)
		assertEquals("Ocean", entry.name)
	}

	@Test
	fun `metadata is carried and falls back to the folder it was found in`() {
		val described = parse("""{"name":"Ocean","version":"1.2","authors":["ana","bo"]}""")
		val bare = parse("""{"name":"  ","authors":["ana",7]}""")

		assertEquals("Ocean", described.name)
		assertEquals("1.2", described.version)
		assertEquals(listOf("ana", "bo"), described.authors)
		assertEquals(ID, bare.name)
		assertEquals("", bare.version)
		assertEquals(listOf("ana"), bare.authors)
	}

	private fun parse(body: String): ThemeEntry = ThemeFormat.parse(ID, json(body))

	private companion object {
		const val ID = "probe"
		const val PROBE_HEX = "#112233"
		const val PROBE_MOTION = 33.0
		val PROBE = 0xFF112233u.toInt()

		val COLOR_TOKENS: Map<String, (DhenTheme) -> Int> = mapOf(
			"canvas" to { it.canvas },
			"surface" to { it.surface },
			"surfaceRaised" to { it.surfaceRaised },
			"surfaceInteractive" to { it.surfaceInteractive },
			"border" to { it.border },
			"textPrimary" to { it.textPrimary },
			"textSecondary" to { it.textSecondary },
			"textDisabled" to { it.textDisabled },
			"textOnAccent" to { it.textOnAccent },
			"textOnWorld" to { it.textOnWorld },
			"splashCanvas" to { it.splashCanvas },
			"splashTrack" to { it.splashTrack },
			"splashInk" to { it.splashInk },
			"glassCanvas" to { it.glassCanvas },
			"glassSurface" to { it.glassSurface },
			"glassSurfaceRaised" to { it.glassSurfaceRaised },
			"glassSurfaceInteractive" to { it.glassSurfaceInteractive },
			"glassScrim" to { it.glassScrim },
			"glassShadow" to { it.glassShadow },
			"glassSheen" to { it.glassSheen },
			"glassVeil" to { it.glassVeil },
			"accent" to { it.accent }
		)

		val MOTION_TOKENS: Map<String, (DhenTheme) -> Double> = mapOf(
			"entryMillis" to { it.entryMillis.toDouble() },
			"entryRise" to { it.entryRise.toDouble() },
			"tabMillis" to { it.tabMillis.toDouble() },
			"tabSlide" to { it.tabSlide.toDouble() },
			"toggleMillis" to { it.toggleMillis.toDouble() }
		)
	}
}
