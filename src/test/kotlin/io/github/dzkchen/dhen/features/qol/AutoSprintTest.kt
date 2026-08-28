package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoSprintTest {
	@Test
	fun `declares the native QOL module pattern`() {
		assertEquals("Auto Sprint", AutoSprint.name)
		assertEquals(Category.QOL, AutoSprint.category)
		assertEquals(1, AutoSprint.subscriptionCount)
		assertTrue(AutoSprint.settings.isEmpty())
	}

	@Test
	fun `holds sprint only during unscreened movement handling before sprint starts`() {
		assertTrue(AutoSprint.shouldHoldSprint(screenOpen = false, sprinting = false))
		assertFalse(AutoSprint.shouldHoldSprint(screenOpen = true, sprinting = false))
		assertFalse(AutoSprint.shouldHoldSprint(screenOpen = false, sprinting = true))
		assertFalse(AutoSprint.shouldHoldSprint(screenOpen = true, sprinting = true))
	}
}
