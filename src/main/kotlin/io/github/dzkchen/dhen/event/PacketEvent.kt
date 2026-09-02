package io.github.dzkchen.dhen.event

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.syncher.SynchedEntityData
import java.util.Optional

internal fun trackedName(entry: SynchedEntityData.DataValue<*>): Component? =
	(entry.value() as? Optional<*>)?.orElse(null) as? Component

sealed class PacketReceiveEvent : DeepProfiledEvent {
	private var held: Packet<*>? = null

	var packet: Packet<*>
		get() = held!!
		internal set(value) {
			held = value
		}

	internal fun forget() {
		held = null
	}

	class Pre internal constructor() : PacketReceiveEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class Post internal constructor() : PacketReceiveEvent()
}

class PacketSendEvent internal constructor() : DeepProfiledEvent, Cancellable {
	private var held: Packet<*>? = null

	var packet: Packet<*>
		get() = held!!
		internal set(value) {
			held = value
		}

	override var cancelled: Boolean = false

	internal fun forget() {
		held = null
	}
}
