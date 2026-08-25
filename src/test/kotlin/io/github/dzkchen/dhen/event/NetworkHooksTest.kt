package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.mixin.ServerboundChatCommandPacketAccessor
import io.github.dzkchen.dhen.mixin.ServerboundChatPacketAccessor
import io.github.dzkchen.dhen.mixin.SystemChatPacketAccessor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.game.ClientGamePacketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetworkHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		NetworkHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		NetworkHooks.uninstall()
	}

	@Test
	fun `a received packet reaches the handlers watching for it`() {
		val packet = FakePacket()
		val seen = mutableListOf<String>()
		bus.subscribe<PacketReceiveEvent.Pre> { seen += "pre ${it.packet === packet}" }
		bus.subscribe<PacketReceiveEvent.Post> { seen += "post ${it.packet === packet}" }

		assertFalse(NetworkHooks.beforeHandle(packet))
		NetworkHooks.afterHandle(packet)

		assertEquals(listOf("pre true", "post true"), seen)
	}

	@Test
	fun `cancelling an incoming packet reports back that it must not be handled`() {
		bus.subscribe<PacketReceiveEvent.Pre> { it.cancel() }

		assertTrue(NetworkHooks.beforeHandle(FakePacket()))
	}

	@Test
	fun `a chat packet raises a chat line carrying styled and stripped text`() {
		val line = Component.literal("Hello ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal("world").withStyle(ChatFormatting.BOLD))
		var styled = ""
		var stripped = ""
		bus.subscribe<ChatReceiveEvent> {
			styled = it.styled
			stripped = it.stripped
		}

		assertFalse(NetworkHooks.beforeHandle(FakeChatPacket(line)))

		assertEquals("§aHello §a§lworld", styled)
		assertEquals("Hello world", stripped)
	}

	@Test
	fun `styled text resets so a plain run does not inherit the run before it`() {
		val line = Component.empty()
			.append(Component.literal("bold").withStyle(ChatFormatting.BOLD))
			.append(Component.literal(" plain"))
		var styled = ""
		bus.subscribe<ChatReceiveEvent> { styled = it.styled }

		NetworkHooks.beforeHandle(FakeChatPacket(line))

		assertEquals("§lbold§r plain", styled)
	}

	@Test
	fun `an overlay chat packet raises an action bar line instead of a chat line`() {
		val raised = mutableListOf<String>()
		bus.subscribe<ChatReceiveEvent> { raised += "chat" }
		bus.subscribe<ActionBarEvent> { raised += "action bar ${it.stripped}" }

		NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("§c400 damage"), overlay = true))

		assertEquals(listOf("action bar 400 damage"), raised)
	}

	@Test
	fun `cancelling a chat line stops the packet that carried it`() {
		bus.subscribe<ChatReceiveEvent> { it.cancel() }

		assertTrue(NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("spam"))))
	}

	@Test
	fun `rewriting a chat line replaces the text the packet carries`() {
		val replacement = Component.literal("kept")
		bus.subscribe<ChatReceiveEvent> { it.text = replacement }
		val packet = FakeChatPacket(Component.literal("dropped"))

		assertFalse(NetworkHooks.beforeHandle(packet))

		assertSame(replacement, packet.chatContent())
	}

	@Test
	fun `a rewritten chat line reports the replacement text and not the original`() {
		var stripped: String? = null
		bus.subscribe<ChatReceiveEvent>(priority = 1) { it.text = Component.literal("second") }
		bus.subscribe<ChatReceiveEvent> { stripped = it.stripped }

		NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("first")))

		assertEquals("second", stripped)
	}

	@Test
	fun `a sent packet reaches the handlers watching for it`() {
		val packet = FakePacket()
		var seen: Packet<*>? = null
		bus.subscribe<PacketSendEvent> { seen = it.packet }

		assertFalse(NetworkHooks.beforeSend(packet))

		assertSame(packet, seen)
	}

	@Test
	fun `cancelling an outgoing packet stops it before its message is raised`() {
		var raised = false
		bus.subscribe<PacketSendEvent> { it.cancel() }
		bus.subscribe<MessageSendEvent> { raised = true }

		assertTrue(NetworkHooks.beforeSend(FakeChatSendPacket("hidden")))
		assertFalse(raised)
	}

	@Test
	fun `an outgoing chat packet raises a message that is not a command`() {
		var message = ""
		var command = true
		bus.subscribe<MessageSendEvent> {
			message = it.message
			command = it.isCommand
		}

		assertFalse(NetworkHooks.beforeSend(FakeChatSendPacket("hello there")))

		assertEquals("hello there", message)
		assertFalse(command)
	}

	@Test
	fun `an outgoing command packet raises a message that knows it is a command`() {
		var message = ""
		var command = false
		bus.subscribe<MessageSendEvent> {
			message = it.message
			command = it.isCommand
		}

		assertFalse(NetworkHooks.beforeSend(FakeCommandSendPacket("party warp")))

		assertEquals("party warp", message)
		assertTrue(command)
	}

	@Test
	fun `a packet carrying no message raises none`() {
		var raised = false
		bus.subscribe<MessageSendEvent> { raised = true }

		assertFalse(NetworkHooks.beforeSend(FakePacket()))
		assertFalse(raised)
	}

	@Test
	fun `rewriting an outgoing message replaces the text the packet carries`() {
		bus.subscribe<MessageSendEvent> { it.message = "party warp" }
		val packet = FakeCommandSendPacket("pw")

		assertFalse(NetworkHooks.beforeSend(packet))

		assertEquals("party warp", packet.chatText())
	}

	@Test
	fun `cancelling an outgoing message stops the packet that carried it`() {
		bus.subscribe<MessageSendEvent> { it.cancel() }

		assertTrue(NetworkHooks.beforeSend(FakeChatSendPacket("blocked")))
	}

	@Test
	fun `a handler that sends another packet while handling keeps the outer verdict`() {
		bus.subscribe<PacketSendEvent> { event ->
			if (event.packet is FakeChatSendPacket) {
				event.cancel()
				NetworkHooks.beforeSend(FakePacket())
			}
		}

		assertTrue(NetworkHooks.beforeSend(FakeChatSendPacket("cancelled")))
	}

	@Test
	fun `a message handler that sends a replacement keeps the outer verdict`() {
		bus.subscribe<MessageSendEvent> { event ->
			if (event.isCommand) {
				event.cancel()
				NetworkHooks.beforeSend(FakeChatSendPacket("party warp"))
			}
		}

		assertTrue(NetworkHooks.beforeSend(FakeCommandSendPacket("pw")))
	}

	@Test
	fun `a chat line raised inside another borrows its own event`() {
		val seen = mutableListOf<ChatReceiveEvent>()
		val texts = mutableListOf<String>()
		var nested = false
		bus.subscribe<ChatReceiveEvent> { event ->
			seen += event
			texts += event.text.string
			if (!nested) {
				nested = true
				NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("nested")))
				texts += event.text.string
			}
		}

		NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("outer")))

		assertNotSame(seen[0], seen[1])
		assertEquals(listOf("outer", "nested", "outer"), texts)
	}

	@Test
	fun `network events release their payloads when dispatch returns`() {
		var receivePre: PacketReceiveEvent.Pre? = null
		var receivePost: PacketReceiveEvent.Post? = null
		var sent: PacketSendEvent? = null
		var chat: ChatReceiveEvent? = null
		var actionBar: ActionBarEvent? = null
		var message: MessageSendEvent? = null
		bus.subscribe<PacketReceiveEvent.Pre> { receivePre = it }
		bus.subscribe<PacketReceiveEvent.Post> { receivePost = it }
		bus.subscribe<PacketSendEvent> { sent = it }
		bus.subscribe<ChatReceiveEvent> { chat = it }
		bus.subscribe<ActionBarEvent> { actionBar = it }
		bus.subscribe<MessageSendEvent> { message = it }
		val received = FakeChatPacket(Component.literal("chat"))

		NetworkHooks.beforeHandle(received)
		NetworkHooks.afterHandle(received)
		NetworkHooks.beforeHandle(FakeChatPacket(Component.literal("action"), overlay = true))
		NetworkHooks.beforeSend(FakeCommandSendPacket("party warp"))

		assertThrows(NullPointerException::class.java) { receivePre!!.packet }
		assertThrows(NullPointerException::class.java) { receivePost!!.packet }
		assertThrows(NullPointerException::class.java) { sent!!.packet }
		assertTrue(chat!!.text.string.isEmpty())
		assertTrue(actionBar!!.text.string.isEmpty())
		val releasedMessage = message ?: error("message event missing")
		assertTrue(releasedMessage.message.isEmpty())
	}

	@Test
	fun `a handler that throws turns the hooks off instead of failing the packet`() {
		bus.subscribe<PacketReceiveEvent.Pre> { throw IllegalStateException("boom") }

		assertFalse(NetworkHooks.beforeHandle(FakePacket()))
		assertFalse(NetworkHooks.active())
	}

	@Test
	fun `packets are ignored while the hooks are not installed`() {
		NetworkHooks.uninstall()
		var seen = false
		bus.subscribe<PacketReceiveEvent.Pre> { seen = true }
		bus.subscribe<PacketSendEvent> { seen = true }

		assertFalse(NetworkHooks.beforeHandle(FakePacket()))
		assertFalse(NetworkHooks.beforeSend(FakePacket()))
		assertFalse(seen)
	}
}

private open class FakePacket : Packet<ClientGamePacketListener> {
	override fun type(): PacketType<out Packet<ClientGamePacketListener>> =
		throw UnsupportedOperationException()

	override fun handle(listener: ClientGamePacketListener) =
		throw UnsupportedOperationException()
}

private open class FakeSendPacket(private var text: String) : FakePacket(), ChatTextAccess {
	override fun chatText(): String = text

	override fun chatText(text: String) {
		this.text = text
	}
}

private class FakeChatSendPacket(text: String) : FakeSendPacket(text), ServerboundChatPacketAccessor

private class FakeCommandSendPacket(text: String) : FakeSendPacket(text), ServerboundChatCommandPacketAccessor

private class FakeChatPacket(
	private var content: Component,
	private val overlay: Boolean = false
) : FakePacket(), SystemChatPacketAccessor {
	override fun chatContent(): Component = content

	override fun chatContent(content: Component) {
		this.content = content
	}

	override fun chatOverlay(): Boolean = overlay
}
