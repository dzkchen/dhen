package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.theme.ThemeFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.Locale
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class DhenThemeTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		DhenTheme.activate(DhenTheme.DEFAULT)
	}

	@Test
	fun `every token the palette exposes reads the active theme`() {
		DhenTheme.activate(DhenTheme.DEFAULT.withAccent(TEAL))

		assertEquals(DhenTheme.active.canvas, DhenPalette.CANVAS)
		assertEquals(DhenTheme.active.surface, DhenPalette.SURFACE)
		assertEquals(DhenTheme.active.surfaceRaised, DhenPalette.SURFACE_RAISED)
		assertEquals(DhenTheme.active.surfaceInteractive, DhenPalette.SURFACE_INTERACTIVE)
		assertEquals(DhenTheme.active.border, DhenPalette.BORDER)
		assertEquals(DhenTheme.active.textPrimary, DhenPalette.TEXT_PRIMARY)
		assertEquals(DhenTheme.active.textSecondary, DhenPalette.TEXT_SECONDARY)
		assertEquals(DhenTheme.active.textDisabled, DhenPalette.TEXT_DISABLED)
		assertEquals(DhenTheme.active.textOnAccent, DhenPalette.TEXT_ON_ACCENT)
		assertEquals(DhenTheme.active.textOnWorld, DhenPalette.TEXT_ON_WORLD)
		assertEquals(DhenTheme.active.splashCanvas, DhenPalette.SPLASH_CANVAS)
		assertEquals(DhenTheme.active.splashTrack, DhenPalette.SPLASH_TRACK)
		assertEquals(DhenTheme.active.splashInk, DhenPalette.SPLASH_INK)
		assertEquals(DhenTheme.active.glassCanvas, DhenPalette.GLASS_CANVAS)
		assertEquals(DhenTheme.active.glassSurface, DhenPalette.GLASS_SURFACE)
		assertEquals(DhenTheme.active.glassSurfaceRaised, DhenPalette.GLASS_SURFACE_RAISED)
		assertEquals(DhenTheme.active.glassSurfaceInteractive, DhenPalette.GLASS_SURFACE_INTERACTIVE)
		assertEquals(DhenTheme.active.glassScrim, DhenPalette.GLASS_SCRIM)
		assertEquals(DhenTheme.active.glassShadow, DhenPalette.GLASS_SHADOW)
		assertEquals(DhenTheme.active.glassSheen, DhenPalette.GLASS_SHEEN)
		assertEquals(DhenTheme.active.glassVeil, DhenPalette.GLASS_VEIL)
		assertEquals(TEAL, DhenPalette.accent)
		assertEquals(DhenTheme.active.accentMuted, DhenPalette.accentMuted)
		assertEquals(DhenTheme.active.accentForeground, DhenPalette.accentForeground)
		assertEquals(DhenTheme.DEFAULT.accent, DhenPalette.DEFAULT_ACCENT)
	}

	@Test
	fun `the motion the animations read comes from the active theme`() {
		DhenTheme.activate(DhenTheme.DEFAULT.copy(entryMillis = 40L, entryRise = 3f, tabMillis = 30L, tabSlide = 4f, toggleMillis = 20L))

		assertEquals(40L, GlassGui.ENTRY_MILLIS)
		assertEquals(3f, GlassGui.ENTRY_RISE)
		assertEquals(30L, GlassGui.TAB_MILLIS)
		assertEquals(4f, GlassGui.TAB_SLIDE)
		assertEquals(20L, GlassGui.TOGGLE_MILLIS)
	}

	@Test
	fun `every activation reaches the next read the palette serves`() {
		assertEquals(DhenTheme.DEFAULT.canvas, DhenPalette.CANVAS)

		DhenTheme.activate(DhenTheme.LIGHT)

		assertEquals(DhenTheme.LIGHT.canvas, DhenPalette.CANVAS)
		assertEquals(DhenTheme.LIGHT.accent, DhenPalette.accent)

		DhenTheme.activate(DhenTheme.LIGHT.withAccent(TEAL).copy(entryMillis = 40L))

		assertEquals(TEAL, DhenPalette.accent)
		assertEquals(DhenTheme.LIGHT.canvas, DhenPalette.CANVAS)
		assertEquals(40L, GlassGui.ENTRY_MILLIS)

		DhenTheme.activate(DhenTheme.DEFAULT)

		assertEquals(DhenTheme.DEFAULT.canvas, DhenPalette.CANVAS)
		assertEquals(DhenTheme.DEFAULT.accent, DhenPalette.accent)
		assertEquals(DhenTheme.DEFAULT.entryMillis, GlassGui.ENTRY_MILLIS)
	}

	@Test
	fun `the draw path reads a plain field while the cross-thread one stays volatile`() {
		val drawn = DhenTheme::class.java.getDeclaredField("activeOnRenderThread")
		val shared = DhenTheme::class.java.getDeclaredField("active")

		assertFalse(Modifier.isVolatile(drawn.modifiers))
		assertTrue(Modifier.isVolatile(shared.modifiers))
	}

	@Test
	fun `an accent override leaves the theme it was resolved from untouched`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertEquals(TEAL, themed.accent)
		assertEquals(0xFFF5A9C6u.toInt(), DhenTheme.DEFAULT.accent)
		assertEquals(DhenTheme.DEFAULT.surface, themed.surface)
	}

	@Test
	fun `re-resolving the accent already in place allocates nothing`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertSame(DhenTheme.DEFAULT, DhenTheme.DEFAULT.withAccent(DhenTheme.DEFAULT.accent))
		assertSame(themed, themed.withAccent(TEAL))
	}

	@Test
	fun `text on the accent stays readable whichever accent the user picks`() {
		for (candidate in listOf(DhenTheme.DEFAULT.accent, TEAL, 0xFF3A0B5Fu.toInt(), 0xFF000000u.toInt())) {
			val themed = DhenTheme.DEFAULT.withAccent(candidate)
			val gap = DhenPalette.contrast(candidate, themed.accentForeground)

			assertTrue(gap >= 100, "accent ${Integer.toHexString(candidate)} left only $gap luminance of contrast")
		}
	}

	@Test
	fun `the muted accent follows the slot it is derived from`() {
		val themed = DhenTheme.DEFAULT.withAccent(TEAL)

		assertNotEquals(DhenTheme.DEFAULT.accentMuted, themed.accentMuted)
		assertEquals(0xFF, themed.accentMuted ushr 24)
		assertTrue(DhenPalette.luminance(themed.accentMuted) < DhenPalette.luminance(themed.accent))
		assertTrue(DhenPalette.luminance(themed.accentMuted) > DhenPalette.luminance(themed.surface))
	}

	@Test
	fun `a transparent accent keeps its alpha through the muted derivation`() {
		assertEquals(0x80, DhenTheme.DEFAULT.withAccent(0x80F5A9C6u.toInt()).accentMuted ushr 24)
	}

	@Test
	fun `every slot the record holds is reachable through the palette`() {
		val slots = DhenTheme::class.memberProperties
			.filter { it.returnType.classifier == Int::class && it.name !in DERIVED }
		val colors = JsonObject()
		slots.forEachIndexed { index, slot -> colors.addProperty(slot.name, probe(index)) }
		DhenTheme.activate(ThemeFormat.parse(PROBE_ID, JsonObject().apply { add("colors", colors) }).theme)

		val exposed = DhenPalette::class.memberProperties
			.filter { it.visibility == KVisibility.PUBLIC }
			.mapNotNull { it.getter.call(DhenPalette) as? Int }
			.toSet()
		val unreachable = slots.filter { (it.getter.call(DhenTheme.active) as Int) !in exposed }

		assertTrue(unreachable.isEmpty()) {
			"DhenPalette forwards each token by hand, so these are unreadable through it: ${unreachable.map { it.name }}"
		}
	}

	@Test
	fun `no color literal lives outside the token table`() {
		val scanned = SourceScan.files()
		assertTrue(scanned.any { it.name == TOKENS }) { "scan missed the sources at ${SourceScan.MAIN.absolutePath}" }
		assertTrue(scanned.any { it.extension == "java" }) { "scan missed the mixins at ${SourceScan.MAIN.absolutePath}" }

		val offenders = SourceScan.offenders(scanned, TOKENS, ARGB)

		assertTrue(offenders.isEmpty()) {
			"Every color belongs to $TOKENS, but these hold their own:\n${offenders.joinToString("\n")}"
		}
	}

	private fun probe(index: Int): String = String.format(Locale.ROOT, "#FF%02X%02X%02X", index + 1, 0x40 + index, 0x90 + index)

	private companion object {
		const val TOKENS = "DhenTheme.kt"
		const val PROBE_ID = "probe"
		val DERIVED = setOf("accentMuted", "accentForeground")
		val TEAL = 0xFF55D6C2u.toInt()
		val ARGB = Regex("""0[xX][0-9A-Fa-f]{8}""")
	}
}
