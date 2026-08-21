package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

class ProfileMiningTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@AfterEach
	fun uninstall() {
		DataFixture.uninstall()
	}

	@Test
	fun `heart of the mountain experience becomes a level against the repo's table`() {
		val mining = decode(MEMBER)

		assertEquals(300.0, mining.experience)
		assertEquals(3, mining.level)
		assertEquals(3, mining.maxLevel)
	}

	@Test
	fun `powder available is what was collected less what the selected tree spent`() {
		val powders = decode(MEMBER).powders

		assertEquals(1000L, powders.getValue(Powder.MITHRIL).collected)
		assertEquals(250L, powders.getValue(Powder.MITHRIL).spent)
		assertEquals(750L, powders.getValue(Powder.MITHRIL).available)
	}

	@Test
	fun `a powder the selected tree never spent still reports everything collected`() {
		val powders = decode(MEMBER).powders

		assertEquals(500L, powders.getValue(Powder.GEMSTONE).available)
		assertEquals(0L, powders.getValue(Powder.GLACITE).available)
	}

	@Test
	fun `the perks are the selected tree's, not the first tree's`() {
		val mining = decode(MEMBER)

		assertEquals(2, mining.selectedTree)
		assertEquals(mapOf("mining_fortune" to 7), mining.perks)
		assertEquals("maniac_miner", mining.selectedAbility)
	}

	@Test
	fun `a toggle is never mistaken for a perk, and the disabled set is the selected tree's own`() {
		val mining = decode(MEMBER)

		assertTrue(mining.perks.keys.none { it.startsWith("toggle") })
		assertEquals(setOf("mining_fortune"), mining.disabledPerks)
	}

	@Test
	fun `a profile that has powder but has never opened the tree still reports its powder`() {
		val mining = decode("""{"mining_core":{"powder_mithril":40,"powder_spent_mithril":15}}""")

		assertEquals(1, mining.selectedTree)
		assertEquals(25L, mining.powders.getValue(Powder.MITHRIL).available)
		assertEquals(0.0, mining.experience)
		assertTrue(mining.perks.isEmpty())
	}

	@Test
	fun `a profile that has never mined decodes to nothing rather than an empty shell`() {
		installRepo()

		assertNull(MiningProfiles.of(DataFixture.json("{}")))
	}

	@Test
	fun `experience survives a repo that never loaded, even though no level can be read from it`() {
		val mining = MiningProfiles.of(DataFixture.json(MEMBER))!!

		assertEquals(300.0, mining.experience)
		assertEquals(0, mining.level)
		assertEquals(0, mining.maxLevel)
	}

	private fun decode(member: String): MiningProfile {
		installRepo()
		return MiningProfiles.of(DataFixture.json(member))!!
	}

	private fun installRepo() =
		DataFixture.installRepo(scope, home.resolve("repo"), constants = mapOf("leveling" to ConstantsFixture.LEVELING))


	private companion object {
		private val MEMBER = """{
			"skill_tree": {
				"experience": {"mining": 300},
				"selected_skill_tree_slot": {"mining": 2},
				"nodes": {
					"mining": {"mining_speed": 5, "toggle_mining_speed": false},
					"mining_2": {"mining_fortune": 7, "toggle_mining_fortune": false, "toggle_mining_speed": true}
				},
				"selected_ability": {"mining": "pickobulus", "mining_2": "maniac_miner"}
			},
			"mining_core": {
				"powder_mithril": 1000,
				"powder_spent_mithril": 100,
				"powder_spent_mithril_2": 250,
				"powder_gemstone": 500,
				"powder_spent_gemstone": 20
			}
		}"""
	}
}
