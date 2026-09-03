package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.ServerClock
import io.github.dzkchen.dhen.util.TickClock
import io.github.dzkchen.dhen.util.delayTicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
	fun `client tick phases publish once in order`() {
		val phases = mutableListOf<String>()
		bus.subscribe<ClientTickEvent.Start> { phases += "start" }
		bus.subscribe<ClientTickEvent.End> { phases += "end" }

		TickHooks.clientTickStarted()
		TickHooks.clientTicked()

		assertEquals(listOf("start", "end"), phases)
	}

	@Test
	fun `the end phase sees the advanced client clock`() {
		var seen = -1L
		bus.subscribe<ClientTickEvent.End> { seen = TickClock.clientTick }
		val before = TickClock.clientTick

		TickHooks.clientTicked()

		assertEquals(before + 1, seen)
	}

	@Test
	fun `the client clock advances without publishing outside a world`() {
		var ticks = 0
		bus.subscribe<ClientTickEvent.End> { ticks++ }
		val before = TickClock.clientTick

		TickHooks.clientTicked(publish = false)

		assertEquals(before + 1, TickClock.clientTick)
		assertEquals(0, ticks)
	}

	@Test
	fun `a module receives end ticks only while enabled`() {
		val manager = ModuleManager(bus)
		val module = ClientTickModule()
		manager.register(module)

		TickHooks.clientTicked()
		manager.enable(module)
		TickHooks.clientTicked()
		manager.disable(module)
		TickHooks.clientTicked()

		assertEquals(1, module.ticks)
		assertEquals(1, module.subscriptionCount)
		assertEquals("End", module.handlerTimings.single().eventName)
		assertTrue(Diagnostics(manager).lines().any { it.startsWith("Client Tick Module: subscriptions=1,") })
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
	fun `an island change resets the server clock and cancels a pending tick wait`() {
		val scope = CoroutineScope(Dispatchers.Unconfined)
		val waiting = scope.launch { delayTicks(500) }
		try {
			receive(timePacket())
			nanos += 4 * SECOND
			receive(timePacket())
			assertEquals(5f, ServerClock.tps)
			bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.HUB, Island.NONE))
			assertEquals(5f, ServerClock.tps)
			assertFalse(waiting.isCancelled)

			bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.CATACOMBS, Island.HUB))

			assertEquals(20f, ServerClock.tps)
			assertTrue(waiting.isCancelled)
		} finally {
			scope.cancel()
		}
	}

	@Test
	fun `leaving skyblock cancels a pending tick wait without a disconnect`() {
		val scope = CoroutineScope(Dispatchers.Unconfined)
		val waiting = scope.launch { delayTicks(500) }
		try {
			bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.NONE, Island.HUB))

			assertTrue(waiting.isCancelled)
		} finally {
			scope.cancel()
		}
	}

	@Test
	fun `a throwing subscriber drops the tick channels instead of reaching the packet path`() {
		bus.subscribe<ServerTickEvent> { error("boom") }

		receive(ClientboundPingPacket(2))

		assertFalse(TickHooks.serverFeedActive())
		assertTrue(TickHooks.active())
	}

	@Test
	fun `a latched server-tick channel leaves the client tick clock running`() {
		var clientTicks = 0
		bus.subscribe<ClientTickEvent.End> { clientTicks++ }
		bus.subscribe<ServerTickEvent> { error("boom") }
		receive(ClientboundPingPacket(2))
		assertFalse(TickHooks.serverFeedActive())

		val before = TickClock.clientTick
		TickHooks.clientTicked()

		assertEquals(before + 1, TickClock.clientTick)
		assertEquals(1, clientTicks)
	}

	@Test
	fun `a latched server-tick channel still cancels world-scoped waits`() {
		val scope = CoroutineScope(Dispatchers.Unconfined)
		val waiting = scope.launch { delayTicks(500) }
		try {
			bus.subscribe<ServerTickEvent> { error("boom") }
			receive(ClientboundPingPacket(2))
			assertFalse(TickHooks.serverFeedActive())

			bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))

			assertTrue(waiting.isCancelled)
		} finally {
			scope.cancel()
		}
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
		var clientTicks = 0
		bus.subscribe<ClientTickEvent.Start> { clientTicks++ }
		bus.subscribe<ClientTickEvent.End> { clientTicks++ }
		TickHooks.uninstall()

		TickHooks.clientTickStarted()
		TickHooks.clientTicked()
		receive(ClientboundPingPacket(3))

		assertEquals(0, ticks)
		assertEquals(0, clientTicks)
		assertFalse(bus.type<WorldChangeEvent>().hasSubscribers)
		assertFalse(bus.type<IslandChangeEvent>().hasSubscribers)
	}

	private fun timePacket() = ClientboundSetTimePacket(0L, emptyMap())

	private fun receive(packet: Packet<*>) {
		val event = PacketReceiveEvent.Post()
		event.packet = packet
		bus.type<PacketReceiveEvent.Post>().dispatch(event)
	}

	private class ClientTickModule : Module(
		name = "Client Tick Module",
		category = Category.DEV,
		description = "Counts client ticks."
	) {
		var ticks = 0
			private set

		init {
			on<ClientTickEvent.End> { ticks++ }
		}
	}

	private companion object {
		const val SECOND = 1_000_000_000L
	}
}
