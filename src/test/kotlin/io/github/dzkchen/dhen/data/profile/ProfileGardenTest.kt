package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProfileGardenTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@BeforeEach
	fun installRepo() {
		DataFixture.installRepo(
			scope,
			home.resolve("repo"),
			constants = mapOf("garden" to ConstantsFixture.GARDEN)
		)
	}

	@AfterEach
	fun uninstall() {
		DataFixture.uninstall()
	}

	@Test
	fun `garden experience becomes a level against the repo's table`() {
		val garden = garden()

		assertEquals(140.0, garden.experience)
		assertEquals(3, garden.level)
		assertEquals(4, garden.maxLevel)
	}

	@Test
	fun `a crop reports what was collected, the milestone that buys, and its upgrade tier`() {
		val wheat = garden().crops.getValue("WHEAT")

		assertEquals(100L, wheat.collected)
		assertEquals(2, wheat.milestone)
		assertEquals(4, wheat.upgrade)
	}

	@Test
	fun `a crop that was only ever upgraded still gets a row, so a table has no holes`() {
		val carrot = garden().crops.getValue("CARROT")

		assertEquals(0L, carrot.collected)
		assertEquals(0, carrot.milestone)
		assertEquals(1, carrot.upgrade)
	}

	@Test
	fun `a crop the repo has no milestone table for reports no milestone rather than a wrong one`() {
		assertEquals(0, garden().crops.getValue("CACTUS").milestone)
	}

	@Test
	fun `the unlocked plots come back in the order the profile lists them`() {
		assertEquals(listOf("beginner_1", "intermediate_3"), garden().unlockedPlots)
	}

	@Test
	fun `the composter reports its stores and every upgrade, including ones never bought`() {
		val composter = garden().composter

		assertEquals(1500.0, composter.organicMatter)
		assertEquals(300.0, composter.fuel)
		assertEquals(12.5, composter.compostUnits)
		assertEquals(3, composter.compostItems)
		assertEquals(6, composter.upgrades.getValue(ComposterUpgrade.SPEED))
		assertEquals(0, composter.upgrades.getValue(ComposterUpgrade.COST_REDUCTION))
	}

	@Test
	fun `an upgrade the game invents after this was written is ignored rather than throwing`() {
		val composter = decode("""{"garden":{"composter_data":{"upgrades":{"speed":2,"time_travel":9}}}}""").composter

		assertEquals(2, composter.upgrades.getValue(ComposterUpgrade.SPEED))
		assertEquals(ComposterUpgrade.entries.size, composter.upgrades.size)
	}

	@Test
	fun `a player who has never been to the garden decodes to an empty garden at level one`() {
		val garden = decode("""{"garden":{}}""")

		assertEquals(1, garden.level)
		assertTrue(garden.crops.isEmpty())
		assertTrue(garden.unlockedPlots.isEmpty())
		assertEquals(0.0, garden.composter.organicMatter)
	}

	@Test
	fun `a reply that carries no garden at all decodes to nothing`() {
		assertNull(GardenProfiles.of(DataFixture.json("""{"success":true}""")))
	}

	@Test
	fun `the medal cabinet counts every medal, including ones never won`() {
		val medals = farming().medals

		assertEquals(12, medals.getValue(ContestMedal.BRONZE))
		assertEquals(4, medals.getValue(ContestMedal.GOLD))
		assertEquals(0, medals.getValue(ContestMedal.DIAMOND))
		assertEquals(ContestMedal.entries.size, medals.size)
	}

	@Test
	fun `a claimed contest carries its crop, its placing and its medal`() {
		val contest = farming().contests.first { it.contestId == "285:11_5:WHEAT" }

		assertTrue(contest.isCrop("WHEAT"))
		assertFalse(contest.isCrop("CARROT_ITEM"))
		assertEquals(2500, contest.collected)
		assertEquals(3, contest.position)
		assertEquals(180, contest.participants)
		assertEquals(ContestMedal.GOLD, contest.medal)
	}

	@Test
	fun `a contest whose rewards were never claimed reports no placing rather than a zero one`() {
		val contest = farming().contests.first { it.contestId == "286:2_9:CARROT_ITEM" }

		assertTrue(contest.isCrop("CARROT_ITEM"))
		assertNull(contest.position)
		assertNull(contest.participants)
		assertNull(contest.medal)
	}

	@Test
	fun `the perks, personal bests, unique brackets and chips come off the member`() {
		val farming = farming()

		assertEquals(2, farming.farmingLevelCap)
		assertEquals(1, farming.doubleDrops)
		assertEquals(mapOf("WHEAT" to 2500, "CARROT_ITEM" to 900), farming.personalBests)
		assertEquals(setOf("WHEAT"), farming.uniqueBrackets.getValue(ContestMedal.GOLD))
		assertTrue(farming.uniqueBrackets.getValue(ContestMedal.DIAMOND).isEmpty())
		assertEquals(mapOf("cropshot" to 1, "hypercharge" to 3), farming.chips)
	}

	@Test
	fun `a player who has never entered a contest nor fitted a chip decodes to nothing`() {
		assertNull(FarmingProfiles.of(DataFixture.json("{}")))
	}

	private fun decode(reply: String = GARDEN): GardenProfile = GardenProfiles.of(DataFixture.json(reply))!!

	private fun garden(): GardenProfile = decode()

	private fun farming(): FarmingProfile = FarmingProfiles.of(DataFixture.json(MEMBER))!!


	private companion object {
		private val GARDEN = """{
			"success": true,
			"garden": {
				"garden_experience": 140,
				"unlocked_plots_ids": ["beginner_1", "intermediate_3"],
				"resources_collected": {"WHEAT": 100, "CACTUS": 60},
				"crop_upgrade_levels": {"WHEAT": 4, "CARROT": 1},
				"composter_data": {
					"organic_matter": 1500,
					"fuel_units": 300,
					"compost_units": 12.5,
					"compost_items": 3,
					"upgrades": {"speed": 6, "multi_drop": 2}
				}
			}
		}"""

		private val MEMBER = """{
			"player_data": {"garden_chips": {"cropshot": 1, "hypercharge": 3}},
			"jacobs_contest": {
				"medals_inv": {"bronze": 12, "silver": 7, "gold": 4},
				"perks": {"farming_level_cap": 2, "double_drops": 1},
				"unique_brackets": {"gold": ["WHEAT"], "bronze": ["WHEAT", "CARROT_ITEM"]},
				"personal_bests": {"WHEAT": 2500, "CARROT_ITEM": 900},
				"contests": {
					"285:11_5:WHEAT": {
						"collected": 2500,
						"claimed_rewards": true,
						"claimed_position": 3,
						"claimed_participants": 180,
						"claimed_medal": "gold"
					},
					"286:2_9:CARROT_ITEM": {"collected": 900}
				}
			}
		}"""
	}
}
