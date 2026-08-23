package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.MenuType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContainerHooksTest {
	private val bus = EventBus()
	private val closed = mutableListOf<ContainerClosedEvent>()

	@BeforeEach
	fun install() {
		ContainerHooks.install(bus)
		bus.subscribe<ContainerClosedEvent> { closed += it }
	}

	@AfterEach
	fun uninstall() {
		ContainerHooks.uninstall()
	}

	@Test
	fun `the server closing the window raises one closed event for the menu it opened`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		receive(ClientboundContainerClosePacket(WINDOW))

		assertEquals(1, closed.size)
		assertEquals(TITLE, closed[0].title)
		assertEquals(WINDOW, closed[0].windowId)
		assertFalse(closed[0].reopening)
	}

	@Test
	fun `the client closing the window raises the same closed event`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		send(ServerboundContainerClosePacket(WINDOW))

		assertEquals(1, closed.size)
		assertEquals(WINDOW, closed[0].windowId)
	}

	@Test
	fun `a close for a window Dhen never tracked raises nothing`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		receive(ClientboundContainerClosePacket(WINDOW + 1))
		send(ServerboundContainerClosePacket(WINDOW + 1))

		assertTrue(closed.isEmpty())
	}

	@Test
	fun `closing twice raises the event only once`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		receive(ClientboundContainerClosePacket(WINDOW))
		receive(ClientboundContainerClosePacket(WINDOW))

		assertEquals(1, closed.size)
	}

	@Test
	fun `a menu replaced by one of the same name reports that it is reopening`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		receive(ClientboundOpenScreenPacket(WINDOW + 1, MenuType.GENERIC_9x6, Component.literal(TITLE.string)))

		assertEquals(1, closed.size)
		assertTrue(closed[0].reopening)
		assertEquals(WINDOW, closed[0].windowId)
	}

	@Test
	fun `a menu replaced by a different one is not a reopening`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		receive(ClientboundOpenScreenPacket(WINDOW + 1, MenuType.GENERIC_9x6, Component.literal("Storage")))

		assertEquals(1, closed.size)
		assertFalse(closed[0].reopening)
	}

	@Test
	fun `a world change forgets the container it was tracking`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		changeWorld()
		receive(ClientboundContainerClosePacket(WINDOW))

		assertTrue(closed.isEmpty())
	}

	@Test
	fun `a window id reused after a world change is not matched against the stale menu`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		changeWorld()
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))

		assertTrue(closed.isEmpty())
	}

	@Test
	fun `a container opened after a world change is tracked again`() {
		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))
		changeWorld()

		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))
		receive(ClientboundContainerClosePacket(WINDOW))

		assertEquals(1, closed.size)
		assertEquals(WINDOW, closed[0].windowId)
		assertFalse(closed[0].reopening)
	}

	@Test
	fun `uninstalling drops the packet subscriptions`() {
		ContainerHooks.uninstall()

		receive(ClientboundOpenScreenPacket(WINDOW, MenuType.GENERIC_9x6, TITLE))
		receive(ClientboundContainerClosePacket(WINDOW))

		assertTrue(closed.isEmpty())
		assertFalse(ContainerHooks.active())
		assertFalse(bus.type<WorldChangeEvent>().hasSubscribers)
	}

	private fun changeWorld() {
		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))
	}

	private fun receive(packet: Packet<*>) {
		val event = PacketReceiveEvent.Post()
		event.packet = packet
		bus.type<PacketReceiveEvent.Post>().dispatch(event)
	}

	private fun send(packet: Packet<*>) {
		val event = PacketSendEvent()
		event.packet = packet
		bus.type<PacketSendEvent>().dispatch(event)
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()

		private const val WINDOW = 7
		private val TITLE: Component = Component.literal("SkyBlock Menu")
	}
}
