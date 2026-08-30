package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.font.FontStore
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.gui.Font
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.function.IntSupplier

class ClickGuiLegibilityTest {
	@AfterEach
	fun resetFont() {
		DhenFont.resetForTest()
		DhenType.invalidateMeasurements()
	}

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
	fun `only the module row whose name wraps grows, and it grows by exactly one line`() {
		val font = StubFont()
		val column = columnOf(font, TALL_VIEWPORT, FITTING_NAME, WRAPPING_NAME, "Third")
		column.measure(font)
		val lineHeight = DhenType.lineHeight(font)

		assertEquals(HEADER_HEIGHT + BODY_PAD + 3 * ROW_HEIGHT + lineHeight, column.height)
	}

	@Test
	fun `hit testing a wrapped module row returns that row at every scroll offset`() {
		val font = StubFont()
		val names = arrayOf(FITTING_NAME, WRAPPING_NAME, "Third", "Fourth", "Fifth")
		val column = columnOf(font, CLIPPED_VIEWPORT, *names)
		column.measure(font)
		val lineHeight = DhenType.lineHeight(font)
		val heights = IntArray(names.size) { if (names[it] == WRAPPING_NAME) ROW_HEIGHT + lineHeight else ROW_HEIGHT }

		assertTrue(column.scrollBy(-1)) { "the fixture must actually scroll for this to test anything" }

		repeat(SCROLL_PROBES) {
			assertRowsFollowTheirMeasuredHeights(column, names, heights)
			column.scrollBy(-1)
		}
	}

	@Test
	fun `the settings band under a wrapped row starts below the second line`() {
		val font = StubFont()
		val column = columnOf(font, TALL_VIEWPORT, FITTING_NAME, WRAPPING_NAME)
		column.measure(font)
		val lineHeight = DhenType.lineHeight(font)
		val wrappedTop = FIELD_TOP + HEADER_HEIGHT + ROW_HEIGHT
		val wrappedBottom = wrappedTop + ROW_HEIGHT + lineHeight

		column.toggleSettings(column.moduleAt(1))
		column.measure(font)

		assertSame(column.moduleAt(1), column.rowAt(wrappedBottom - 1))
		assertEquals(ClickGuiShell.NONE, column.settingsRowAt(wrappedBottom - 1))
		assertEquals(wrappedBottom + SETTINGS_PAD, column.bodyTop(1))
		assertEquals(1, column.settingsRowAt(wrappedBottom))
		assertEquals(1, column.settingsRowAt(wrappedBottom + 2 * SETTINGS_PAD + CONTROL_ROW_HEIGHT - 1))
	}

	@Test
	fun `a font change remeasures and reclamps a field before the next hit test`() {
		val font = StubFont()
		val field = ClickGuiColumnField(ClickGuiState(), { VIEWPORT_WIDTH }, { TALL_VIEWPORT + MARGIN })
		val modules = listOf(moduleNamed(FITTING_NAME), moduleNamed(WRAPPING_NAME), moduleNamed("Third"))
		field.build(listOf(Category.DEV), mapOf(Category.DEV to modules))
		field.measure(font)
		field.refilter()
		val settled = font.measurements

		DhenFont.synchronize(false, FontStore.INTER)
		field.invalidateMeasurements(font)

		assertTrue(font.measurements > settled) { "a font change must re-split every wrapped name" }
		val column = field.columnAt(0)
		val lineHeight = DhenType.lineHeight(font)
		assertEquals(HEADER_HEIGHT + BODY_PAD + 3 * ROW_HEIGHT + lineHeight, column.height)
		assertSame(modules[1], column.rowAt(FIELD_TOP + HEADER_HEIGHT + ROW_HEIGHT))
	}

	@Test
	fun `a setting name too wide for its row wraps once and reports the cut it could not avoid`() {
		val font = StubFont()
		val fitting = ToggleControl(BooleanSetting("Only"))
		val wrapping = ToggleControl(BooleanSetting(WRAPPING_SETTING))
		val pathological = ToggleControl(BooleanSetting(PATHOLOGICAL_NAME))

		assertFalse(fitting.measure(font, CONTROLS_WIDTH))
		assertTrue(wrapping.measure(font, CONTROLS_WIDTH))
		assertTrue(pathological.measure(font, CONTROLS_WIDTH))

		val lineHeight = DhenType.lineHeight(font)
		assertEquals(CONTROL_ROW_HEIGHT, fitting.height)
		assertEquals(CONTROL_ROW_HEIGHT + lineHeight, wrapping.height)
		assertEquals(CONTROL_ROW_HEIGHT + lineHeight, pathological.height)
		assertFalse(fitting.labelElided)
		assertFalse(wrapping.labelElided)
		assertTrue(pathological.labelElided)
	}

	@Test
	fun `a row keeps its height while its own value is being edited`() {
		val color = ColorControl(ColorSetting(PILL_SENSITIVE, Color.rgba(255, 128, 0), allowAlpha = false))
		val text = TextControl(StringSetting(TEXT_SENSITIVE, default = "ab", maxLength = 40))

		assertStableHeight(color, focusThenType)
		assertStableHeight(text, focusThenType)
	}

