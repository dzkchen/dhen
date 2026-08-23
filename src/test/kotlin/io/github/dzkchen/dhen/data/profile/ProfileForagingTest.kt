package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProfileForagingTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@AfterEach
	fun uninstall() {
		DataFixture.uninstall()
	}

	@Test
	fun `heart of the forest levels against the same repo table as the mountain`() {
		val tree = decode(MEMBER).tree

		assertEquals(60.0, tree.experience)
		assertEquals(2, tree.level)
		assertEquals(2, tree.maxLevel)
	}

	@Test
	fun `the forest tree keeps its own slot, not the mining tree's`() {
		val member = decode(MEMBER)

		assertEquals(3, member.tree.selectedSlot)
		assertEquals(mapOf("forest_fortune" to 4), member.tree.selected.perks)
		assertEquals("gone_with_the_wind", member.tree.selected.selectedAbility)
		assertEquals(setOf("forest_fortune"), member.tree.selected.disabledPerks)
	}

	@Test
	fun `whispers spend per slot, and the desert reads beside the forest rather than under it`() {
		val whispers = decode(MEMBER).whispers

		assertEquals(900L, whispers.getValue(Whisper.FOREST).collected)
		assertEquals(150L, whispers.getValue(Whisper.FOREST).spent)
		assertEquals(750L, whispers.getValue(Whisper.FOREST).available)
		assertEquals(40L, whispers.getValue(Whisper.DESERT).collected)
		assertEquals(40L, whispers.getValue(Whisper.DESERT).available)
	}

	@Test
	fun `slot one's whisper spending is keyed the same way as every other slot's`() {
		val forest = decode(MEMBER).whispers.getValue(Whisper.FOREST)

		assertEquals(25L, forest.spentBySlot.getValue(1))
		assertEquals(150L, forest.spentBySlot.getValue(3))
	}

	@Test
	fun `the daily counters carry the day they belong to, so yesterday's total is not read as today's`() {
		val daily = decode(MEMBER).daily

		assertEquals(37, daily.treesCut)
		assertEquals(412, daily.treesCutDay)
		assertEquals(setOf("FIG_LOG", "MANGROVE_LOG"), daily.logsCut)
		assertEquals(411, daily.logsCutDay)
		assertEquals(2, daily.gifts)
	}

	@Test
	fun `the gift counts and the milestones claimed against them stay apart`() {
		val foraging = decode(MEMBER)

		assertEquals(mapOf("FIG" to 14, "MANGROVE" to 6), foraging.treeGifts)
		assertEquals(mapOf("FIG" to 2), foraging.claimedGiftMilestones)
	}

	@Test
	fun `the personal bests and the fish family come off the foraging section`() {
		val foraging = decode(MEMBER)

		assertEquals(mapOf("agatha" to 9, "FIG_LOG" to 120), foraging.personalBests)
		assertEquals(setOf("SPRINGY", "SLIPPERY"), foraging.fishFamily)
	}

	@Test
	fun `a profile that has swung an axe but never opened the tree still decodes`() {
		val foraging = decode("""{"foraging_core":{"daily_trees_cut":4}}""")

		assertEquals(1, foraging.tree.selectedSlot)
		assertEquals(4, foraging.daily.treesCut)
		assertEquals(0L, foraging.whispers.getValue(Whisper.FOREST).collected)
		assertTrue(foraging.fishFamily.isEmpty())
	}

	@Test
	fun `a profile that has never foraged decodes to nothing rather than an empty shell`() {
		installRepo()

		assertNull(ForagingProfiles.of(DataFixture.json("{}")))
	}

	@Test
	fun `a miner who has never foraged gets no foraging section, even though both trees share a document`() {
		installRepo()
		val miner = """{"skill_tree":{"experience":{"mining":300}},"mining_core":{"powder_mithril":40}}"""

		assertNull(ForagingProfiles.of(DataFixture.json(miner)))
	}

	private fun decode(member: String): ForagingProfile {
		installRepo()
		return ForagingProfiles.of(DataFixture.json(member))!!
	}

	private fun installRepo() =
		DataFixture.installRepo(scope, home.resolve("repo"), constants = mapOf("leveling" to ConstantsFixture.LEVELING))


	private companion object {
		private const val MEMBER = """{
			"skill_tree": {
				"experience": {"mining": 300, "foraging": 60},
				"selected_skill_tree_slot": {"mining": 2, "foraging": 3},
				"nodes": {
					"foraging": {"forest_speed": 2},
					"foraging_3": {"forest_fortune": 4, "toggle_forest_fortune": false}
				},
				"selected_ability": {"foraging": "tree_hugger", "foraging_3": "gone_with_the_wind"}
			},
			"foraging_core": {
				"daily_trees_cut": 37,
				"daily_trees_cut_day": 412,
				"daily_log_cut": ["FIG_LOG", "MANGROVE_LOG"],
				"daily_log_cut_day": 411,
				"daily_gifts": 2,
				"whispers": {
					"forest": {"total": 900, "1": {"spent": 25}, "3": {"spent": 150}},
					"desert": {"total": 40}
				}
			},
			"foraging": {
				"starlyn": {"personal_bests": {"agatha": 9, "FIG_LOG": 120}},
				"fish_family": ["SPRINGY", "SLIPPERY"],
				"tree_gifts": {"FIG": 14, "MANGROVE": 6, "milestone_tier_claimed": {"FIG": 2}}
			}
		}"""
	}
}
