package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TabWidgetHooksTest {
	private val bus = EventBus()
	private val updates = mutableListOf<TabWidgetUpdateEvent>()
	private var names: List<Component> = emptyList()
	private var inSkyBlock = true

	@BeforeEach
	fun install() {
		TablistHooks.install(bus) { names }
		TabWidgetHooks.install(bus) { inSkyBlock }
		bus.subscribe<TabWidgetUpdateEvent> { updates += it }
	}

	@AfterEach
	fun uninstall() {
		TabWidgetHooks.uninstall()
		TablistHooks.uninstall()
	}

	@Test
	fun `a widget takes its header line and the lines under it, blanks dropped`() {
		read(hub())

		assertEquals(listOf("Players (42)", "Alice", "Bob"), TabWidgetState.lines(TabWidget.PLAYER_LIST))
		assertEquals(listOf("Pet:", " [Lvl 100] Tiger"), TabWidgetState.lines(TabWidget.PET))
		assertEquals(listOf(" Server: mini1A"), TabWidgetState.lines(TabWidget.SERVER))
	}

	@Test
	fun `a header with nothing under it is still an active widget`() {
		read(hub())

		assertTrue(TabWidgetState.active(TabWidget.INFO))
		assertEquals(listOf("Info"), TabWidgetState.lines(TabWidget.INFO))
	}

	@Test
	fun `lines before the first header belong to no widget`() {
		read(listOf(Component.literal("Hypixel Network"), Component.literal("Info")))

		assertEquals(listOf("Info"), TabWidgetState.lines(TabWidget.INFO))
		assertTrue(TabWidget.entries.none { TabWidgetState.lines(it).contains("Hypixel Network") })
	}

	@Test
	fun `a repeated column header keeps one widget running across the break`() {
		read(
			listOf(
				Component.literal("Players (42)"),
				Component.literal("Alice"),
				Component.literal("Players (42)"),
				Component.literal("Bob")
			)
		)

		assertEquals(listOf("Players (42)", "Alice", "Bob"), TabWidgetState.lines(TabWidget.PLAYER_LIST))
	}

	@Test
	fun `a repeat of a widget that is not the running one takes no lines with it`() {
		read(
			listOf(
				Component.literal("Info"),
				Component.literal("Pet:"),
				Component.literal(" [Lvl 100] Tiger"),
				Component.literal("Info"),
				Component.literal(" leftover")
			)
		)

		assertEquals(listOf("Pet:", " [Lvl 100] Tiger"), TabWidgetState.lines(TabWidget.PET))
		assertEquals(listOf("Info"), TabWidgetState.lines(TabWidget.INFO))
	}

	@Test
	fun `the styled lines are kept beside the stripped ones the headers matched on`() {
		read(
			listOf(
				Component.literal("Info"),
				Component.literal(" Area: ").append(Component.literal("Hub").withStyle(ChatFormatting.GREEN))
			)
		)

		assertEquals(listOf(" Area: §aHub"), TabWidgetState.lines(TabWidget.AREA))
		assertEquals(listOf(" Area: Hub"), TabWidgetState.stripped(TabWidget.AREA))
		assertEquals("Hub", TabWidgetState.capture(TabWidget.AREA, "island"))
	}

	@Test
	fun `only the widget whose lines moved is told`() {
		read(hub())
		updates.clear()
		read(hub(area = "Village"))

		assertEquals(listOf(TabWidget.AREA), updates.map { it.widget })
		assertEquals(listOf(" Area: Village"), updates.single().lines)
		assertEquals(listOf(" Area: Hub"), updates.single().previous)
	}

	@Test
	fun `a tab list that did not change tells nobody twice`() {
		read(hub())
		val told = updates.size
		read(hub())

		assertEquals(told, updates.size)
	}

	@Test
	fun `a widget that leaves the tab list is cleared once`() {
		read(hub())
		updates.clear()
		read(hub(pet = false))
		read(hub(pet = false))

		assertEquals(listOf(TabWidget.PET), updates.map { it.widget })
		assertTrue(updates.single().lines.isEmpty())
		assertFalse(TabWidgetState.active(TabWidget.PET))
	}

	@Test
	fun `leaving SkyBlock clears every widget`() {
		read(hub())
		inSkyBlock = false
		read(hub(area = "Village"))

		assertTrue(TabWidget.entries.none { TabWidgetState.active(it) })
	}

	@Test
	fun `an uninstalled feed groups nothing`() {
		TabWidgetHooks.uninstall()
		read(hub())

		assertFalse(TabWidgetHooks.active())
		assertTrue(TabWidget.entries.none { TabWidgetState.active(it) })
		assertTrue(updates.isEmpty())
	}

	@Test
	fun `the bank widget splits the co-op total from the personal half`() {
		read(listOf(Component.literal("Info"), Component.literal(" Bank: 1,234,567 / 500,000")))

		assertEquals("1,234,567", TabWidgetState.capture(TabWidget.BANK, "amount"))
		assertEquals("500,000", TabWidgetState.capture(TabWidget.BANK, "personal"))
	}

	@Test
	fun `a solo bank line captures the amount and no personal half`() {
		read(listOf(Component.literal("Info"), Component.literal(" Bank: 1,234,567")))

		assertEquals("1,234,567", TabWidgetState.capture(TabWidget.BANK, "amount"))
		assertNull(TabWidgetState.capture(TabWidget.BANK, "personal"))
	}

	@Test
	fun `the interest widget keeps the timer out of the bracketed amount`() {
		read(listOf(Component.literal("Info"), Component.literal(" Interest: 3h 4m (123,456)")))

		assertEquals("3h 4m", TabWidgetState.capture(TabWidget.INTEREST, "time"))
		assertEquals("123,456", TabWidgetState.capture(TabWidget.INTEREST, "amount"))
	}

	@Test
	fun `an interest line without a bracketed amount still captures its timer`() {
		read(listOf(Component.literal("Info"), Component.literal(" Interest: 7h 12m")))

		assertEquals("7h 12m", TabWidgetState.capture(TabWidget.INTEREST, "time"))
		assertNull(TabWidgetState.capture(TabWidget.INTEREST, "amount"))
	}

	private fun read(lines: List<Component>) {
		names = lines
		TablistHooks.refresh()
	}

	private fun hub(area: String = "Hub", pet: Boolean = true): List<Component> = buildList {
		add(Component.literal("Players (42)"))
		add(Component.literal("Alice"))
		add(Component.literal("Bob"))
		add(Component.empty())
		add(Component.literal("Info"))
		add(Component.literal(" Area: $area"))
		add(Component.literal(" Server: mini1A"))
		if (pet) {
			add(Component.literal("Pet:"))
			add(Component.literal(" [Lvl 100] Tiger"))
		}
		add(Component.empty())
	}
}
