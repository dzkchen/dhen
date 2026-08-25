package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.mixin.ServerboundChatCommandPacketAccessor
import io.github.dzkchen.dhen.mixin.SystemChatPacketAccessor
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet

internal object NetworkHooks : GuardedHooks<NetworkHooks.Channels> {
	override val feed = "Packets"

	override val failsafe = Failsafe("Dhen {} failed, its network events are off until restart")

	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	override fun uninstall() {
		channels = null
	}

	override fun bound() = channels

	@JvmStatic
	fun beforeHandle(packet: Packet<*>): Boolean = guarded("packet receive", false) { channels ->
		if (channels.received(packet)) return@guarded true
		val chat = packet as? SystemChatPacketAccessor ?: return@guarded false
		val content = chat.chatContent()
		val shown = channels.chatLine(content, chat.chatOverlay()) ?: return@guarded true
		if (shown !== content) chat.chatContent(shown)
		false
	}

	@JvmStatic
	fun beforeSend(packet: Packet<*>): Boolean = guarded("packet send", false) { channels -> channels.sent(packet) }

	@JvmStatic
	fun afterHandle(packet: Packet<*>) {
		guarded("packet handled") { it.handled(packet) }
	}

	internal class Channels(bus: EventBus) {
		private val receivePre = bus.type<PacketReceiveEvent.Pre>()
		private val receivePost = bus.type<PacketReceiveEvent.Post>()
		private val chat = TextChannel(bus.type<ChatReceiveEvent>(), ::ChatReceiveEvent)
		private val actionBar = TextChannel(bus.type<ActionBarEvent>(), ::ActionBarEvent)
		private val outbound = OutboundChannel(bus.type<PacketSendEvent>(), bus.type<MessageSendEvent>())
		private val preEvents = ReusableEvent(PacketReceiveEvent::Pre, PacketReceiveEvent::forget)
		private val postEvents = ReusableEvent(PacketReceiveEvent::Post, PacketReceiveEvent::forget)

		fun received(packet: Packet<*>): Boolean {
			val event = preEvents.borrow()
			event.packet = packet
			event.cancelled = false
			return try {
				receivePre.dispatch(event)
				event.cancelled
			} finally {
				preEvents.release(event)
			}
		}

		fun handled(packet: Packet<*>) {
			val event = postEvents.borrow()
			event.packet = packet
			try {
				receivePost.dispatch(event)
			} finally {
				postEvents.release(event)
			}
		}

		fun sent(packet: Packet<*>): Boolean = outbound.publish(packet)

		fun chatLine(content: Component, overlay: Boolean): Component? =
			(if (overlay) actionBar else chat).publish(content)
	}

	private class OutboundChannel(
		private val packets: EventBus.EventType<PacketSendEvent>,
		private val messages: EventBus.EventType<MessageSendEvent>
	) {
		private val sendEvents = ReusableEvent(::PacketSendEvent, PacketSendEvent::forget)
		private val messageEvents = ReusableEvent(::MessageSendEvent, MessageSendEvent::forget)

		fun publish(packet: Packet<*>): Boolean {
			val send = sendEvents.borrow()
			send.packet = packet
			send.cancelled = false
			val cancelled = try {
				packets.dispatch(send)
				send.cancelled
			} finally {
				sendEvents.release(send)
			}
			if (cancelled) return true
			val outgoing = packet as? ChatTextAccess ?: return false
			val text = outgoing.chatText()
			val message = messageEvents.borrow()
			message.cancelled = false
			message.isCommand = outgoing is ServerboundChatCommandPacketAccessor
			message.message = text
			return try {
				messages.dispatch(message)
				if (message.cancelled) return true
				if (message.message != text) outgoing.chatText(message.message)
				false
			} finally {
				messageEvents.release(message)
			}
		}
	}

	private class TextChannel<T : TextEvent>(
		private val channel: EventBus.EventType<T>,
		spare: () -> T
	) {
		private val events = ReusableEvent(spare, TextEvent::forget)

		fun publish(content: Component): Component? {
			val event = events.borrow()
			event.cancelled = false
			event.text = content
			return try {
				channel.dispatch(event)
				if (event.cancelled) null else event.text
			} finally {
				events.release(event)
			}
		}
	}
}
