package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.ServerClock
import io.github.dzkchen.dhen.util.TickClock
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket

internal object TickHooks {
	private val failsafe = Failsafe("Dhen {} failed, its tick events are off until restart")

	@Volatile
	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, clock: NanoClock = NanoClock.SYSTEM) {
		uninstall()
		channels = Channels(bus, clock)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post> { received(it.packet) },
			bus.subscribe<WorldChangeEvent> { worldChanged() }
		)
	}

	fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
	}

	fun active(): Boolean = channels != null

	fun clientTicked() = TickClock.clientTicked()

	private fun received(packet: Packet<*>) = guarded("server tick") { channels ->
		when (packet) {
			is ClientboundPingPacket -> if (packet.id != 0) channels.serverTicked()
			is ClientboundSetTimePacket -> channels.timeSynced()
			else -> Unit
		}
	}

	private fun worldChanged() = guarded("tick clock reset") { it.reset() }

	private inline fun guarded(label: String, block: (Channels) -> Unit) {
		val channels = channels ?: return
		try {
			block(channels)
		} catch (throwable: Throwable) {
			uninstall()
			failsafe.fail(label, throwable)
		}
	}

	private class Channels(bus: EventBus, private val clock: NanoClock) {
		private val ticks = bus.type<ServerTickEvent>()

		fun serverTicked() {
			ServerClock.serverTicked(clock.nanoTime())
			TickClock.serverTicked()
			ticks.dispatch(ServerTickEvent)
		}

		fun timeSynced() = ServerClock.timeSynced(clock.nanoTime())

		fun reset() {
			ServerClock.reset()
			TickClock.cancelWorldScopedWaits()
		}
	}
}
