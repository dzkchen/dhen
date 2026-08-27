package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.ServerClock
import io.github.dzkchen.dhen.util.TickClock
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket

internal object TickHooks : GuardedHooks<TickHooks.ServerChannels> {
	override val feed = "Ticks"

	override val failsafe = Failsafe("Dhen {} failed, its tick events are off until restart")

	private var serverChannels: ServerChannels? = null
	private var clientChannels: ClientChannels? = null

	private var serverSubscriptions: Array<Handle> = emptyArray()
	private var resetSubscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, clock: NanoClock = NanoClock.SYSTEM) {
		uninstall()
		serverChannels = ServerChannels(bus, clock)
		clientChannels = ClientChannels(bus)
		serverSubscriptions = arrayOf(bus.subscribe<PacketReceiveEvent.Post> { received(it.packet) })
		resetSubscriptions = arrayOf(
			bus.subscribe<WorldChangeEvent> { worldChanged() },
			bus.subscribe<IslandChangeEvent> { if (it.resetsWorldState) worldChanged() }
		)
	}

	override fun uninstall() {
		disableServerChannels()
		resetSubscriptions.forEach(Handle::unsubscribe)
		resetSubscriptions = emptyArray()
		clientChannels = null
	}

	override fun active(): Boolean = clientChannels != null

	override fun bound() = serverChannels

	override fun latchOff() = disableServerChannels()

	fun serverFeedActive(): Boolean = bound() != null

	fun clientTickStarted() = clientChannels?.started()

	fun clientTicked(publish: Boolean = true) {
		TickClock.clientTicked()
		if (publish) clientChannels?.ended()
	}

	private fun received(packet: Packet<*>) = guarded("server tick") { channels ->
		when (packet) {
			is ClientboundPingPacket -> if (packet.id != 0) channels.serverTicked()
			is ClientboundSetTimePacket -> channels.timeSynced()
			else -> Unit
		}
	}

	private fun worldChanged() {
		try {
			ServerClock.reset()
			TickClock.cancelWorldScopedWaits()
		} catch (throwable: Throwable) {
			uninstall()
			failsafe.fail("tick clock reset", throwable)
		}
	}

	private fun disableServerChannels() {
		serverSubscriptions.forEach(Handle::unsubscribe)
		serverSubscriptions = emptyArray()
		serverChannels = null
	}

	private class ClientChannels(bus: EventBus) {
		private val starts = bus.type<ClientTickEvent.Start>()
		private val ends = bus.type<ClientTickEvent.End>()

		fun started() = starts.dispatch(ClientTickEvent.Start)

		fun ended() = ends.dispatch(ClientTickEvent.End)
	}

	internal class ServerChannels(bus: EventBus, private val clock: NanoClock) {
		private val ticks = bus.type<ServerTickEvent>()

		fun serverTicked() {
			ServerClock.serverTicked(clock.nanoTime())
			TickClock.serverTicked()
			ticks.dispatch(ServerTickEvent)
		}

		fun timeSynced() = ServerClock.timeSynced(clock.nanoTime())
	}
}
