package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.util.Failsafe
import net.hypixel.data.type.GameType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.ClientboundHypixelPacket
import net.hypixel.modapi.packet.EventPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.client.Minecraft

internal object HypixelModApi {
	private val failsafe = Failsafe("Dhen {} failed, its Hypixel Mod API packets are off until restart")

	private var registered = false

	fun install() {
		if (registered) return
		registered = true
		try {
			handle(ClientboundHelloPacket::class.java) { HypixelLocationHooks.greeted() }
			handleEvent(ClientboundLocationPacket::class.java, ::located)
		} catch (throwable: Throwable) {
			HypixelLocationHooks.uninstall()
			failsafe.fail("Hypixel Mod API registration", throwable)
		}
	}

	fun located(packet: ClientboundLocationPacket) = HypixelLocationHooks.located(
		serverName = packet.serverName,
		skyBlock = packet.serverType.orElse(null) == GameType.SKYBLOCK,
		mode = packet.mode.orElse(null),
		map = packet.map.orElse(null)
	)

	fun <T : ClientboundHypixelPacket> handle(type: Class<T>, handler: (T) -> Unit) {
		HypixelModAPI.getInstance().createHandler(type) { packet ->
			Minecraft.getInstance().execute { handler(packet) }
		}
	}

	fun <T : EventPacket> handleEvent(type: Class<T>, handler: (T) -> Unit) {
		HypixelModAPI.getInstance().subscribeToEventPacket(type)
		handle(type, handler)
	}
}