	@Test
	fun `a row keeps its height while its slider is dragged from end to end`() {
		val slider = SliderControl(NumberSetting(PILL_SENSITIVE, default = 0.0, min = 0.0, max = 100.0))

		assertStableHeight(
			slider,
			listOf(
				{ control: SettingControl -> control.press(0, 0, CONTROLS_WIDTH); Unit },
				{ control: SettingControl -> control.drag(CONTROLS_WIDTH, 0, CONTROLS_WIDTH) },
				{ control: SettingControl -> control.drag(CONTROLS_WIDTH / 2, 0, CONTROLS_WIDTH) }
			)
		)
	}

	private fun assertStableHeight(control: SettingControl, steps: List<(SettingControl) -> Unit>) {
		val font = StubFont()
		control.measure(font, CONTROLS_WIDTH)
		val resting = control.height

		for (step in steps) {
			step(control)
			assertFalse(control.measure(font, CONTROLS_WIDTH)) { "the row resized while it was being used" }
			assertEquals(resting, control.height)
		}
	}

	@Test
	fun `a font change remeasures and reclamps the settings panel before the next hit test`() {
		val font = StubFont()
		val panel = ClickGuiPrefsPanel({ VIEWPORT_WIDTH }, { TALL_VIEWPORT })
		panel.measure(font)
		val settled = font.measurements

		DhenFont.synchronize(false, FontStore.INTER)
		panel.invalidateMeasurements(font)

		assertTrue(font.measurements > settled) { "a font change must re-measure every preference card" }
	}

	@Test
	fun `every click gui wrap that is drawn is a wrap that is measured`() {
		val files = SourceScan.files().filter { CLICK_GUI_FILE.matches(it.name) }
		val wrapped = files.filter { WRAP_FIELD.containsMatchIn(it.readText()) }
		assertFalse(wrapped.isEmpty()) { "the scan must see the wrapping sites it guards" }

		val offenders = wrapped.flatMap { file ->
			val source = file.readText()
			WRAP_FIELD.findAll(source).map { it.groupValues[1] }.filterNot { field ->
				Regex("""\b$field\b[^\n]*\.measure\(""").containsMatchIn(source)
			}.map { "${file.path}: $it is drawn but never measured" }.toList()
		}

		assertTrue(offenders.isEmpty()) {
			"A WrappedText draws its cached split, so a wrap nothing measures draws nothing:\n${offenders.joinToString("\n")}"
		}
	}

	private fun assertRowsFollowTheirMeasuredHeights(column: ClickGuiColumn, names: Array<String>, heights: IntArray) {
		val bottom = FIELD_TOP + column.height
		var probe = FIELD_TOP + HEADER_HEIGHT
		val first = column.rowAt(probe) ?: return
		var index = names.indexOfFirst { it == first.name }
		while (probe < bottom && column.rowAt(probe) === column.moduleAt(index)) probe++
		index++
		while (index < names.size && probe < bottom) {
			val end = minOf(probe + heights[index], bottom)
			for (y in probe until end) assertSame(column.moduleAt(index), column.rowAt(y)) { "row $index lost y=$y" }
			probe = end
			index++
		}
	}

	private fun columnOf(font: Font, bottom: Int, vararg names: String): ClickGuiColumn {
		val glyphs = ColumnGlyphs()
		glyphs.measure(font)
		return ClickGuiColumn(
			Category.DEV,
			names.map(::moduleNamed),
			ClickGuiState(),
			mutableSetOf(),
			glyphs,
			ClickGuiTooltip(),
			IntSupplier { bottom }
		)
	}

	private fun moduleNamed(name: String): Module = object : Module(
		name = name,
		category = Category.DEV,
		description = "Fixture module for the legibility tests."
	) {
		init {
			registerSetting(BooleanSetting("Only"))
		}
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
		const val PATHOLOGICAL_NAME = "Asettingormodulenamemuchwiderthanitsbox"
		const val FITTING_NAME = "Short"
		const val WRAPPING_NAME = "Auto Sprint"
		const val WRAPPING_SETTING = "Disable Swing"
		const val PILL_SENSITIVE = "Tinting"
		const val TEXT_SENSITIVE = "Sound"
		val focusThenType: List<(SettingControl) -> Unit> = listOf(
			{ control -> control.press(0, 0, CONTROLS_WIDTH); Unit },
			{ control -> control.charTyped('1'.code); Unit },
			{ control -> control.charTyped('2'.code); Unit }
		)
		const val TALL_VIEWPORT = 600
		const val CLIPPED_VIEWPORT = 140
		const val VIEWPORT_WIDTH = 480
		const val SCROLL_PROBES = 40
		val CLICK_GUI_FILE = Regex("""(?:ClickGui.*|SettingControl.*|ControlBody|ColorControl|SoundControl)\.kt""")
		val WRAP_FIELD = Regex("""val\s+(\w+)\s*=\s*(?:List\(\w+[^)]*\)\s*\{\s*)?DhenType\.wrap\(\)""")
		val FITTED_ASSIGNMENT = Regex("""val\s+(\w+)\s*=\s*([\w.\[\]]+)\.fit\(""")
		val TEXT_DRAW = Regex("""([\w.\[\]]+)\.text\(\s*graphics,\s*font,\s*([^,\n]+)""")
	}
}
