package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldHooks
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class TablistHooksTest {
	private val bus = EventBus()
	private val updates = mutableListOf<TablistUpdateEvent>()
	private var names: List<Component> = emptyList()

	@BeforeEach
	fun install() {
		TablistHooks.install(bus) { names }
		bus.subscribe<TablistUpdateEvent> { updates += it }
	}

	@AfterEach
	fun uninstall() {
		TablistHooks.uninstall()
	}

	@Test
	fun `every displayed name is read once, styled and stripped`() {
		names = listOf(
			Component.literal("Info").withStyle(ChatFormatting.GREEN),
			Component.literal(" Purse: 1,234")
		)
		TablistHooks.refresh()

		assertEquals(listOf("§aInfo", " Purse: 1,234"), TablistState.lines)
		assertEquals(listOf("Info", " Purse: 1,234"), TablistState.stripped)
		assertEquals(listOf(emptyList<String>()), updates.map { it.previous })
	}

	@Test
	fun `a tab list that did not change publishes nothing the second time`() {
		names = listOf(Component.literal("Info"))
		TablistHooks.refresh()
		TablistHooks.refresh()

		assertEquals(1, updates.size)
	}

	@Test
	fun `a player-info packet is read on the next tick, not in the handler`() {
		names = listOf(Component.literal("Info"))
		bus.type<PacketReceiveEvent.Post>().dispatch(
			PacketReceiveEvent.Post().also { it.packet = ClientboundPlayerInfoRemovePacket(listOf(UUID.randomUUID())) }
		)

		assertTrue(updates.isEmpty())

		bus.type<ClientTickEvent.Start>().dispatch(ClientTickEvent.Start)

		assertEquals(1, updates.size)
	}

	@Test
	fun `the header and footer come off the tab list packet`() {
		bus.type<PacketReceiveEvent.Post>().dispatch(
			PacketReceiveEvent.Post().also {
				it.packet = ClientboundTabListPacket(
					Component.literal("Hypixel").withStyle(ChatFormatting.YELLOW),
					Component.literal("ranks.hypixel.net")
				)
			}
		)
		bus.type<ClientTickEvent.Start>().dispatch(ClientTickEvent.Start)

		assertEquals("§eHypixel", TablistState.header)
		assertEquals("Hypixel", TablistState.strippedHeader)
		assertEquals("ranks.hypixel.net", TablistState.strippedFooter)
		assertEquals(1, updates.size)
	}

	@Test
	fun `a footer change alone still tells the consumers`() {
		names = listOf(Component.literal("Info"))
		TablistHooks.refresh()
		framed("ranks.hypixel.net")
		framed("store.hypixel.net")

		assertEquals(3, updates.size)
		assertEquals("store.hypixel.net", TablistState.footer)
	}

	@Test
	fun `a resent footer is not a change`() {
		framed("ranks.hypixel.net")
		framed("ranks.hypixel.net")

		assertEquals(1, updates.size)
	}

	@Test
	fun `joining a world forgets the tab list it read on the last one`() {
		WorldHooks.install(bus)
		try {
			names = listOf(Component.literal("Info"))
			TablistHooks.refresh()
			framed("ranks.hypixel.net")
			WorldHooks.worldChanged(WorldChange.JOIN)
		} finally {
			WorldHooks.uninstall()
		}

		assertTrue(TablistState.lines.isEmpty())
		assertEquals("", TablistState.footer)
	}

	@Test
	fun `changing islands forgets the tab list it read on the last one`() {
		names = listOf(Component.literal("Info"))
		TablistHooks.refresh()
		framed("ranks.hypixel.net")

		bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.CATACOMBS, Island.HUB))

		assertTrue(TablistState.lines.isEmpty())
		assertEquals("", TablistState.footer)
	}

	@Test
	fun `an uninstalled feed reads nothing`() {
		TablistHooks.uninstall()
		names = listOf(Component.literal("Info"))
		TablistHooks.refresh()

		assertFalse(TablistHooks.active())
		assertTrue(TablistState.lines.isEmpty())
		assertTrue(updates.isEmpty())
	}

	private fun framed(footer: String) {
		bus.type<PacketReceiveEvent.Post>().dispatch(
			PacketReceiveEvent.Post().also {
				it.packet = ClientboundTabListPacket(Component.empty(), Component.literal(footer))
			}
		)
		bus.type<ClientTickEvent.Start>().dispatch(ClientTickEvent.Start)
	}
}
