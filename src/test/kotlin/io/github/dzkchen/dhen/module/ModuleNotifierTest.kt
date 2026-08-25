package io.github.dzkchen.dhen.module

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleNotifierTest {
	@Test
	fun `a chat notice waits for the client thread instead of announcing where it was raised`() {
		val queued = mutableListOf<Runnable>()
		val announced = mutableListOf<Component>()
		val notifier = ModuleNotifier.chatBacked({ queued += it }, { announced += it })

		notifier.notify(PlaceholderModule(), "Module 'Test Module' encountered an error.", null)

		assertTrue(announced.isEmpty())

		queued.forEach(Runnable::run)

		assertEquals(listOf("Module 'Test Module' encountered an error."), announced.map { it.string })
		assertNull(announced.single().style.clickEvent)
	}

	@Test
	fun `an error notice copies its retained trace`() {
		val queued = mutableListOf<Runnable>()
		val announced = mutableListOf<Component>()
		val notifier = ModuleNotifier.chatBacked({ queued += it }, { announced += it })

		notifier.notify(PlaceholderModule(), "Click to copy.", "retained trace")
		queued.forEach(Runnable::run)

		val click = announced.single().style.clickEvent as ClickEvent.CopyToClipboard
		assertEquals("retained trace", click.value)
	}
}
