package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyRole
import io.github.dzkchen.dhen.util.Failsafe
import net.hypixel.data.type.GameType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.error.BuiltinErrorReason
import net.hypixel.modapi.error.ErrorReason
import net.hypixel.modapi.handler.RegisteredHandler
import net.hypixel.modapi.packet.ClientboundHypixelPacket
import net.hypixel.modapi.packet.EventPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.util.UUID

internal object HypixelModApi {
	private val logger = LoggerFactory.getLogger(Dhen.MOD_ID)

	private val failsafe = Failsafe("Dhen {} failed, its Hypixel Mod API packets are off until restart")

	private val listedName: (UUID) -> String? = { uuid ->
		Minecraft.getInstance().connection?.getPlayerInfo(uuid)?.profile?.name
	}

	private var registered = false

	fun install() {
		if (registered) return
		registered = true
		try {
			handle(ClientboundHelloPacket::class.java) {
				HypixelLocationHooks.greeted()
				PartyHooks.greeted()
			}
			handleEvent(ClientboundLocationPacket::class.java, ::located)
			handle(ClientboundPartyInfoPacket::class.java) { partyInfo(it) }
				.onError { reason -> Minecraft.getInstance().execute { partyInfoRefused(reason) } }
		} catch (throwable: Throwable) {
			HypixelLocationHooks.uninstall()
			PartyHooks.uninstall()
			failsafe.fail("Hypixel Mod API registration", throwable)
		}
	}

	fun located(packet: ClientboundLocationPacket) = HypixelLocationHooks.located(
		serverName = packet.serverName,
		skyBlock = packet.serverType.orElse(null) == GameType.SKYBLOCK,
		mode = packet.mode.orElse(null),
		map = packet.map.orElse(null)
	)

	fun partyInfo(packet: ClientboundPartyInfoPacket, nameOf: (UUID) -> String? = listedName) {
		if (!packet.isInParty) return PartyHooks.reconciled(false, null, emptyMap(), 0)
		val leader = packet.leader.orElse(null)
		val members = packet.memberMap
		val roles = LinkedHashMap<String, PartyRole>(members.size)
		var leaderName: String? = null
		for (member in members.values) {
			val name = nameOf(member.uuid) ?: continue
			roles[name] = roleOf(member.role)
			if (member.uuid == leader) leaderName = name
		}
		PartyHooks.reconciled(true, leaderName, roles, members.size)
	}

	fun requestPartyInfo() {
		if (!SkyBlockLocation.onHypixel) return
		HypixelModAPI.getInstance().sendPacket(ServerboundPartyInfoPacket())
	}

	fun <T : ClientboundHypixelPacket> handle(type: Class<T>, handler: (T) -> Unit): RegisteredHandler<T> =
		HypixelModAPI.getInstance().createHandler(type) { packet ->
			Minecraft.getInstance().execute { handler(packet) }
		}

	fun <T : EventPacket> handleEvent(type: Class<T>, handler: (T) -> Unit): RegisteredHandler<T> {
		HypixelModAPI.getInstance().subscribeToEventPacket(type)
		return handle(type, handler)
	}

	private fun partyInfoRefused(reason: ErrorReason) {
		logger.warn("Hypixel refused the party info request: {}", reason)
		PartyHooks.refused(
			when (reason) {
				BuiltinErrorReason.DISABLED,
				BuiltinErrorReason.INVALID_PACKET_VERSION,
				BuiltinErrorReason.NO_LONGER_SUPPORTED -> true

				else -> false
			}
		)
	}

	private fun roleOf(role: ClientboundPartyInfoPacket.PartyRole): PartyRole = when (role) {
		ClientboundPartyInfoPacket.PartyRole.LEADER -> PartyRole.LEADER
		ClientboundPartyInfoPacket.PartyRole.MOD -> PartyRole.MOD
		else -> PartyRole.MEMBER
	}
}
