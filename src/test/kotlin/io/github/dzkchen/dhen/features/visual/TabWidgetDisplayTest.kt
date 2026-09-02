package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabWidgetDisplayTest {
	@AfterEach
	fun reset() {
		if (TabWidgetDisplay.enabled) TabWidgetDisplay.setEnabled(false)
		for (setting in TabWidgetDisplay.settings) setting.reset()
		TabWidgetState.reset()
	}

	@Test
	fun `the module declares one catalogue and one readout per widget group`() {
		assertEquals(listOf("Widgets"), TabWidgetDisplay.settings.map { it.name })
		assertEquals(Category.VISUAL, TabWidgetDisplay.category)
		assertEquals(33, WidgetGroup.entries.size)
		assertEquals(WidgetGroup.labels, TabWidgetDisplay.hudElements.map { it.name })
		assertEquals(WidgetGroup.labels, TabWidgetDisplay.widgetsSetting.options)
		assertEquals(emptyList<String>(), TabWidgetDisplay.widgetsSetting.value)
	}

	@Test
	fun `the group names and their widgets follow the source table`() {
		assertEquals("Bank and Interest", WidgetGroup.COINS.label)
		assertEquals(listOf(TabWidget.BANK, TabWidget.INTEREST), WidgetGroup.COINS.widgets.toList())
		assertEquals("Fire Sale", WidgetGroup.FIRE_SALE.label)
		assertEquals("Shen's Auction inside the Rift", WidgetGroup.SHEN_RIFT.label)
		assertEquals(
			listOf(
				TabWidget.PROFILE,
				TabWidget.SB_LEVEL,
				TabWidget.BANK,
				TabWidget.INTEREST,
				TabWidget.SOULFLOW,
				TabWidget.FAIRY_SOULS
			),
			WidgetGroup.FULL_PROFILE_WIDGET.widgets.toList()
		)
	}

	@Test
	fun `a selected group draws the widget lines with its header first`() {
		select(WidgetGroup.SOULFLOW)
		read(TabWidget.SOULFLOW, "§bSoulflow: §3234", " §7Overflow: §3128")
		val element = elementFor(WidgetGroup.SOULFLOW)

		assertTrue(element.contentAvailable(editing = false))
		assertEquals(2, element.shownCount(editing = false))
		assertEquals("§bSoulflow: §3234", element.shownLine(0, editing = false))
		assertEquals(" §7Overflow: §3128", element.shownLine(1, editing = false))
	}

	@Test
	fun `a group joins its widgets in the declared order and skips the absent one`() {
		select(WidgetGroup.PEST_TRAPS)
		read(TabWidget.PEST_TRAPS, "§ePest Traps: §a2§7/4")
		read(TabWidget.NO_BAIT, "§eNo Bait: §c#3")
		val element = elementFor(WidgetGroup.PEST_TRAPS)

		assertEquals(2, element.shownCount(editing = false))
		assertEquals("§ePest Traps: §a2§7/4", element.shownLine(0, editing = false))
		assertEquals("§eNo Bait: §c#3", element.shownLine(1, editing = false))
	}

	@Test
	fun `an unselected group draws nothing and stays out of the editor`() {
		read(TabWidget.SOULFLOW, "§bSoulflow: §3234")
		val element = elementFor(WidgetGroup.SOULFLOW)

		assertFalse(element.listed)
		assertFalse(element.contentAvailable(editing = false))
		assertFalse(element.contentAvailable(editing = true))
	}

	@Test
	fun `a selected group the tab list is not showing appears only while the editor is open`() {
		select(WidgetGroup.DRAGON)
		val element = elementFor(WidgetGroup.DRAGON)

		assertTrue(element.listed)
		assertFalse(element.contentAvailable(editing = false))
		assertTrue(element.contentAvailable(editing = true))
		assertEquals(1, element.shownCount(editing = true))
		assertEquals("Dragon Fight Info", element.shownLine(0, editing = true))
	}

	@Test
	fun `a widget leaving the tab list empties its readout`() {
		select(WidgetGroup.TIMERS)
		read(TabWidget.TIMERS, "§eTimers:", " §7Cake: §a3h")
		val element = elementFor(WidgetGroup.TIMERS)
		assertEquals(2, element.shownCount(editing = false))

		read(TabWidget.TIMERS)

		assertEquals(0, element.shownCount(editing = false))
		assertFalse(element.contentAvailable(editing = false))
	}

	@Test
	fun `the widget feed latching off empties every readout without an event`() {
		select(WidgetGroup.TIMERS)
		select(WidgetGroup.PROFILE)
		read(TabWidget.TIMERS, "§eTimers:")
		read(TabWidget.PROFILE, "§eProfile: §aApple")
		assertEquals(1, elementFor(WidgetGroup.TIMERS).shownCount(editing = false))

		TabWidgetState.reset()

		assertEquals(0, elementFor(WidgetGroup.TIMERS).shownCount(editing = false))
		assertEquals(0, elementFor(WidgetGroup.PROFILE).shownCount(editing = false))
	}

	private fun select(group: WidgetGroup) = TabWidgetDisplay.widgetsSetting.toggle(group.label)

	private fun elementFor(group: WidgetGroup): TabWidgetElement = TabWidgetDisplay.elements[group.ordinal]

	private fun read(widget: TabWidget, vararg lines: String) =
		TabWidgetState.read(widget, lines.toList(), lines.map { it.replace(COLOR, "") })

	private companion object {
		val COLOR = Regex("§.")
	}
}
