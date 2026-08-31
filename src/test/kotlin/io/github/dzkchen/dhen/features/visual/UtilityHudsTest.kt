package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.ServerClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UtilityHudsTest {
	private val sample = UtilitySample()

	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in UtilityHuds.settings) setting.reset()
		UtilityHuds.clear()
		ServerClock.reset()
		sample.millis = 0L
		sample.epochMillis = 0L
		sample.nanos = 0L
		sample.fps = 0
		sample.inDungeon = false
		sample.localServer = false
		sample.zoneOffsetMillis = 0L
	}

	@Test
	fun `the module declares every setting and movable readout`() {
		assertEquals("Utility HUDs", UtilityHuds.name)
		assertEquals(Category.VISUAL, UtilityHuds.category)
		assertEquals(
			listOf("FPS", "TPS", "Ping", "CPS", "Clock", "Server Freeze", "Queue Estimate"),
			UtilityHuds.hudElements.map { it.name }
		)
		assertEquals(
			listOf(
				"FPS",
				"FPS Color",
				"TPS",
				"TPS Color",
				"Ping",
				"Ping Color",
				"CPS",
				"CPS Color",
				"Clock",
				"Show Seconds",
				"Clock Color",
				"Server Freeze",
				"Server Freeze Color",
				"Threshold",
				"Only in Dungeons",
				"Queue Estimate",
				"Queue Estimate Color"
			),
			UtilityHuds.settings.map { it.name }
		)
	}

	@Test
	fun `every readout ships on so switching the module on shows something`() {
		assertTrue(UtilityHuds.fpsSetting.on)
		assertTrue(UtilityHuds.tpsSetting.on)
		assertTrue(UtilityHuds.pingSetting.on)
		assertTrue(UtilityHuds.cpsSetting.on)
		assertTrue(UtilityHuds.clockSetting.on)
		assertTrue(UtilityHuds.freezeSetting.on)
		assertTrue(UtilityHuds.queueSetting.on)
		assertFalse(UtilityHuds.clockSecondsSetting.on)
	}

	@Test
	fun `the seven readouts declare separate resting places`() {
		val places = UtilityHuds.hudElements.map { Triple(it.anchor, it.offsetX, it.offsetY) }
		assertEquals(places.size, places.toSet().size)
	}

	@Test
	fun `frame rate and tick rate read the live clocks`() {
		sample.fps = 144
		ServerClock.timeSynced(0L)
		ServerClock.timeSynced(2_000_000_000L)
		UtilityHuds.refresh(sample)

		assertEquals("144 fps", UtilityHuds.fpsElement.liveText)
		assertEquals("TPS: 10.0", UtilityHuds.tpsElement.liveText)
	}

	@Test
	fun `a readout whose sub-toggle is off says nothing`() {
		sample.fps = 60
		UtilityHuds.refresh(sample)
		assertEquals("60 fps", UtilityHuds.fpsElement.liveText)

		UtilityHuds.fpsSetting.on = false
		UtilityHuds.refresh(sample)

		assertEquals("", UtilityHuds.fpsElement.liveText)
		assertFalse(UtilityHuds.fpsElement.contentAvailable(editing = false))
		assertFalse(UtilityHuds.fpsElement.contentAvailable(editing = true))
	}

	@Test
	fun `the editor shows example text for a readout with nothing live`() {
		UtilityHuds.refresh(sample)

		assertEquals("", UtilityHuds.freezeElement.liveText)
		assertTrue(UtilityHuds.freezeElement.contentAvailable(editing = true))
		assertEquals("567ms", UtilityHuds.freezeElement.shownText(editing = true))
		assertEquals("Queue: 3m 20s", UtilityHuds.queueElement.shownText(editing = true))
	}

	@Test
	fun `clicks count inside a one second window and expire out of it`() {
		ClickRate.pressed(ClickRate.LEFT_BUTTON, 1_000L)
		ClickRate.pressed(ClickRate.LEFT_BUTTON, 1_400L)
		ClickRate.pressed(ClickRate.RIGHT_BUTTON, 1_600L)
		sample.millis = 1_800L
		UtilityHuds.refresh(sample)
		assertEquals("2 | 1 CPS", UtilityHuds.cpsElement.liveText)

		sample.millis = 2_300L
		UtilityHuds.refresh(sample)
		assertEquals("1 | 1 CPS", UtilityHuds.cpsElement.liveText)

		sample.millis = 5_000L
		UtilityHuds.refresh(sample)
		assertEquals("0 | 0 CPS", UtilityHuds.cpsElement.liveText)
	}

	@Test
	fun `a middle click is not a click per second`() {
		ClickRate.pressed(ClickRate.LEFT_BUTTON, 1_000L)
		ClickRate.pressed(ClickRate.RIGHT_BUTTON, 1_000L)
		ClickRate.pressed(MIDDLE_BUTTON, 1_000L)

		assertEquals(1, ClickRate.count(ClickRate.LEFT_BUTTON, 1_000L))
		assertEquals(1, ClickRate.count(ClickRate.RIGHT_BUTTON, 1_000L))
	}

	@Test
	fun `the clock renders local time with and without seconds`() {
		sample.epochMillis = 52_327_000L
		sample.zoneOffsetMillis = 0L
		UtilityHuds.refresh(sample)
		assertEquals("14:32", UtilityHuds.clockElement.liveText)

		UtilityHuds.clockSecondsSetting.on = true
		UtilityHuds.refresh(sample)
		assertEquals("14:32:07", UtilityHuds.clockElement.liveText)
	}

	@Test
	fun `the clock reads wall time, never the monotonic timer the other readouts use`() {
		sample.millis = 408_709_898L
		sample.epochMillis = 52_327_000L
		sample.zoneOffsetMillis = 0L
		UtilityHuds.refresh(sample)

		assertEquals("14:32", UtilityHuds.clockElement.liveText)
	}

	@Test
	fun `the clock follows the zone offset across midnight`() {
		sample.epochMillis = 3_600_000L
		sample.zoneOffsetMillis = -7_200_000L
		UtilityHuds.refresh(sample)

		assertEquals("23:00", UtilityHuds.clockElement.liveText)
	}

	@Test
	fun `the freeze readout waits for the threshold and stays off the integrated server`() {
		UtilityHuds.freezeDungeonsSetting.on = false
		ServerClock.serverTicked(1_000_000_000L)

		sample.nanos = 1_400_000_000L
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.freezeElement.liveText)

		sample.nanos = 1_560_000_000L
		UtilityHuds.refresh(sample)
		assertEquals("560ms", UtilityHuds.freezeElement.liveText)

		sample.localServer = true
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.freezeElement.liveText)
	}

	@Test
	fun `the freeze readout honours only in dungeons and needs a first server tick`() {
		sample.nanos = 5_000_000_000L
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.freezeElement.liveText)

		ServerClock.serverTicked(1_000_000_000L)
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.freezeElement.liveText)

		sample.inDungeon = true
		UtilityHuds.refresh(sample)
		assertEquals("4000ms", UtilityHuds.freezeElement.liveText)
	}

	@Test
	fun `ping averages its samples and is asked for again on an interval`() {
		assertTrue(PingProbe.due(0L))
		assertFalse(PingProbe.due(50L))

		sample.millis = 100L
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.pingElement.liveText)

		PingProbe.pong(140L, 40L)
		PingProbe.pong(260L, 60L)
		sample.millis = 300L
		UtilityHuds.refresh(sample)

		assertEquals("Ping: 150ms", UtilityHuds.pingElement.liveText)
	}

	@Test
	fun `a lost pong stops blocking the next request after the timeout`() {
		assertTrue(PingProbe.due(0L))
		for (tick in 1..80) PingProbe.due(tick.toLong())
		assertFalse(PingProbe.due(100L))

		PingProbe.reset()
		assertTrue(PingProbe.due(0L))
		for (tick in 1..80) PingProbe.due(tick.toLong())
		assertTrue(PingProbe.due(20_000L))
	}

	@Test
	fun `the queue estimate fits a falling position and counts down`() {
		QueueEstimate.subtitle("You are #100 in the queue!", 0L)
		sample.millis = 0L
		UtilityHuds.refresh(sample)
		assertEquals("Queue: collecting", UtilityHuds.queueElement.liveText)

		QueueEstimate.subtitle("You are #90 in the queue!", 10_000L)
		QueueEstimate.subtitle("You are #80 in the queue!", 20_000L)
		sample.millis = 20_000L
		UtilityHuds.refresh(sample)

		assertEquals("Queue: 1m 20s", UtilityHuds.queueElement.liveText)
	}

	@Test
	fun `a growing queue and a foreign subtitle produce no estimate`() {
		QueueEstimate.subtitle("You are #10 in the queue!", 0L)
		QueueEstimate.subtitle("You are #20 in the queue!", 10_000L)
		sample.millis = 10_000L
		UtilityHuds.refresh(sample)
		assertEquals("Queue: collecting", UtilityHuds.queueElement.liveText)

		QueueEstimate.subtitle("Welcome to Hypixel!", 11_000L)
		UtilityHuds.refresh(sample)
		assertEquals("", UtilityHuds.queueElement.liveText)
		assertFalse(UtilityHuds.queueElement.contentAvailable(editing = false))
	}

	@Test
	fun `a stale queue clears itself`() {
		QueueEstimate.subtitle("You are #10 in the queue!", 0L)
		QueueEstimate.tick(20_000L)
		assertTrue(QueueEstimate.collecting)

		QueueEstimate.tick(40_000L)
		assertFalse(QueueEstimate.collecting)
	}

	@Test
	fun `switching the module off forgets what its readouts said`() {
		sample.fps = 30
		UtilityHuds.refresh(sample)
		assertEquals("30 fps", UtilityHuds.fpsElement.liveText)

		UtilityHuds.clear()

		assertEquals("", UtilityHuds.fpsElement.liveText)
		assertFalse(UtilityHuds.fpsElement.contentAvailable(editing = false))
	}

	@Test
	fun `an unchanged readout keeps the same string instance`() {
		sample.fps = 30
		UtilityHuds.refresh(sample)
		val first = UtilityHuds.fpsElement.liveText
		UtilityHuds.refresh(sample)

		assertSame(first, UtilityHuds.fpsElement.liveText)
	}

	private companion object {
		const val MIDDLE_BUTTON = 2
	}
}
