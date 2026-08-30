package io.github.dzkchen.dhen.privacy

import net.minecraft.network.protocol.Packet
import net.minecraft.resources.Identifier

object PacketContext {
	private const val MAX_HANDLE_DEPTH = 64
	private const val UNKNOWN_PACKET = "unknown"

	private val current = ThreadLocal.withInitial(::State)

	@JvmStatic
	fun beginDecode() {
		current.get().decodeDepth++
	}

	@JvmStatic
	fun endDecode(packet: Any? = null) {
		val state = current.get()
		state.decodeDepth--
		if (state.decodeDepth > 0) return
		val packetId = (packet as? Packet<*>)?.type()?.id()
		var origin = state.decoded
		while (origin != null) {
			val next = origin.decodedNext()
			origin.linkDecoded(null)
			if (packetId != null) origin.identifyPacket(packetId)
			origin = next
		}
		state.decoded = null
	}

	@JvmStatic
	fun trackDecoded(origin: PacketOrigin) {
		val state = current.get()
		val packetId = state.packetId
		if (state.decodeDepth == 0 && packetId != null) {
			origin.identifyPacket(packetId)
			return
		}
		origin.linkDecoded(state.decoded)
		state.decoded = origin
	}

	@JvmStatic
	fun beginHandle(packet: Packet<*>) {
		val state = current.get()
		state.packetIds[state.handleDepth] = state.packetId
		state.handleDepth++
		state.packetId = packet.type().id()
	}

	@JvmStatic
	fun endHandle() {
		val state = current.get()
		state.handleDepth--
		state.packetId = state.packetIds[state.handleDepth]
		state.packetIds[state.handleDepth] = null
	}

	@JvmStatic
	fun isDecodingPayload(): Boolean = current.get().decodeDepth > 0

	@JvmStatic
	fun isProcessingPacket(): Boolean {
		val state = current.get()
		return state.decodeDepth > 0 || state.handleDepth > 0
	}

	@JvmStatic
	fun packetName(): String = current.get().packetId?.toString() ?: UNKNOWN_PACKET

	private class State {
		var decodeDepth = 0
		var handleDepth = 0
		var packetId: Identifier? = null
		var decoded: PacketOrigin? = null
		val packetIds = arrayOfNulls<Identifier>(MAX_HANDLE_DEPTH)
	}
}
