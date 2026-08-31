package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MaskTimersTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in MaskTimers.settings) setting.reset()
		MaskTimers.procNotificationSetting.value = false
		MaskTimers.readyNotificationSetting.value = false
		MaskTimers.clear()
		SkyBlockLocation.reset()
	}

	private fun inDungeon() = SkyBlockLocation.located("mini1A", true, "dungeon", "The Catacombs")

	private fun onHub() = SkyBlockLocation.located("mini1A", true, "hub", "Hub")

	private fun ticks(count: Int) {
		repeat(count) { MaskTimers.ticked() }
	}

	@Test
	fun `the module declares its settings and its two movable elements`() {
		assertEquals("Mask Timers", MaskTimers.name)
		assertEquals(Category.VISUAL, MaskTimers.category)
		assertEquals(
			listOf("Mask Timers", "Mask Invulnerability"),
			MaskTimers.hudElements.map { it.name }
		)
		assertEquals(
			listOf(
				"Style",
				"Dungeons Only",
				"Invulnerability Timers",
				"Proc Notification",
				"Ready Notification",
				"Bonzo Color",
				"Spirit Color",
				"Phoenix Color"
			),
			MaskTimers.settings.map { it.name }
		)
	}

	@Test
	fun `each mask carries the cooldown and invulnerability both source mods agree on`() {
		assertEquals(3600, Mask.BONZO.cooldownTicks)
		assertEquals(60, Mask.BONZO.invulnerabilityTicks)
		assertEquals(600, Mask.SPIRIT.cooldownTicks)
		assertEquals(60, Mask.SPIRIT.invulnerabilityTicks)
		assertEquals(1200, Mask.PHOENIX.cooldownTicks)
		assertEquals(80, Mask.PHOENIX.invulnerabilityTicks)
	}

	@Test
	fun `every proc line arms exactly its own mask`() {
		onHub()

		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		assertEquals(3600, Mask.BONZO.cooldownLeft)
		assertEquals(60, Mask.BONZO.invulnerabilityLeft)
		assertEquals(0, Mask.SPIRIT.cooldownLeft)

		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")
		assertEquals(600, Mask.SPIRIT.cooldownLeft)

		MaskTimers.chatted("Your Phoenix Pet saved you from certain death!")
		assertEquals(1200, Mask.PHOENIX.cooldownLeft)
	}

	@Test
	fun `the starred Bonzo prefix still arms the timer and a lookalike line does not`() {
		onHub()

		MaskTimers.chatted("Your ✪ Bonzo's Mask saved your life!")
		assertEquals(3600, Mask.BONZO.cooldownLeft)

		MaskTimers.clear()
		MaskTimers.chatted("Player said: Your Bonzo's Mask saved your life! lol")
		assertEquals(0, Mask.BONZO.cooldownLeft)
	}

	@Test
	fun `a proc outside SkyBlock is ignored`() {
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")

		assertEquals(0, Mask.BONZO.cooldownLeft)
	}

	@Test
	fun `Dungeons Only ignores a proc on the hub and takes it in the Catacombs`() {
		MaskTimers.dungeonsOnlySetting.value = true
		onHub()

		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		assertEquals(0, Mask.BONZO.cooldownLeft)

		inDungeon()
		assertSame(Island.CATACOMBS, SkyBlockLocation.island)
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		assertEquals(3600, Mask.BONZO.cooldownLeft)
	}

	@Test
	fun `proccing again while the timer runs restarts it at full`() {
		onHub()
		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")
		ticks(100)
		assertEquals(500, Mask.SPIRIT.cooldownLeft)

		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")

		assertEquals(600, Mask.SPIRIT.cooldownLeft)
		assertEquals(60, Mask.SPIRIT.invulnerabilityLeft)
	}

	@Test
	fun `switching the invulnerability overlay off leaves the countdown unarmed on the next proc`() {
		MaskTimers.invulnerabilitySetting.value = false
		onHub()

		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")

		assertEquals(600, Mask.SPIRIT.cooldownLeft)
		assertEquals(0, Mask.SPIRIT.invulnerabilityLeft)
	}

	@Test
	fun `the countdown only moves on server ticks inside SkyBlock`() {
		onHub()
		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")

		SkyBlockLocation.reset()
		ticks(10)
		assertEquals(600, Mask.SPIRIT.cooldownLeft)

		onHub()
		ticks(10)
		assertEquals(590, Mask.SPIRIT.cooldownLeft)
		assertEquals(50, Mask.SPIRIT.invulnerabilityLeft)
	}

	@Test
	fun `the ready latch starts closed and reopens one tick after the cooldown empties`() {
		onHub()
		assertTrue(Mask.SPIRIT.notifiedReady)
		ticks(5)
		assertTrue(Mask.SPIRIT.notifiedReady)

		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")
		ticks(600)
		assertEquals(0, Mask.SPIRIT.cooldownLeft)
		assertFalse(Mask.SPIRIT.notifiedReady)

		ticks(1)
		assertTrue(Mask.SPIRIT.notifiedReady)
	}

	@Test
	fun `a world change clears every mask`() {
		onHub()
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")

		MaskTimers.clear()

		assertEquals(0, Mask.BONZO.cooldownLeft)
		assertEquals(0, Mask.BONZO.invulnerabilityLeft)
		assertTrue(Mask.BONZO.notifiedReady)
	}

	@Test
	fun `the NoammAddons style lists only the masks on cooldown and shows one decimal`() {
		onHub()
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		ticks(3354)

		MaskTimers.refresh(false)

		val bonzo = MaskTimers.timersElement.rowFor(Mask.BONZO)
		assertTrue(bonzo.listed)
		assertEquals("12.3", bonzo.value)
		assertFalse(MaskTimers.timersElement.rowFor(Mask.SPIRIT).listed)
		assertTrue(MaskTimers.timersElement.contentAvailable(locationAllows = true, editing = false))
	}

	@Test
	fun `the Zyryon style lists every mask and shows two decimals`() {
		onHub()
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		ticks(3354)

		MaskTimers.styleSetting.value = "Zyryon"
		MaskTimers.refresh(false)

		assertEquals("12.30", MaskTimers.timersElement.rowFor(Mask.BONZO).value)
		val spirit = MaskTimers.timersElement.rowFor(Mask.SPIRIT)
		assertTrue(spirit.listed)
		assertEquals("Ready", spirit.value)
		assertFalse(spirit.worn)
	}

	@Test
	fun `nothing is listed while every mask is ready in the NoammAddons style`() {
		MaskTimers.refresh(false)

		assertFalse(MaskTimers.timersElement.contentAvailable(locationAllows = true, editing = false))
	}

	@Test
	fun `the editor preview fills every row even with no cooldown running`() {
		MaskTimers.refresh(true)

		val bonzo = MaskTimers.timersElement.rowFor(Mask.BONZO)
		assertTrue(bonzo.listed)
		assertEquals("90.0", bonzo.value)
		assertTrue(bonzo.worn)
		assertSame(Mask.BONZO, MaskTimers.invulnerabilityElement.shown)
		assertEquals("3.0", MaskTimers.invulnerabilityElement.value)
	}

	@Test
	fun `the invulnerability overlay follows the longest countdown and hides when it empties`() {
		onHub()
		MaskTimers.chatted("Second Wind Activated! Your Spirit Mask saved your life!")
		ticks(20)
		MaskTimers.chatted("Your Phoenix Pet saved you from certain death!")

		MaskTimers.refresh(false)
		assertSame(Mask.PHOENIX, MaskTimers.invulnerabilityElement.shown)
		assertEquals("4.0", MaskTimers.invulnerabilityElement.value)

		ticks(80)
		MaskTimers.refresh(false)
		assertNull(MaskTimers.invulnerabilityElement.shown)
	}

	@Test
	fun `switching the invulnerability overlay off hides a running countdown`() {
		onHub()
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		MaskTimers.refresh(false)
		assertSame(Mask.BONZO, MaskTimers.invulnerabilityElement.shown)

		MaskTimers.invulnerabilitySetting.value = false
		MaskTimers.refresh(false)

		assertNull(MaskTimers.invulnerabilityElement.shown)
	}

	@Test
	fun `the list stays off the screen outside SkyBlock even in the always-listing style`() {
		MaskTimers.styleSetting.value = "Zyryon"
		MaskTimers.refresh(false)

		assertTrue(MaskTimers.timersElement.contentAvailable(locationAllows = true, editing = false))
		assertFalse(MaskTimers.timersElement.contentAvailable(locationAllows = false, editing = false))
		assertTrue(MaskTimers.timersElement.contentAvailable(locationAllows = false, editing = true))
	}

	@Test
	fun `switching the module off and on again never reports a live cooldown as ready`() {
		onHub()
		MaskTimers.chatted("Your Bonzo's Mask saved your life!")
		ticks(100)

		MaskTimers.setEnabled(false)
		MaskTimers.setEnabled(true)

		assertEquals(3500, Mask.BONZO.cooldownLeft)
	}

	@Test
	fun `seconds round the way both source mods print them`() {
		val builder = StringBuilder()

		appendSeconds(builder, 3, false)
		assertEquals("0.2", builder.toString())

		builder.setLength(0)
		appendSeconds(builder, 3, true)
		assertEquals("0.15", builder.toString())

		builder.setLength(0)
		appendSeconds(builder, 1200, true)
		assertEquals("60.00", builder.toString())
	}
}
