package io.github.dzkchen.dhen

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnnouncementBufferTest {
	@Test
	fun `pre-join notices flush once in insertion order`() {
		val buffer = AnnouncementBuffer(3)
		val sent = mutableListOf<String>()
		buffer.add(Component.literal("first"))
		buffer.add(Component.literal("second"))

		buffer.flush { sent += it.string }
		buffer.flush { sent += it.string }

		assertEquals(listOf("first", "second"), sent)
	}

	@Test
	fun `full buffer keeps the newest notices`() {
		val buffer = AnnouncementBuffer(2)
		val sent = mutableListOf<String>()
		buffer.add(Component.literal("oldest"))
		buffer.add(Component.literal("middle"))
		buffer.add(Component.literal("newest"))

		buffer.flush { sent += it.string }

		assertEquals(listOf("middle", "newest"), sent)
	}
}
