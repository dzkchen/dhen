package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.ServerClock
import io.github.dzkchen.dhen.util.TickClock
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TickHooksTest {
	private val bus = EventBus()
	private var nanos = SECOND
	private var ticks = 0

	@BeforeEach
	fun install() {
		TickHooks.install(bus) { nanos }
		bus.subscribe<ServerTickEvent> { ticks++ }
	}

	@AfterEach
	fun uninstall() {
		TickHooks.uninstall()
		ServerClock.reset()
	}

	@Test
	fun `a ping with a non-zero id publishes one server tick`() {
		val before = TickClock.serverTick

		receive(ClientboundPingPacket(7))

		assertEquals(1, ticks)
		assertEquals(before + 1, TickClock.serverTick)
		assertEquals(nanos, ServerClock.lastTickNanos)
	}

	@Test
	fun `the vanilla zero-id ping is not a server tick`() {
		receive(ClientboundPingPacket(0))

		assertEquals(0, ticks)
	}

	@Test
	fun `the client ticks since the last server tick count up and reset on the next one`() {
		receive(ClientboundPingPacket(1))
		assertEquals(0L, ServerClock.clientTicksSinceServerTick)

		repeat(3) { TickHooks.clientTicked() }
		assertEquals(3L, ServerClock.clientTicksSinceServerTick)

		receive(ClientboundPingPacket(1))

		assertEquals(0L, ServerClock.clientTicksSinceServerTick)
	}

	@Test
	fun `two time packets a second apart read as twenty ticks per second`() {
		receive(timePacket())
		nanos += SECOND
		receive(timePacket())

		assertEquals(20f, ServerClock.tps)
	}

	@Test
	fun `a stretched gap between time packets drops the estimate`() {
		receive(timePacket())
		nanos += 2 * SECOND
		receive(timePacket())

		assertEquals(10f, ServerClock.tps)
	}

	@Test
	fun `a short gap is clamped to twenty rather than reported above it`() {
		receive(timePacket())
		nanos += SECOND / 2
		receive(timePacket())

		assertEquals(20f, ServerClock.tps)
	}

	@Test
	fun `the first time packet after a world change does not estimate against the old one`() {
		receive(timePacket())
		nanos += 4 * SECOND
		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))
		receive(timePacket())

		assertEquals(20f, ServerClock.tps)
	}

	@Test
	fun `a world change resets the tps estimate`() {
		receive(timePacket())
		nanos += 4 * SECOND
		receive(timePacket())
		assertEquals(5f, ServerClock.tps)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertEquals(20f, ServerClock.tps)
	}

	@Test
	fun `a throwing subscriber drops the tick channels instead of reaching the packet path`() {
		bus.subscribe<ServerTickEvent> { error("boom") }

		receive(ClientboundPingPacket(2))

		assertFalse(TickHooks.active())
	}

	@Test
	fun `a latched server-tick channel leaves the client tick clock running`() {
		bus.subscribe<ServerTickEvent> { error("boom") }
		receive(ClientboundPingPacket(2))
		assertFalse(TickHooks.active())

		val before = TickClock.clientTick
		TickHooks.clientTicked()

		assertEquals(before + 1, TickClock.clientTick)
	}

	@Test
	fun `installing twice does not leave the first subscription counting pings`() {
		TickHooks.install(bus) { nanos }
		ticks = 0
		val before = TickClock.serverTick

		receive(ClientboundPingPacket(4))

		assertEquals(1, ticks)
		assertEquals(before + 1, TickClock.serverTick)
	}

	@Test
	fun `an uninstalled hook publishes nothing`() {
		TickHooks.uninstall()

		receive(ClientboundPingPacket(3))

		assertEquals(0, ticks)
	}

	private fun timePacket() = ClientboundSetTimePacket(0L, emptyMap())

	private fun receive(packet: Packet<*>) {
		val event = PacketReceiveEvent.Post()
		event.packet = packet
		bus.type<PacketReceiveEvent.Post>().dispatch(event)
	}

	private companion object {
		const val SECOND = 1_000_000_000L
	}
}
