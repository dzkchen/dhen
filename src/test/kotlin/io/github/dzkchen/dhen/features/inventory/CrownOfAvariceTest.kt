package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrownOfAvariceTest {
	@BeforeEach
	fun reset() {
		for (setting in CrownOfAvarice.settings) setting.reset()
		CrownOfAvarice.forget()
	}

	@Test
	fun `the module declares the six controls its source exposes beside its own toggle`() {
		assertEquals("Crown of Avarice", CrownOfAvarice.name)
		assertEquals(Category.ECONOMY, CrownOfAvarice.category)
		assertEquals(
			listOf(
				"Counter Format",
				"Coins Per Hour Format",
				"AFK Pause Time",
				"Session Active Timer",
				"Reset on World Change",
				"Tracker Text"
			),
			CrownOfAvarice.settings.map { it.name }
		)
		assertEquals(
			listOf("Coins Per Hour", "Time Until Max", "Last Coins Gained", "Coins This Session", "Session Time"),
			CrownOfAvarice.trackerSetting.default
		)
	}

	@Test
	fun `a stopwatch counts only while it is running`() {
		val session = CoinSession()
		assertTrue(session.paused)
		assertEquals(0L, session.durationMillis(1_000L))
		session.start(1_000L)
		assertFalse(session.paused)
		assertEquals(500L, session.durationMillis(1_500L))
		session.pause(2_000L, revert = false)
		assertTrue(session.paused)
		assertEquals(1_000L, session.durationMillis(9_000L))
	}

	@Test
	fun `a reverted pause throws away the lap that timed out`() {
		val session = CoinSession()
		session.start(0L)
		session.lap(4_000L)
		session.pause(9_000L, revert = true)
		assertEquals(4_000L, session.durationMillis(60_000L))
	}

	@Test
	fun `a lap time is unbounded while the stopwatch is paused`() {
		val session = CoinSession()
		assertEquals(Long.MAX_VALUE, session.lapMillis(5_000L))
		session.start(1_000L)
		assertEquals(4_000L, session.lapMillis(5_000L))
	}

	@Test
	fun `an unchanged total leaves the last gain unrecorded`() {
		CrownOfAvarice.readCrown("still", 400L, 0L)
		CrownOfAvarice.readCrown("still", 400L, 1_000L)
		CrownOfAvarice.update(1_000L)
		assertTrue(CrownOfAvarice.element.hasLine("§aLast coins gained: §6§cnever"))
	}

	@Test
	fun `swapping to another crown rebases instead of counting its coins as earned`() {
		CrownOfAvarice.readCrown("first", 500_000L, 0L)
		CrownOfAvarice.readCrown("first", 600_000L, 1_000L)
		CrownOfAvarice.readCrown("second", 900_000_000L, 2_000L)
		CrownOfAvarice.update(3_000L)
		assertTrue(CrownOfAvarice.session.durationMillis(3_000L) > 0L)
		CrownOfAvarice.readCrown("second", 900_000_100L, 4_000L)
		assertTrue(CrownOfAvarice.element.hasLine("§aCoins this session: §6100,100"))
	}

	@Test
	fun `a manual reset reports a zero gain where an untouched counter reports never`() {
		CrownOfAvarice.reset()
		assertTrue(CrownOfAvarice.element.hasLine("§aLast coins gained: §60"))
	}

	@Test
	fun `a total that drops starts a fresh session`() {
		CrownOfAvarice.readCrown("drop", 1_000L, 0L)
		CrownOfAvarice.readCrown("drop", 5_000L, 1_000L)
		CrownOfAvarice.readCrown("drop", 2_000L, 2_000L)
		assertTrue(CrownOfAvarice.session.paused)
		assertTrue(CrownOfAvarice.element.hasLine("§aLast coins gained: §60"))
		assertTrue(CrownOfAvarice.element.hasLine("§aCoins this session: §60"))
	}

	@Test
	fun `no gain yet reads as never rather than zero`() {
		CrownOfAvarice.update(0L)
		assertTrue(CrownOfAvarice.element.hasLine("§aCoins this session: §60"))
		assertTrue(CrownOfAvarice.element.hasLine("§aLast coins gained: §6§cnever"))
	}

	@Test
	fun `a session that has not run long enough refuses to guess a rate`() {
		CrownOfAvarice.readCrown("rate", 1_000L, 0L)
		CrownOfAvarice.readCrown("rate", 2_000L, 1L)
		CrownOfAvarice.update(2L)
		assertTrue(CrownOfAvarice.element.hasLine("§aCoins Per Hour: §6Calculating..."))
		assertTrue(CrownOfAvarice.element.hasLine("§aTime until Max: §bCalculating..."))
	}
}
