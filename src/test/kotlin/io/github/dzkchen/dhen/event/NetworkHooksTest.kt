package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.mixin.SystemChatPacketAccessor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.game.ClientGamePacketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
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
		var event: ChatReceiveEvent? = null
		bus.subscribe<ChatReceiveEvent> { event = it }

		assertFalse(NetworkHooks.beforeHandle(FakeChatPacket(line)))

		assertEquals("§aHello §a§lworld", event?.styled)
		assertEquals("Hello world", event?.stripped)
	}

	@Test
	fun `styled text resets so a plain run does not inherit the run before it`() {
		val line = Component.empty()
			.append(Component.literal("bold").withStyle(ChatFormatting.BOLD))
			.append(Component.literal(" plain"))
		var event: ChatReceiveEvent? = null
		bus.subscribe<ChatReceiveEvent> { event = it }

		NetworkHooks.beforeHandle(FakeChatPacket(line))

		assertEquals("§lbold§r plain", event?.styled)
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

		assertFalse(NetworkHooks.beforeHandle(FakePacket()))
		assertFalse(seen)
	}
}

private open class FakePacket : Packet<ClientGamePacketListener> {
	override fun type(): PacketType<out Packet<ClientGamePacketListener>> =
		throw UnsupportedOperationException()

	override fun handle(listener: ClientGamePacketListener) =
		throw UnsupportedOperationException()
}

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
