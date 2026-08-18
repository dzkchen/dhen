package io.github.dzkchen.dhen.event

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		ScreenHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		ScreenHooks.uninstall()
	}

	@Test
	fun `swapping screens closes the old one before it opens the new one`() {
		val seen = mutableListOf<String>()
		bus.subscribe<GuiCloseEvent> { seen += "close ${it.screen.title.string}" }
		bus.subscribe<GuiOpenEvent> { seen += "open ${it.screen.title.string}" }

		ScreenHooks.screenChanged(FakeScreen("leaving"), FakeScreen("arriving"))

		assertEquals(listOf("close leaving", "open arriving"), seen)
	}

	@Test
	fun `returning to the world closes the screen without opening another`() {
		val seen = mutableListOf<String>()
		bus.subscribe<GuiCloseEvent> { seen += "close" }
		bus.subscribe<GuiOpenEvent> { seen += "open" }

		ScreenHooks.screenChanged(FakeScreen("leaving"), null)

		assertEquals(listOf("close"), seen)
	}

	@Test
	fun `opening the first screen raises no close for a screen that was never there`() {
		val seen = mutableListOf<String>()
		bus.subscribe<GuiCloseEvent> { seen += "close" }
		bus.subscribe<GuiOpenEvent> { seen += "open" }

		ScreenHooks.screenChanged(null, FakeScreen("arriving"))

		assertEquals(listOf("open"), seen)
	}

	@Test
	fun `a close carries the screen that is going away`() {
		val leaving = FakeScreen("leaving")
		var closed: Screen? = null
		bus.subscribe<GuiCloseEvent> { closed = it.screen }

		ScreenHooks.screenChanged(leaving, FakeScreen("arriving"))

		assertSame(leaving, closed)
	}

	@Test
	fun `a handler that throws turns the hooks off instead of failing the screen change`() {
		bus.subscribe<GuiOpenEvent> { throw IllegalStateException("boom") }

		ScreenHooks.screenChanged(null, FakeScreen("arriving"))

		assertFalse(ScreenHooks.active())
	}

	@Test
	fun `screens are ignored while the hooks are not installed`() {
		ScreenHooks.uninstall()
		var seen = false
		bus.subscribe<GuiOpenEvent> { seen = true }
		bus.subscribe<GuiCloseEvent> { seen = true }

		ScreenHooks.screenChanged(FakeScreen("leaving"), FakeScreen("arriving"))

		assertFalse(seen)
	}
}

private class FakeScreen(title: String) : Screen(absent(), absent(), Component.literal(title))

@Suppress("UNCHECKED_CAST")
private fun <T> absent(): T = null as T
