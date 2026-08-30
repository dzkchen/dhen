package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiLegibilityTest {
	@Test
	fun `module and header names end before their reserved glyph boxes`() {
		val font = StubFont()
		val moduleText = DhenType.memo()
		val headerText = DhenType.memo()
		val moduleRoom = moduleNameRoom(COLUMN_WIDTH, expandable = true, STUB_GLYPH_WIDTH)
		val headerRoom = headerTitleRoom(COLUMN_WIDTH, STUB_GLYPH_WIDTH)
		val module = moduleText.fit(font, PATHOLOGICAL_NAME, moduleRoom)
		val header = headerText.fit(font, PATHOLOGICAL_NAME, headerRoom)

		assertTrue(moduleText.width(font, module) <= moduleRoom)
		assertTrue(headerText.width(font, header) <= headerRoom)
		assertTrue(CONTENT_PAD + moduleRoom + LABEL_GAP <= COLUMN_WIDTH - CONTENT_PAD - STUB_GLYPH_WIDTH)
		assertTrue(CONTENT_PAD + headerRoom + LABEL_GAP <= COLUMN_WIDTH - CONTENT_PAD - STUB_GLYPH_WIDTH)
	}

	@Test
	fun `selector values claim the widest option so the pill stays stable`() {
		val font = StubFont()
		val options = listOf("Off", "On", "Required")
		val widest = WidestText().width(font, options)
		val firstRoom = pillValueRoom(CONTROLS_WIDTH, maxOf(widest, DhenType.width(font, options.first())), STUB_GLYPH_WIDTH, 0, 0)
		val lastRoom = pillValueRoom(CONTROLS_WIDTH, maxOf(widest, DhenType.width(font, options.last())), STUB_GLYPH_WIDTH, 0, 0)

		assertEquals(options.last().length * STUB_GLYPH_WIDTH, widest)
		assertEquals(firstRoom, lastRoom)
		assertEquals(
			pillWidth(firstRoom, 0, 0, CONTROLS_WIDTH),
			pillWidth(lastRoom, 0, 0, CONTROLS_WIDTH)
		)
	}

	@Test
	fun `click gui source has no direct or visibly raw text draw`() {
		val files = SourceScan.files().filter { CLICK_GUI_FILE.matches(it.name) }
		assertFalse(files.isEmpty())

		val offenders = files.flatMap { file ->
			val source = file.readText()
			val fitted = FITTED_ASSIGNMENT.findAll(source)
				.map { match -> match.groupValues[2] to match.groupValues[1] }
				.toSet()
			TEXT_DRAW.findAll(source).mapNotNull { match ->
				val receiver = match.groupValues[1]
				val argument = match.groupValues[2].trim()
				if (receiver to argument in fitted) null else "${file.path}: ${match.value.trim()}"
			}.toList()
		}

		assertTrue(offenders.isEmpty()) {
			"Click GUI text must be fitted before drawing, but these calls are raw:\n${offenders.joinToString("\n")}"
		}
	}

	private companion object {
		const val PATHOLOGICAL_NAME = "A setting or module name much wider than its box"
		val CLICK_GUI_FILE = Regex("""(?:ClickGui.*|SettingControl.*|ControlBody|ColorControl|SoundControl)\.kt""")
		val FITTED_ASSIGNMENT = Regex("""val\s+(\w+)\s*=\s*([\w.\[\]]+)\.fit\(""")
		val TEXT_DRAW = Regex("""([\w.\[\]]+)\.text\(\s*graphics,\s*font,\s*([^,\n]+)""")
	}
}
