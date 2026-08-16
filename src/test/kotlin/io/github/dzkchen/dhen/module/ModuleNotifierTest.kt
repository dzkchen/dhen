package io.github.dzkchen.dhen.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleNotifierTest {
	@Test
	fun `a chat notice waits for the client thread instead of announcing where it was raised`() {
		val queued = mutableListOf<Runnable>()
		val announced = mutableListOf<String>()
		val notifier = ModuleNotifier.chatBacked({ queued += it }, { announced += it })

		notifier.notify(PlaceholderModule(), "Module 'Test Module' encountered an error.")

		assertTrue(announced.isEmpty())

		queued.forEach(Runnable::run)

		assertEquals(listOf("Module 'Test Module' encountered an error."), announced)
	}
}
