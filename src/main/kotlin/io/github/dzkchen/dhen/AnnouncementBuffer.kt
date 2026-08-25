package io.github.dzkchen.dhen

import net.minecraft.network.chat.Component

internal class AnnouncementBuffer(
	private val capacity: Int
) {
	init {
		require(capacity > 0) { "Announcement capacity must be positive." }
	}

	private val pending = ArrayDeque<Component>(capacity)

	fun add(message: Component) {
		if (pending.size == capacity) pending.removeFirst()
		pending.addLast(message)
	}

	fun flush(send: (Component) -> Unit) {
		while (pending.isNotEmpty()) send(pending.removeFirst())
	}
}
