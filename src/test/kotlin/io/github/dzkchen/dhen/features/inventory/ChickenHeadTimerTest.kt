package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChickenHeadTimerTest {
	private val element = ChickenHeadElement()

	@BeforeEach
	fun reset() {
		for (setting in ChickenHeadTimer.settings) setting.reset()
	}

	@Test
	fun `the module declares only the control its source exposes beside the toggle`() {
		assertEquals("Chicken Head Timer", ChickenHeadTimer.name)
		assertEquals(Category.MISC, ChickenHeadTimer.category)
		assertEquals(listOf("Hide Chat"), ChickenHeadTimer.settings.map { it.name })
		assertTrue(ChickenHeadTimer.settings.single().default as Boolean)
	}

	@Test
	fun `a fresh egg counts five seconds down in whole seconds`() {
		element.reset(1_000L)
		element.refresh(1_000L)
		assertEquals("Chicken Head Timer: §b5s", element.line)
		element.refresh(2_500L)
		assertEquals("Chicken Head Timer: §b3s", element.line)
	}

	@Test
	fun `the last second is shown to a tenth`() {
		element.reset(0L)
		element.refresh(4_060L)
		assertEquals("Chicken Head Timer: §b0.9s", element.line)
		element.refresh(4_950L)
		assertEquals("Chicken Head Timer: §b0.0s", element.line)
	}

	@Test
	fun `the line reads Now once the cooldown is spent and stays there`() {
		element.reset(0L)
		element.refresh(5_000L)
		assertEquals("Chicken Head Timer: §aNow", element.line)
		element.refresh(90_000L)
		assertEquals("Chicken Head Timer: §aNow", element.line)
	}
}
