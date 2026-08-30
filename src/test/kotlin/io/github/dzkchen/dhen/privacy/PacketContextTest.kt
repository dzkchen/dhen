package io.github.dzkchen.dhen.privacy

import com.google.gson.JsonPrimitive
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PacketContextTest {
	@Test
	fun `decode and handle contexts nest and clear`() {
		val packet = ClientboundSystemChatPacket(Component.literal("message"), false)
		val nested = ClientboundSetTitleTextPacket(Component.literal("title"))

		assertFalse(PacketContext.isProcessingPacket())
		PacketContext.beginDecode()
		try {
			assertTrue(PacketContext.isDecodingPayload())
			PacketContext.beginDecode()
			PacketContext.endDecode()
			PacketContext.beginHandle(packet)
			try {
				assertTrue(PacketContext.isProcessingPacket())
				assertEquals("minecraft:system_chat", PacketContext.packetName())
				PacketContext.beginHandle(nested)
				PacketContext.endHandle()
				assertEquals("minecraft:system_chat", PacketContext.packetName())
			} finally {
				PacketContext.endHandle()
			}
			assertTrue(PacketContext.isDecodingPayload())
		} finally {
			PacketContext.endDecode()
		}
		assertFalse(PacketContext.isProcessingPacket())
		assertEquals("unknown", PacketContext.packetName())
	}

	@Test
	fun `codec marks packet trees but leaves local trees alone`() {
		val argument = MarkedContents()
		val sibling = MarkedContents()
		val component = Component.translatable("dhen.test", MutableComponent.create(argument))
			.append(MutableComponent.create(sibling))
		val codec = PacketComponentCodec(Codec.STRING.xmap({ component }, { "encoded" }))

		codec.decode(JsonOps.INSTANCE, JsonPrimitive("local")).getOrThrow()
		assertFalse(argument.fromPacket())
		assertFalse(sibling.fromPacket())

		PacketContext.beginDecode()
		try {
			codec.decode(JsonOps.INSTANCE, JsonPrimitive("packet")).getOrThrow()
		} finally {
			PacketContext.endDecode(ClientboundSystemChatPacket(component, false))
		}
		assertTrue(argument.fromPacket())
		assertTrue(sibling.fromPacket())
		assertEquals("minecraft:system_chat", argument.packetName())
		assertEquals("minecraft:system_chat", sibling.packetName())
	}

	@Test
	fun `a component decoded lazily while handling takes the current packet name`() {
		val contents = MarkedContents()
		val component = MutableComponent.create(contents)
		val packet = ClientboundSetTitleTextPacket(Component.literal("title"))

		PacketContext.beginHandle(packet)
		try {
			PacketComponentCodec.markTree(component)
		} finally {
			PacketContext.endHandle()
		}

		assertEquals("minecraft:set_title_text", contents.packetName())
	}

	private class MarkedContents : PlainTextContents, PacketOrigin {
		private var marked = false
		private var packetId: Identifier? = null
		private var next: PacketOrigin? = null

		override fun text() = ""

		override fun fromPacket() = marked

		override fun markFromPacket() {
			marked = true
		}

		override fun packetName() = packetId?.toString() ?: "unknown"

		override fun decodedNext() = next

		override fun linkDecoded(next: PacketOrigin?) {
			this.next = next
		}

		override fun identifyPacket(id: Identifier) {
			packetId = id
		}
	}
}
