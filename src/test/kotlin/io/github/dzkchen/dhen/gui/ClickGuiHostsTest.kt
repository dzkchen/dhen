package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.IntSupplier

class ClickGuiHostsTest {
	@Test
	fun `a settings tab card gives an expanded control exactly the room it asks for`() {
		val selector = SelectorSetting("Mode", "A", OPTIONS)
		val card = PrefCard(PrefSection("Appearance", listOf(BooleanSetting("Above"), selector, BooleanSetting("Below"))))
		val closed = card.height
		assertEquals(HEADER_HEIGHT + 2 * SECTION_PAD + 3 * CONTROL_ROW_HEIGHT, closed)

		val listed = card.body.at(1)
		listed.press(0, 0, PANEL_CONTROLS_WIDTH)

		assertEquals(closed + listed.height - CONTROL_ROW_HEIGHT, card.height)
	}

	@Test
	fun `a settings tab card grows by one line for the one control whose name wraps`() {
		val font = StubFont()
		val card = PrefCard(PrefSection("Appearance", listOf(BooleanSetting("Above"), BooleanSetting(WRAPPING_NAME))))
		val closed = card.height

		assertTrue(card.measure(font))

		assertEquals(closed + DhenType.lineHeight(font), card.height)
	}

	@Test
	fun `a click under an open list in a column lands on the control below it`() {
		val selector = SelectorSetting("Mode", "A", OPTIONS)
		val module = moduleOf(selector, BooleanSetting("Below"))
		val column = columnOf(module)
		column.toggleSettings(module)
		val body = column.bodyOf(0)
		val closed = column.height

		val listed = body.at(0)
		listed.press(0, 0, CONTROLS_WIDTH)
		val grown = listed.height

		assertEquals(closed + grown - CONTROL_ROW_HEIGHT, column.height)
		assertEquals(FIELD_TOP + HEADER_HEIGHT + ROW_HEIGHT + SETTINGS_PAD, column.bodyTop(0))

		val hitY = column.bodyTop(0) + grown
		assertEquals(0, column.settingsRowAt(hitY))
		assertSame(
			body.at(1),
			body.hit(ControlHit(), column, 0, column.bodyTop(0), CONTROLS_WIDTH, hitY)
		)
	}

	@Test
	fun `a module with no settings has no expansion`() {
		val module = moduleOf()
		val column = columnOf(module)
		val closed = column.height

		column.toggleSettings(module)

		assertEquals(closed, column.height)
		assertEquals(ClickGuiShell.NONE, column.settingsRowAt(FIELD_TOP + HEADER_HEIGHT + ROW_HEIGHT))
		assertFalse(column.chevronContains(COLUMN_WIDTH - 1, module))
	}

	@Test
	fun `a module with a setting keeps its chevron and expands by one control row`() {
		val module = moduleOf(BooleanSetting("Only"))
		val column = columnOf(module)
		val closed = column.height

		assertTrue(column.chevronContains(COLUMN_WIDTH - 1, module))

		column.toggleSettings(module)

		assertEquals(closed + 2 * SETTINGS_PAD + CONTROL_ROW_HEIGHT, column.height)
	}

	@Test
	fun `a module whose every setting is hidden keeps its chevron but opens no band`() {
		val module = moduleOf(BooleanSetting("Gated").withDependency { false })
		val column = columnOf(module)
		val closed = column.height

		assertTrue(column.chevronContains(COLUMN_WIDTH - 1, module))

		column.toggleSettings(module)

		assertEquals(closed, column.height)
		assertEquals(ClickGuiShell.NONE, column.settingsRowAt(FIELD_TOP + HEADER_HEIGHT + ROW_HEIGHT))
	}

	private fun columnOf(module: Module): ClickGuiColumn = ClickGuiColumn(
		Category.DEV,
		listOf(module),
		ClickGuiState(),
		mutableSetOf(),
		ColumnGlyphs(),
		ClickGuiTooltip(),
		IntSupplier { VIEWPORT_HEIGHT }
	)

	private fun moduleOf(vararg settings: Setting<*>): Module = object : Module(
		name = "Host",
		category = Category.DEV,
		description = "Fixture hosting the settings under test."
	) {
		init {
			for (setting in settings) registerSetting(setting)
		}
	}

	private companion object {
		const val VIEWPORT_HEIGHT = 600
		const val WRAPPING_NAME = "Reduce Motion And Transparency"
		val OPTIONS = listOf("A", "B", "C")
	}
}
