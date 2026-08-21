package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileCrimsonIsleTest {
	@Test
	fun `the chosen faction and both reputations come back together`() {
		val isle = decode(MEMBER)

		assertEquals(Faction.BARBARIAN, isle.faction)
		assertEquals(1200, isle.reputation.getValue(Faction.BARBARIAN))
		assertEquals(0, isle.reputation.getValue(Faction.MAGE))
	}

	@Test
	fun `a reputation the player has driven below zero reads as zero rather than a debt`() {
		assertEquals(0, decode("""{"nether_island_player_data":{"mages_reputation":-400}}""").reputation.getValue(Faction.MAGE))
	}

	@Test
	fun `every kuudra tier the reply mentions carries its completions and its highest wave`() {
		val kuudra = decode(MEMBER).kuudra

		assertEquals(37, kuudra.getValue("none").completions)
		assertEquals(0, kuudra.getValue("none").highestWave)
		assertEquals(4, kuudra.getValue("infernal").completions)
		assertEquals(6, kuudra.getValue("infernal").highestWave)
	}

	@Test
	fun `a tier that only ever recorded a wave still appears, with no completions`() {
		val burning = decode(MEMBER).kuudra.getValue("burning")

		assertEquals(0, burning.completions)
		assertEquals(2, burning.highestWave)
	}

	@Test
	fun `a dojo discipline that was never attempted is not read as a zero score`() {
		val dojo = decode(MEMBER).dojo

		assertEquals(920, dojo.getValue("swing").points)
		assertEquals(74, dojo.getValue("swing").time)
		assertNull(dojo.getValue("mastery").points)
		assertNull(dojo.getValue("mastery").time)
	}

	@Test
	fun `a real zero on the dojo board stays a zero`() {
		assertEquals(0, decode(MEMBER).dojo.getValue("force").points)
	}

	@Test
	fun `a player who has set foot on the isle and done nothing else reads as zeros rather than throwing`() {
		val isle = decode("""{"nether_island_player_data":{}}""")

		assertNull(isle.faction)
		assertEquals(0, isle.reputation.getValue(Faction.MAGE))
		assertTrue(isle.kuudra.isEmpty())
		assertTrue(isle.dojo.isEmpty())
	}

	@Test
	fun `a profile that has never crossed to the crimson isle decodes to nothing`() {
		assertNull(CrimsonIsleProfiles.of(DataFixture.json("{}")))
	}

	private fun decode(member: String): CrimsonIsleProfile = CrimsonIsleProfiles.of(DataFixture.json(member))!!

	private companion object {
		private val MEMBER = """{
			"nether_island_player_data": {
				"selected_faction": "barbarians",
				"barbarians_reputation": 1200,
				"mages_reputation": 0,
				"kuudra_completed_tiers": {
					"none": 37,
					"infernal": 4,
					"highest_wave_infernal": 6,
					"highest_wave_burning": 2
				},
				"dojo": {
					"dojo_points_swing": 920,
					"dojo_time_swing": 74,
					"dojo_points_force": 0,
					"dojo_time_force": 120,
					"dojo_points_mastery": -1,
					"dojo_time_mastery": -1
				}
			}
		}"""
	}
}
