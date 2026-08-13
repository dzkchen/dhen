package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class BuiltInThemeTest {
	@Test
	fun `every slot the record holds is a token the format reads and writes`() {
		val written = ThemeFormat.document(ThemeStore.DEFAULT_ID, null, DhenTheme.DEFAULT)
		val tokens = written.getAsJsonObject("colors").keySet() + written.getAsJsonObject("motion").keySet()

		assertEquals(tokens, slots() - DERIVED)
	}

	@Test
	fun `the light theme repaints every color rather than restating the dark one at a new alpha`() {
		val dark = colors(ThemeStore.DEFAULT_ID, DhenTheme.DEFAULT)
		val light = colors(ThemeStore.LIGHT_ID, DhenTheme.LIGHT)

		for (token in dark.keys) {
			assertNotEquals(rgb(dark.getValue(token)), rgb(light.getValue(token))) {
				"the light theme inherits the dark default for '$token'"
			}
		}
	}

	@Test
	fun `the light theme reads as light and carries its text the other way round`() {
		val light = DhenTheme.LIGHT

		assertTrue(DhenPalette.luminance(light.canvas) > LIGHT_GROUND, "the light canvas is not light")
		assertTrue(DhenPalette.luminance(light.canvas) < DhenPalette.luminance(light.surfaceInteractive))
		assertTrue(
			DhenPalette.luminance(light.border) < DhenPalette.luminance(light.surfaceInteractive),
			"the border vanishes into the surface"
		)
		assertTrue(DhenPalette.luminance(light.textPrimary) < DhenPalette.luminance(light.textSecondary))
		assertTrue(DhenPalette.luminance(light.textSecondary) < DhenPalette.luminance(light.textDisabled))
		assertTrue(
			DhenPalette.luminance(light.textDisabled) < DhenPalette.luminance(light.surface),
			"disabled text is lighter than what it sits on"
		)
	}

	@Test
	fun `every built-in keeps its accent readable and its splash legible`() {
		for (entry in ThemeStore.builtIn) {
			val theme = entry.theme
			val gap = DhenPalette.contrast(theme.accent, theme.accentForeground)
			val surface = DhenPalette.luminance(theme.surface)
			val accent = DhenPalette.luminance(theme.accent)

			assertTrue(gap >= READABLE_GAP, "${entry.id} left only $gap luminance between its accent and the text on it")
			assertTrue(
				DhenPalette.luminance(theme.accentMuted) in minOf(surface, accent)..maxOf(surface, accent),
				"${entry.id} has a muted accent outside the span it is blended from"
			)
			assertTrue(
				DhenPalette.luminance(theme.splashInk) < DhenPalette.luminance(theme.splashTrack),
				"${entry.id} has a splash bar that cannot be seen"
			)
			assertTrue(
				DhenPalette.luminance(theme.splashTrack) < DhenPalette.luminance(theme.splashCanvas),
				"${entry.id} has a splash track that cannot be seen"
			)
		}
	}

	@Test
	fun `every built-in keeps the ink it draws on the world light enough for the world to be dark`() {
		for (entry in ThemeStore.builtIn) {
			val ink = DhenPalette.luminance(entry.theme.textOnWorld)

			assertTrue(ink > WORLD_INK, "${entry.id} draws over the world in ink the night sky would swallow")
		}
	}

	@Test
	fun `every built-in draws every token that is not glass fully opaque`() {
		for (entry in ThemeStore.builtIn) {
			val solid = colors(entry.id, entry.theme).filterKeys { !it.startsWith(GLASS) }

			for ((token, hex) in solid) {
				assertTrue(hex.startsWith(OPAQUE), "${entry.id} left '$token' translucent")
			}
		}
	}

	private fun rgb(hex: String): String = hex.takeLast(RGB_DIGITS)

	private fun colors(id: String, theme: DhenTheme): Map<String, String> =
		ThemeFormat.document(id, null, theme).getAsJsonObject("colors").let { block ->
			block.keySet().associateWith { block.get(it).asString }
		}

	private fun slots(): Set<String> =
		DhenTheme::class.java.declaredFields
			.filter { !Modifier.isStatic(it.modifiers) }
			.map { it.name }
			.toSet()

	private companion object {
		const val GLASS = "glass"
		const val OPAQUE = "#FF"
		const val RGB_DIGITS = 6
		const val LIGHT_GROUND = 200
		const val WORLD_INK = 180
		const val READABLE_GAP = 100
		val DERIVED = setOf("accentMuted", "accentForeground")
	}
}
