package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.absent
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.uninitialized
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
	fun `a disconnect releases the container the shared slot event was holding`() {
		var seen: SlotRenderEvent? = null
		bus.subscribe<SlotRenderEvent.Pre> { seen = it }
		ScreenHooks.beforeSlotRender(uninitialized<ContainerScreen>(), uninitialized<GuiGraphicsExtractor>(), slot())

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertThrows(NullPointerException::class.java) { seen!!.slot }
		assertThrows(NullPointerException::class.java) { seen!!.screen }
		assertThrows(NullPointerException::class.java) { seen!!.graphics }
	}

	@Test
	fun `joining a world leaves the container the shared slot event is holding alone`() {
		val slot = slot()
		var seen: SlotRenderEvent? = null
		bus.subscribe<SlotRenderEvent.Pre> { seen = it }
		ScreenHooks.beforeSlotRender(uninitialized<ContainerScreen>(), uninitialized<GuiGraphicsExtractor>(), slot)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))

		assertSame(slot, seen?.slot)
	}

	@Test
	fun `a disconnect releases the item the shared tooltip event was holding`() {
		var seen: TooltipEvent? = null
		bus.subscribe<TooltipEvent> { seen = it }
		ScreenHooks.beforeTooltip(
			uninitialized<ContainerScreen>(),
			uninitialized<GuiGraphicsExtractor>(),
			slot(),
			ItemStack(Items.DIAMOND),
			listOf(Component.literal("Diamond")),
			0,
			0
		)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertThrows(NullPointerException::class.java) { seen!!.stack }
		assertThrows(NullPointerException::class.java) { seen!!.screen }
		assertTrue(seen!!.lines.isEmpty())
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
		fun bootstrap() = bootstrapMinecraft()
	}
}

private class FakeScreen(title: String) : Screen(absent(), absent(), Component.literal(title))
