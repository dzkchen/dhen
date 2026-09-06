package io.github.dzkchen.dhen.data.pet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KatDialogTest {
	@Test
	fun `only Kat's own dialog is followed`() {
		assertEquals("Hello there!", KatDialog.spokenByKat("[NPC] Kat: Hello there!"))
		assertNull(KatDialog.spokenByKat("[NPC] Jerry: Hello there!"))
		assertNull(KatDialog.spokenByKat("Kat: Hello there!"))
		assertNull(KatDialog.spokenByKat("Player: [NPC] Kat: Hello there!"))
	}

	@Test
	fun `an upgrade start carries the pet and its target rarity`() {
		val upgrade = KatDialog.upgradeStart("I'll get your Blue Whale upgraded to LEGENDARY in no time!")!!

		assertEquals("Blue Whale", upgrade.pet)
		assertEquals("LEGENDARY", upgrade.rarity)
	}

	@Test
	fun `a curly apostrophe is still Kat`() {
		assertNotNull(KatDialog.upgradeStart("I’ll get your Megalodon upgraded to Epic in no time."))
		assertNotNull(KatDialog.reminderStart("I’ll remind you when your Megalodon is done!"))
	}

	@Test
	fun `Hypixel's houre typo still parses as an hour`() {
		assertEquals(3_600_000L, KatDialog.parseDuration("1 houre"))
		assertEquals(7_200_000L, KatDialog.parseDuration("2 houres"))
		assertEquals(3_600_000L, KatDialog.parseDuration("1 hour"))
	}

	@Test
	fun `a duration adds up every part it finds`() {
		assertEquals(
			2 * 86_400_000L + 5 * 3_600_000L,
			KatDialog.duration("Come back in 2 days 5 hours to pick it up!")
		)
		assertEquals(90_000L, KatDialog.duration("I'll remind you in 1 minute 30 seconds!"))
	}

	@Test
	fun `flower bouquet and reset are the three special lines`() {
		assertEquals(KatSpecial.FLOWER, KatDialog.special("A flower? For me? How sweet!"))
		assertEquals(KatSpecial.BOUQUET, KatDialog.special("A bouquet? For me? How sweet!"))
		assertEquals(
			KatSpecial.RESET,
			KatDialog.special("If you have any other pets you'd like to upgrade, you know where to find me!")
		)
		assertNull(KatDialog.special("A flower? For me?"))
	}

	@Test
	fun `the flower and bouquet reductions are the source's`() {
		assertEquals(86_400_000L, KatDialog.FLOWER_REDUCTION_MS)
		assertEquals(432_000_000L, KatDialog.BOUQUET_REDUCTION_MS)
	}
}
