package io.github.dzkchen.dhen.event

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TooltipEventTest {
	private val event = TooltipEvent()

	@Test
	fun `a tooltip nobody edits is drawn from the game's own list`() {
		val vanilla = listOf(Component.literal("Diamond Sword"))
		event.reuse(vanilla)

		assertSame(vanilla, event.lines)
	}

	@Test
	fun `editing leaves the game's own list alone`() {
		val vanilla = mutableListOf<Component>(Component.literal("Diamond Sword"))
		event.reuse(vanilla)

		event.edit().add(Component.literal("Worth 1,000 coins"))

		assertEquals(1, vanilla.size)
		assertEquals(2, event.lines.size)
		assertNotSame(vanilla, event.lines)
	}

	@Test
	fun `two handlers editing the same tooltip share one copy`() {
		event.reuse(listOf(Component.literal("Diamond Sword")))

		val first = event.edit()
		first.add(Component.literal("Worth 1,000 coins"))
		val second = event.edit()
		second.add(Component.literal("Protected"))

		assertSame(first, second)
		assertEquals(3, event.lines.size)
	}

	@Test
	fun `the next tooltip starts from the game's list again`() {
		event.reuse(listOf(Component.literal("Diamond Sword")))
		event.edit().add(Component.literal("Worth 1,000 coins"))

		val next = listOf(Component.literal("Bread"))
		event.reuse(next)

		assertSame(next, event.lines)
	}
}
