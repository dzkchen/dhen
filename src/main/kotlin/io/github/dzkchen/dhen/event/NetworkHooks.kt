package io.github.dzkchen.dhen.event

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

		fun chatLine(content: Component, overlay: Boolean): Component? =
			(if (overlay) actionBar else chat).publish(content)
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
