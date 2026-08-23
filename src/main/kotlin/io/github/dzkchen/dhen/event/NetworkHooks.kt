package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.mixin.ServerboundChatCommandPacketAccessor
import io.github.dzkchen.dhen.mixin.SystemChatPacketAccessor
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet

internal object NetworkHooks {
	private val failsafe = Failsafe("Dhen {} failed, its network events are off until restart")

	@Volatile
	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	fun uninstall() {
		channels = null
	}

	@JvmStatic
	fun active(): Boolean = channels != null

	@JvmStatic
	fun beforeHandle(packet: Packet<*>): Boolean = guarded("packet receive") { channels ->
		if (channels.received(packet)) return@guarded true
		val chat = packet as? SystemChatPacketAccessor ?: return@guarded false
		val content = chat.chatContent()
		val shown = channels.chatLine(content, chat.chatOverlay()) ?: return@guarded true
		if (shown !== content) chat.chatContent(shown)
		false
	}

	@JvmStatic
	fun beforeSend(packet: Packet<*>): Boolean = guarded("packet send") { channels -> channels.sent(packet) }

	@JvmStatic
	fun afterHandle(packet: Packet<*>) {
		guarded("packet handled") { channels ->
			channels.handled(packet)
			false
		}
	}

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			this.channels = null
			failsafe.fail(label, throwable)
			false
		}
	}

	private class Channels(bus: EventBus) {
		private val receivePre = bus.type<PacketReceiveEvent.Pre>()
		private val receivePost = bus.type<PacketReceiveEvent.Post>()
		private val chat = TextChannel(bus.type<ChatReceiveEvent>(), ChatReceiveEvent())
		private val actionBar = TextChannel(bus.type<ActionBarEvent>(), ActionBarEvent())
		private val outbound = OutboundChannel(bus.type<PacketSendEvent>(), bus.type<MessageSendEvent>())
		private val preEvent = PacketReceiveEvent.Pre()
		private val postEvent = PacketReceiveEvent.Post()

		fun received(packet: Packet<*>): Boolean {
			preEvent.packet = packet
			preEvent.cancelled = false
			receivePre.dispatch(preEvent)
			return preEvent.cancelled
		}

		fun handled(packet: Packet<*>) {
			postEvent.packet = packet
			receivePost.dispatch(postEvent)
		}

		fun sent(packet: Packet<*>): Boolean = outbound.publish(packet)

		fun chatLine(content: Component, overlay: Boolean): Component? =
			(if (overlay) actionBar else chat).publish(content)
	}

	private class OutboundChannel(
		private val packets: EventBus.EventType<PacketSendEvent>,
		private val messages: EventBus.EventType<MessageSendEvent>
	) {
		private val sendEvent = PacketSendEvent()
		private val messageEvent = MessageSendEvent()
		private var sharedEventsInUse = false

		fun publish(packet: Packet<*>): Boolean {
			if (sharedEventsInUse) return publish(packet, PacketSendEvent(), MessageSendEvent())
			sharedEventsInUse = true
			return try {
				publish(packet, sendEvent, messageEvent)
			} finally {
				sharedEventsInUse = false
			}
		}

		private fun publish(packet: Packet<*>, send: PacketSendEvent, message: MessageSendEvent): Boolean {
			send.packet = packet
			send.cancelled = false
			packets.dispatch(send)
			if (send.cancelled) return true
			val outgoing = packet as? ChatTextAccess ?: return false
			val text = outgoing.chatText()
			message.cancelled = false
			message.isCommand = outgoing is ServerboundChatCommandPacketAccessor
			message.message = text
			messages.dispatch(message)
			if (message.cancelled) return true
			if (message.message != text) outgoing.chatText(message.message)
			return false
		}
	}

	private class TextChannel<T : TextEvent>(
		private val channel: EventBus.EventType<T>,
		private val event: T
	) {
		fun publish(content: Component): Component? {
			event.cancelled = false
			event.text = content
			channel.dispatch(event)
			return if (event.cancelled) null else event.text
		}
	}
}
