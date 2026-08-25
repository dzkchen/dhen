package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.absent
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.uninitialized
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
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

		val arriving = FakeScreen("arriving")

		assertSame(arriving, ScreenHooks.screenChanged(FakeScreen("leaving"), arriving))
		assertEquals(listOf("close leaving", "open arriving"), seen)
	}

	@Test
	fun `returning to the world closes the screen without opening another`() {
		val seen = mutableListOf<String>()
		bus.subscribe<GuiCloseEvent> { seen += "close" }
		bus.subscribe<GuiOpenEvent> { seen += "open" }

		assertNull(ScreenHooks.screenChanged(FakeScreen("leaving"), null))
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
	fun `a screen opened from inside an open handler raises one close, not two, and is handed straight back`() {
		val nested = FakeScreen("nested")
		val seen = mutableListOf<String>()
		var reply: Screen? = null
		bus.subscribe<GuiCloseEvent> { seen += "close ${it.screen.title.string}" }
		bus.subscribe<GuiOpenEvent> {
			seen += "open ${it.screen.title.string}"
			if (it.screen.title.string == "vanilla") {
				reply = ScreenHooks.screenChanged(FakeScreen("leaving"), nested)
			}
		}

		ScreenHooks.screenChanged(FakeScreen("leaving"), FakeScreen("vanilla"))

		assertEquals(listOf("close leaving", "open vanilla"), seen)
		assertSame(nested, reply)
	}

	@Test
	fun `a handler that replaces the screen is the one the player ends up looking at`() {
		val ours = FakeScreen("ours")
		bus.subscribe<GuiOpenEvent> { if (it.screen.title.string == "vanilla") it.screen = ours }

		assertSame(ours, ScreenHooks.screenChanged(FakeScreen("leaving"), FakeScreen("vanilla")))
	}

	@Test
	fun `a replaced screen is the one later handlers are told about`() {
		val ours = FakeScreen("ours")
		val seen = mutableListOf<String>()
		bus.subscribe<GuiOpenEvent> { if (it.screen.title.string == "vanilla") it.screen = ours }
		bus.subscribe<GuiOpenEvent> { seen += it.screen.title.string }

		ScreenHooks.screenChanged(null, FakeScreen("vanilla"))

		assertEquals(listOf("ours"), seen)
	}

	@Test
	fun `the death screen vanilla invents when the player dies raises an open`() {
		val death = uninitialized<DeathScreen>()
		val seen = mutableListOf<Screen>()
		bus.subscribe<GuiOpenEvent> { seen += it.screen }

		ScreenHooks.screenChanged(FakeScreen("playing"), null)

		assertSame(death, ScreenHooks.screenSynthesised(death))
		assertEquals(listOf<Screen>(death), seen)
	}

	@Test
	fun `the title screen vanilla invents when a menu closes with no world raises an open`() {
		val title = uninitialized<TitleScreen>()
		val seen = mutableListOf<Screen>()
		bus.subscribe<GuiOpenEvent> { seen += it.screen }

		ScreenHooks.screenChanged(FakeScreen("options"), null)

		assertSame(title, ScreenHooks.screenSynthesised(title))
		assertEquals(listOf<Screen>(title), seen)
	}

	@Test
	fun `a null argument raises the close and exactly one open, never two`() {
		val seen = mutableListOf<String>()
		bus.subscribe<GuiCloseEvent> { seen += "close ${it.screen.title.string}" }
		bus.subscribe<GuiOpenEvent> { seen += "open" }

		assertNull(ScreenHooks.screenChanged(FakeScreen("playing"), null))
		ScreenHooks.screenSynthesised(uninitialized<DeathScreen>())

		assertEquals(listOf("close playing", "open"), seen)
	}

	@Test
	fun `a handler replaces the screen vanilla invented`() {
		val ours = FakeScreen("ours")
		bus.subscribe<GuiOpenEvent> { if (it.screen is DeathScreen) it.screen = ours }

		assertSame(ours, ScreenHooks.screenSynthesised(uninitialized<DeathScreen>()))
	}

	@Test
	fun `a chat screen vanilla had nothing to restore raises no open`() {
		var seen = false
		bus.subscribe<GuiOpenEvent> { seen = true }

		assertNull(ScreenHooks.screenSynthesised(null))
		assertFalse(seen)
	}

	@Test
	fun `a screen invented inside an open handler raises one open, not two`() {
		val nested = uninitialized<TitleScreen>()
		val seen = mutableListOf<Screen>()
		var reply: Screen? = null
		bus.subscribe<GuiOpenEvent> {
			seen += it.screen
			if (it.screen !== nested) reply = ScreenHooks.screenSynthesised(nested)
		}

		val arriving = FakeScreen("arriving")
		ScreenHooks.screenChanged(null, arriving)

		assertEquals(listOf<Screen>(arriving), seen)
		assertSame(nested, reply)
	}

	@Test
	fun `a handler that throws on an invented screen turns the hooks off instead of failing the screen change`() {
		val death = uninitialized<DeathScreen>()
		bus.subscribe<GuiOpenEvent> { throw IllegalStateException("boom") }

		assertSame(death, ScreenHooks.screenSynthesised(death))
		assertFalse(ScreenHooks.active())
	}

	@Test
	fun `invented screens are ignored while the hooks are not installed`() {
		ScreenHooks.uninstall()
		var seen = false
		bus.subscribe<GuiOpenEvent> { seen = true }

		val death = uninitialized<DeathScreen>()

		assertSame(death, ScreenHooks.screenSynthesised(death))
		assertFalse(seen)
	}

	@Test
	fun `a slot event releases its container when dispatch returns`() {
		var seen: SlotRenderEvent? = null
		bus.subscribe<SlotRenderEvent.Pre> { seen = it }
		ScreenHooks.beforeSlotRender(uninitialized<ContainerScreen>(), uninitialized<GuiGraphicsExtractor>(), slot())

		assertThrows(NullPointerException::class.java) { seen!!.slot }
		assertThrows(NullPointerException::class.java) { seen!!.screen }
		assertThrows(NullPointerException::class.java) { seen!!.graphics }
	}

	@Test
	fun `a slot event exposes its container while dispatch is active`() {
		val slot = slot()
		var seen: Slot? = null
		bus.subscribe<SlotRenderEvent.Pre> { seen = it.slot }
		ScreenHooks.beforeSlotRender(uninitialized<ContainerScreen>(), uninitialized<GuiGraphicsExtractor>(), slot)

		assertSame(slot, seen)
	}

	@Test
	fun `releasing a tooltip clears the item it was holding`() {
		var seen: TooltipEvent? = null
		bus.subscribe<TooltipEvent> { seen = it }
		val event = ScreenHooks.beforeTooltip(
			uninitialized<ContainerScreen>(),
			uninitialized<GuiGraphicsExtractor>(),
			slot(),
			ItemFixture.vanilla(),
			listOf(Component.literal("Diamond")),
			0,
			0
		)!!

		assertSame(event, seen)
		assertEquals("Diamond", event.lines.single().string)
		ScreenHooks.releaseTooltip(event)

		assertThrows(NullPointerException::class.java) { seen!!.stack }
		assertThrows(NullPointerException::class.java) { seen!!.screen }
		assertTrue(seen!!.lines.isEmpty())
	}

	@Test
	fun `a screen render event clears its screen and graphics when dispatch returns`() {
		var seen: ScreenRenderEvent.Pre? = null
		bus.subscribe<ScreenRenderEvent.Pre> { seen = it }

		ScreenHooks.beforeScreenRender(
			FakeScreen("render"),
			uninitialized<GuiGraphicsExtractor>(),
			0,
			0
		)

		assertThrows(NullPointerException::class.java) { seen!!.screen }
		assertThrows(NullPointerException::class.java) { seen!!.graphics }
	}

	@Test
	fun `a throwing tooltip handler hands back no tooltip`() {
		bus.subscribe<TooltipEvent> { error("boom") }

		val event = ScreenHooks.beforeTooltip(
			uninitialized<ContainerScreen>(),
			uninitialized<GuiGraphicsExtractor>(),
			slot(),
			ItemFixture.vanilla(),
			listOf(Component.literal("Diamond")),
			0,
			0
		)

		assertNull(event)
		assertFalse(ScreenHooks.active())
	}

	@Test
	fun `a handler that throws turns the hooks off instead of failing the screen change`() {
		val arriving = FakeScreen("arriving")
		bus.subscribe<GuiOpenEvent> { throw IllegalStateException("boom") }

		assertSame(arriving, ScreenHooks.screenChanged(null, arriving))
		assertFalse(ScreenHooks.active())
	}

	@Test
	fun `screens are ignored while the hooks are not installed`() {
		ScreenHooks.uninstall()
		var seen = false
		bus.subscribe<GuiOpenEvent> { seen = true }
		bus.subscribe<GuiCloseEvent> { seen = true }

		val arriving = FakeScreen("arriving")

		assertSame(arriving, ScreenHooks.screenChanged(FakeScreen("leaving"), arriving))
		assertFalse(seen)
		assertFalse(bus.type<WorldChangeEvent>().hasSubscribers)
	}

	private fun slot(): Slot = Slot(SimpleContainer(1), 0, 0, 0)

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}

private class FakeScreen(title: String) : Screen(absent(), absent(), Component.literal(title))
