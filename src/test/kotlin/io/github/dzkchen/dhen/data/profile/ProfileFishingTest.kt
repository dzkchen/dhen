package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileFishingTest {
	@Test
	fun `everything reeled in comes back split by what it was`() {
		val fished = decode(MEMBER).itemsFished

		assertEquals(1000, fished.total)
		assertEquals(700, fished.normal)
		assertEquals(200, fished.treasure)
		assertEquals(50, fished.largeTreasure)
		assertEquals(50, fished.trophyFish)
	}

	@Test
	fun `the treasure and the festival sharks come off two other sections entirely`() {
		val fishing = decode(MEMBER)

		assertEquals(42, fishing.treasuresCaught)
		assertEquals(12, fishing.festivalSharksKilled)
	}

	@Test
	fun `every trophy fish is counted under the id and tier the API sends`() {
		val counts = decode(MEMBER).trophyCounts

		assertEquals(5, counts.getValue("sulphur_skitter_bronze"))
		assertEquals(2, counts.getValue("sulphur_skitter_silver"))
		assertEquals(10, counts.getValue("blobfish_bronze"))
	}

	@Test
	fun `the running total is its own number rather than a row among the fish`() {
		val fishing = decode(MEMBER)

		assertEquals(100, fishing.trophiesCaught)
		assertTrue("total_caught" !in fishing.trophyCounts)
		assertTrue("last_caught" !in fishing.trophyCounts)
		assertTrue("rewards" !in fishing.trophyCounts)
	}

	@Test
	fun `the last one caught comes apart into the fish and the tier it was`() {
		val last = decode(MEMBER).lastTrophy!!

		assertEquals("BLOBFISH", last.type)
		assertEquals("BRONZE", last.tier)
	}

	@Test
	fun `a last catch with no tier on it is reported as no catch at all`() {
		assertNull(decode("""{"trophy_fish":{"last_caught":"BLOBFISH"}}""").lastTrophy)
		assertNull(decode("""{"trophy_fish":{"total_caught":3}}""").lastTrophy)
	}

	@Test
	fun `a reward tier never reached is left out rather than listed as a zero`() {
		assertEquals(listOf(1, 2, 3), decode(MEMBER).trophyRewards)
	}

	@Test
	fun `a trophy entry that is not a number is skipped rather than counted as none`() {
		val member = """{"trophy_fish":{"blobfish_gold":{"caught":4},"blobfish_bronze":6}}"""

		assertEquals(mapOf("blobfish_bronze" to 6), decode(member).trophyCounts)
	}

	@Test
	fun `a player who has fished but never landed a trophy still decodes`() {
		val fishing = decode("""{"player_data":{"fishing_treasure_caught":3}}""")

		assertEquals(3, fishing.treasuresCaught)
		assertEquals(0, fishing.trophiesCaught)
		assertTrue(fishing.trophyCounts.isEmpty())
		assertTrue(fishing.trophyRewards.isEmpty())
	}

	@Test
	fun `a player who has never held a rod decodes to nothing`() {
		val member = """{"player_data":{"experience":{"SKILL_MINING":5}},"leveling":{"experience":2}}"""

		assertNull(FishingProfiles.of(DataFixture.json(member)))
	}

	private fun decode(member: String): FishingProfile = FishingProfiles.of(DataFixture.json(member))!!

	private companion object {
		private val MEMBER = """{
			"trophy_fish": {
				"sulphur_skitter_bronze": 5,
				"sulphur_skitter_silver": 2,
				"blobfish_bronze": 10,
				"last_caught": "BLOBFISH/BRONZE",
				"total_caught": 100,
				"rewards": [1, 0, 2, 3]
			},
			"player_data": {"fishing_treasure_caught": 42},
			"leveling": {"fishing_festival_sharks_killed": 12},
			"player_stats": {"items_fished": {
				"total": 1000, "normal": 700, "treasure": 200, "large_treasure": 50, "trophy_fish": 50
			}}
		}"""
	}
}
