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
	}

	@Test
	fun `declares the water sub-toggle off by default`() {
		assertEquals(listOf(AutoSprint.disableInWaterSetting), AutoSprint.settings)
		assertEquals("Disable In Water", AutoSprint.disableInWaterSetting.name)
		assertEquals("Stops sprinting while you are in water.", AutoSprint.disableInWaterSetting.description)
		assertFalse(AutoSprint.disableInWaterSetting.default)
		assertFalse(AutoSprint.disableInWaterSetting.on)
	}

	@Test
	fun `owns the sprint key only during unscreened movement handling before sprint starts`() {
		assertTrue(AutoSprint.ownsSprintKey(screenOpen = false, sprinting = false))
		assertFalse(AutoSprint.ownsSprintKey(screenOpen = true, sprinting = false))
		assertFalse(AutoSprint.ownsSprintKey(screenOpen = false, sprinting = true))
		assertFalse(AutoSprint.ownsSprintKey(screenOpen = true, sprinting = true))
	}

	@Test
	fun `ignores water while the sub-toggle is off`() {
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = false, inWater = true))
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = false, inWater = false))
	}

	@Test
	fun `releases the sprint key in water only while the sub-toggle is on`() {
		assertFalse(AutoSprint.shouldHoldSprint(disableInWater = true, inWater = true))
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = true, inWater = false))
	}
}
