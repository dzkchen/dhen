package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
		val tree = decode(MEMBER).tree

		assertEquals(300.0, tree.experience)
		assertEquals(3, tree.level)
		assertEquals(3, tree.maxLevel)
	}

	@Test
	fun `powder available is what was collected less what the selected tree spent`() {
		val mithril = decode(MEMBER).powders.getValue(Powder.MITHRIL)

		assertEquals(1000L, mithril.collected)
		assertEquals(250L, mithril.spent)
		assertEquals(750L, mithril.available)
	}

	@Test
	fun `a powder the selected tree never spent still reports everything collected`() {
		val powders = decode(MEMBER).powders

		assertEquals(500L, powders.getValue(Powder.GEMSTONE).available)
		assertEquals(0L, powders.getValue(Powder.GLACITE).available)
	}

	@Test
	fun `every slot's powder spending is readable, not only the selected slot's`() {
		val mithril = decode(MEMBER).powders.getValue(Powder.MITHRIL)

		assertEquals(100L, mithril.spentBySlot.getValue(1))
		assertEquals(250L, mithril.spentBySlot.getValue(2))
		assertEquals(0L, mithril.spentBySlot.getValue(5))
	}

	@Test
	fun `the perks are the selected tree's, not the first tree's`() {
		val tree = decode(MEMBER).tree

		assertEquals(2, tree.selectedSlot)
		assertEquals(mapOf("mining_fortune" to 7), tree.selected.perks)
		assertEquals("maniac_miner", tree.selected.selectedAbility)
	}

	@Test
	fun `the four slots the player is not using are decoded beside the one they are`() {
		val slots = decode(MEMBER).tree.slots

		assertEquals(SkillTrees.SLOTS.toSet(), slots.keys)
		assertEquals(mapOf("mining_speed" to 5), slots.getValue(1).perks)
		assertEquals("pickobulus", slots.getValue(1).selectedAbility)
		assertTrue(slots.getValue(4).perks.isEmpty())
		assertNull(slots.getValue(4).selectedAbility)
	}

	@Test
	fun `a toggle is never mistaken for a perk, and the disabled set is each slot's own`() {
		val slots = decode(MEMBER).tree.slots

		assertTrue(slots.values.none { slot -> slot.perks.keys.any { it.startsWith("toggle") } })
		assertEquals(setOf("mining_speed"), slots.getValue(1).disabledPerks)
		assertEquals(setOf("mining_fortune"), slots.getValue(2).disabledPerks)
	}

	@Test
	fun `a slot the reply never mentions falls back to the first one`() {
		val tree = decode("""{"skill_tree":{"selected_skill_tree_slot":{"mining":9}}}""").tree

		assertEquals(1, tree.selectedSlot)
	}

	@Test
	fun `the crystal hollows report state and the running placed and found counts`() {
		val crystals = decode(MEMBER).crystals

		assertEquals(setOf("jade_crystal", "amber_crystal"), crystals.keys)
		assertEquals("FOUND", crystals.getValue("jade_crystal").state)
		assertEquals(3, crystals.getValue("jade_crystal").totalPlaced)
		assertEquals(4, crystals.getValue("jade_crystal").totalFound)
	}

	@Test
	fun `a crystal with no state recorded reads as never found`() {
		val amber = decode(MEMBER).crystals.getValue("amber_crystal")

		assertEquals("NOT_FOUND", amber.state)
		assertEquals(0, amber.totalPlaced)
	}

	@Test
	fun `the forge reports what is in each numbered slot`() {
		val forge = decode(MEMBER).forge

		assertEquals(setOf(1, 3), forge.keys)
		assertEquals("REFINED_DIAMOND", forge.getValue(1).id)
		assertEquals(1_700_000_000_000L, forge.getValue(1).startedAt)
		assertTrue(forge.getValue(1).notified)
		assertEquals("PETS", forge.getValue(3).type)
		assertFalse(forge.getValue(3).notified)
	}

	@Test
	fun `the glacite tunnels report fossils, corpses and mineshafts`() {
		val glacite = decode(MEMBER).glacite

		assertEquals(setOf("CLAW", "SPINE"), glacite.fossilsDonated)
		assertEquals(mapOf("lapis" to 12, "umber" to 3), glacite.corpsesLooted)
		assertEquals(41, glacite.mineshaftsEntered)
	}

	@Test
	fun `a profile that has powder but has never opened the tree still reports its powder`() {
		val mining = decode("""{"mining_core":{"powder_mithril":40,"powder_spent_mithril":15}}""")

		assertEquals(1, mining.tree.selectedSlot)
		assertEquals(25L, mining.powders.getValue(Powder.MITHRIL).available)
		assertEquals(0.0, mining.tree.experience)
		assertTrue(mining.tree.selected.perks.isEmpty())
	}

	@Test
	fun `a profile that has only been down a mineshaft still decodes`() {
		val mining = decode("""{"glacite_player_data":{"mineshafts_entered":2}}""")

		assertEquals(2, mining.glacite.mineshaftsEntered)
		assertTrue(mining.crystals.isEmpty())
		assertTrue(mining.forge.isEmpty())
	}

	@Test
	fun `a profile that has never mined decodes to nothing rather than an empty shell`() {
		installRepo()

		assertNull(MiningProfiles.of(DataFixture.json("{}")))
	}

	@Test
	fun `a forager who has never mined gets no mining section, even though both trees share a document`() {
		installRepo()
		val forager = """{"skill_tree":{"experience":{"foraging":60},"nodes":{"foraging_2":{"forest_speed":1}}}}"""

		assertNull(MiningProfiles.of(DataFixture.json(forager)))
	}

	@Test
	fun `experience survives a repo that never loaded, even though no level can be read from it`() {
		val tree = MiningProfiles.of(DataFixture.json(MEMBER))!!.tree

		assertEquals(300.0, tree.experience)
		assertEquals(0, tree.level)
		assertEquals(0, tree.maxLevel)
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
				"powder_spent_gemstone": 20,
				"crystals": {
					"jade_crystal": {"state": "FOUND", "total_placed": 3, "total_found": 4},
					"amber_crystal": {}
				}
			},
			"forge": {
				"forge_processes": {
					"forge_1": {
						"1": {"type": "FORGE", "id": "REFINED_DIAMOND", "startTime": 1700000000000, "notified": true},
						"3": {"type": "PETS", "id": "AMMONITE", "startTime": 1700000000001, "notified": false}
					}
				}
			},
			"glacite_player_data": {
				"fossils_donated": ["CLAW", "SPINE"],
				"corpses_looted": {"lapis": 12, "umber": 3},
				"mineshafts_entered": 41
			}
		}"""
	}
}
