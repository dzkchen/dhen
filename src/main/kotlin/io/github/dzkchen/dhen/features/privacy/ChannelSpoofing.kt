package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.PacketSendEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.privacy.ModRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object ChannelSpoofing : Module(
	name = "Channel Spoofing",
	category = Category.PRIVACY,
	description = "Hides the network channels that tell a server which mods you have."
) {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	init {
		on<PacketSendEvent> { event ->
			val packet = event.packet as? ServerboundCustomPayloadPacket ?: return@on
			val channel = packet.payload.type().id()
			if (!blocks(channel)) return@on
			event.cancelled = true
			log.info("Channel Spoofing dropped an outgoing payload on '{}'", channel)
		}
	}

	private val filtering: Boolean
		get() = enabled && !Minecraft.getInstance().hasSingleplayerServer()

	@JvmStatic
	fun blocks(channel: Identifier): Boolean = filtering && !ModRegistry.allows(channel)

	@JvmStatic
	fun retained(channels: Collection<Identifier>): List<Identifier>? {
		if (!filtering) return null
		for (channel in channels) {
			if (ModRegistry.allows(channel)) continue
			val retained = channels.filter(ModRegistry::allows)
			log.info("Channel Spoofing announced {} of {} channels: {}", retained.size, channels.size, retained)
			return retained
		}
		return null
	}
}
