package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoSprintTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		AutoSprint.setEnabled(false)
		for (setting in AutoSprint.settings) setting.reset()
	}

	private fun enabled(disableInWater: Boolean) {
		AutoSprint.disableInWaterSetting.on = disableInWater
		AutoSprint.setEnabled(true)
	}

	@Test
	fun `drives the sprint decision without subscribing to the bus`() {
		assertEquals("Auto Sprint", AutoSprint.name)
		assertEquals(Category.QOL, AutoSprint.category)
		assertEquals(0, AutoSprint.subscriptionCount)
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
	fun `ignores water while the sub-toggle is off`() {
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = false, inWater = true))
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = false, inWater = false))
	}

	@Test
	fun `stands down in water only while the sub-toggle is on`() {
		assertFalse(AutoSprint.shouldHoldSprint(disableInWater = true, inWater = true))
		assertTrue(AutoSprint.shouldHoldSprint(disableInWater = true, inWater = false))
	}

	@Test
	fun `forces no sprint at all while the module is off`() {
		AutoSprint.disableInWaterSetting.on = false
		assertFalse(AutoSprint.forcesSprint(inWater = false))
		assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
		assertFalse(AutoSprint.suppressesDoubleTapSprint(inWater = true))
	}

	@Test
	fun `forces sprint on land and in water while the sub-toggle is off`() {
		enabled(disableInWater = false)
		assertTrue(AutoSprint.forcesSprint(inWater = false))
		assertTrue(AutoSprint.forcesSprint(inWater = true))
	}

	@Test
	fun `forces sprint on land but not in water while the sub-toggle is on`() {
		enabled(disableInWater = true)
		assertTrue(AutoSprint.forcesSprint(inWater = false))
		assertFalse(AutoSprint.forcesSprint(inWater = true))
	}

	@Test
	fun `stops the swim sprint it started on land`() {
		enabled(disableInWater = true)
		AutoSprint.forcesSprint(inWater = false)
		assertTrue(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
	}

	@Test
	fun `spares a swim sprint the player is holding the sprint key for`() {
		enabled(disableInWater = true)
		AutoSprint.forcesSprint(inWater = false)
		assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = true))
	}

	@Test
	fun `keeps sparing that sprint across ticks that never reach a sprint start`() {
		enabled(disableInWater = true)
		AutoSprint.forcesSprint(inWater = false)
		repeat(3) { assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = true)) }
		assertTrue(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
	}

	@Test
	fun `spares a sprint that started in water where it stood down`() {
		enabled(disableInWater = true)
		AutoSprint.forcesSprint(inWater = true)
		assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
	}

	@Test
	fun `never stops a swim sprint while the sub-toggle is off`() {
		enabled(disableInWater = false)
		AutoSprint.forcesSprint(inWater = false)
		assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
	}

	@Test
	fun `forgets the sprint it started once the module is disabled`() {
		enabled(disableInWater = true)
		AutoSprint.forcesSprint(inWater = false)
		AutoSprint.setEnabled(false)
		AutoSprint.setEnabled(true)
		assertFalse(AutoSprint.stopsForcedSwimSprint(playerHoldingSprint = false))
	}

	@Test
	fun `suppresses the double tap sprint only in water with the sub-toggle on`() {
		enabled(disableInWater = true)
		assertTrue(AutoSprint.suppressesDoubleTapSprint(inWater = true))
		assertFalse(AutoSprint.suppressesDoubleTapSprint(inWater = false))

		AutoSprint.disableInWaterSetting.on = false
		assertFalse(AutoSprint.suppressesDoubleTapSprint(inWater = true))
	}
}
