package io.github.dzkchen.dhen.event

import net.minecraft.network.protocol.Packet

sealed class PacketReceiveEvent : DeepProfiledEvent {
	lateinit var packet: Packet<*>
		internal set

	class Pre internal constructor() : PacketReceiveEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class Post internal constructor() : PacketReceiveEvent()
}

class PacketSendEvent internal constructor() : DeepProfiledEvent, Cancellable {
	lateinit var packet: Packet<*>
		internal set

	override var cancelled: Boolean = false
}
