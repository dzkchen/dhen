package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContainerClicksTest {
	@Test
	fun `all five sub-toggles reach the module, so the GUI can show and persist them`() {
		assertEquals(Category.INVENTORY, ContainerClicks.category)
		assertEquals(
			listOf(
				"Middle-Click Fix",
				"Huntrap Misclick Prevention",
				"Shift-Click Equipment",
				"Shift-Click Brewing",
				"Shift-Click NPC Sell"
			),
			ContainerClicks.settings.map { it.name }
		)
	}

	@Test
	fun `only the middle-click fix is on by default`() {
		val defaults = ContainerClicks.settings.filterIsInstance<BooleanSetting>().associate { it.name to it.default }
		assertTrue(defaults.getValue("Middle-Click Fix"))
		assertFalse(defaults.getValue("Huntrap Misclick Prevention"))
		assertFalse(defaults.getValue("Shift-Click Equipment"))
		assertFalse(defaults.getValue("Shift-Click Brewing"))
		assertFalse(defaults.getValue("Shift-Click NPC Sell"))
	}

	@Test
	fun `the middle-click fix stays off while the module is disabled`() {
		assertFalse(ContainerClicks.enabled)
		assertFalse(ContainerClicks.fixesMiddleClick())
	}
}
