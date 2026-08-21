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

class SkyBlockProfileTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@AfterEach
	fun uninstall() {
		DataFixture.uninstall()
	}

	@Test
	fun `the envelope carries the id, the name, the game mode and the members still on the profile`() {
		val profile = decode()

		assertEquals("profile-1", profile.profileId)
		assertEquals("Apple", profile.cuteName)
		assertEquals(ProfileMode.IRONMAN, profile.mode)
		assertEquals(listOf(VIEWED, COOP), profile.members)
	}

	@Test
	fun `a co-op member who has been kicked is no longer listed`() {
		assertTrue(KICKED !in decode().members)
	}

	@Test
	fun `skill experience becomes a level against the repo's table`() {
		val skills = decode().skills

		assertEquals(675.0, skills.getValue(Skill.FARMING).experience)
		assertEquals(4, skills.getValue(Skill.FARMING).level)
		assertEquals(1, skills.getValue(Skill.COMBAT).level)
		assertEquals(2, skills.getValue(Skill.RUNECRAFTING).level)
		assertEquals(0, skills.getValue(Skill.HUNTING).level)
	}

	@Test
	fun `a skill the profile never mentions still gets a row so a table has no holes`() {
		val skills = decode().skills

		assertEquals(Skill.entries.size, skills.size)
		assertEquals(0.0, skills.getValue(Skill.ALCHEMY).experience)
	}

	@Test
	fun `farming and taming lift their own cap, and every other skill keeps the repo's`() {
		val skills = decode().skills

		assertEquals(5, skills.getValue(Skill.FARMING).maxLevel)
		assertEquals(52, skills.getValue(Skill.TAMING).maxLevel)
		assertEquals(5, skills.getValue(Skill.COMBAT).maxLevel)
		assertEquals(50, skills.getValue(Skill.HUNTING).maxLevel)
	}

	@Test
	fun `social is the whole co-op's, so one member alone reads lower than the profile does`() {
		val social = decode().skills.getValue(Skill.SOCIAL)

		assertEquals(70.0, social.experience)
		assertEquals(1, social.level)
	}

	@Test
	fun `a slayer's experience becomes a level and its boss kills come back by tier`() {
		val zombie = decode().slayers.getValue("zombie")

		assertEquals(100.0, zombie.experience)
		assertEquals(2, zombie.level)
		assertEquals(3, zombie.maxLevel)
		assertEquals(mapOf(0 to 4, 1 to 2), zombie.tierKills)
	}

	@Test
	fun `a slayer the repo has no table for reports no level rather than a wrong one`() {
		val spider = decode().slayers.getValue("spider")

		assertEquals(500.0, spider.experience)
		assertEquals(0, spider.level)
		assertEquals(0, spider.maxLevel)
	}

	@Test
	fun `collections are the whole co-op's, added together`() {
		assertEquals(mapOf("WHEAT" to 15L, "COBBLESTONE" to 3L), decode().collections)
	}

	@Test
	fun `a minion crafted by any member counts, and only its highest tier is kept`() {
		assertEquals(mapOf("COBBLESTONE" to 11, "MAGMA_CUBE" to 2), decode().minions)
	}

	@Test
	fun `bestiary kills and deaths come back on one record per mob`() {
		val bestiary = decode().bestiary

		assertEquals(500L, bestiary.getValue("zombie").kills)
		assertEquals(2L, bestiary.getValue("zombie").deaths)
		assertEquals(0L, bestiary.getValue("skeleton").kills)
		assertEquals(1L, bestiary.getValue("skeleton").deaths)
	}

	@Test
	fun `the dungeon numbers are the ones the dungeon slice already reports`() {
		installRepo()
		val reply = reply()
		val spine = SkyBlockProfiles.of(VIEWED, reply)!!.dungeons!!
		val slice = ProfileSlices.of(VIEWED, reply)!!.dungeons!!

		assertEquals(slice.catacombsLevel, spine.catacombsLevel)
		assertEquals(slice.secrets, spine.secrets)
		assertEquals(slice.runs, spine.runs)
		assertEquals(slice.catacombs.keys, spine.catacombs.keys)
	}

	@Test
	fun `a profile with none of these sections decodes to empty rather than throwing`() {
		installRepo()
		val profile = SkyBlockProfiles.of(VIEWED, bareReply())!!

		assertNull(profile.dungeons)
		assertEquals(ProfileMode.NORMAL, profile.mode)
		assertTrue(profile.slayers.isEmpty())
		assertTrue(profile.collections.isEmpty())
		assertTrue(profile.minions.isEmpty())
		assertTrue(profile.bestiary.isEmpty())
		assertEquals(0.0, profile.skills.getValue(Skill.FARMING).experience)
	}

	@Test
	fun `a player who is not on the profile decodes to nothing`() {
		installRepo()

		assertNull(SkyBlockProfiles.of(OTHER, reply()))
	}

	@Test
	fun `experience survives a repo that never loaded, even though no level can be read from it`() {
		val farming = SkyBlockProfiles.of(VIEWED, reply())!!.skills.getValue(Skill.FARMING)

		assertEquals(675.0, farming.experience)
		assertEquals(0, farming.level)
	}

	private fun decode(): SkyBlockProfile {
		installRepo()
		return SkyBlockProfiles.of(VIEWED, reply())!!
	}

	private fun installRepo() =
		DataFixture.installRepo(scope, home.resolve("repo"), constants = mapOf("leveling" to ConstantsFixture.LEVELING))

	private fun reply(): JsonObject = JsonParser.parseString(REPLY).asJsonObject

	private fun bareReply(): JsonObject =
		JsonParser.parseString("""{"profiles":[{"selected":true,"members":{"$VIEWED":{}}}]}""").asJsonObject

	private companion object {
		private const val VIEWED = "123e4567e89b12d3a456426614174000"
		private const val COOP = "00000000000000000000000000000001"
		private const val KICKED = "00000000000000000000000000000002"
		private const val OTHER = "99999999999999999999999999999999"

		private val REPLY = """{
			"success": true,
			"profiles": [
				{"profile_id":"profile-0","cute_name":"Banana","selected":false,"members":{}},
				{
					"profile_id":"profile-1",
					"cute_name":"Apple",
					"game_mode":"ironman",
					"selected":true,
					"members":{
						"$VIEWED":{
							"player_data":{
								"experience":{"SKILL_FARMING":675,"SKILL_COMBAT":50,"SKILL_RUNECRAFTING":150,"SKILL_SOCIAL":30},
								"crafted_generators":["COBBLESTONE_5","MAGMA_CUBE_2"]
							},
							"jacobs_contest":{"perks":{"farming_level_cap":2}},
							"pets_data":{"pet_care":{"pet_types_sacrificed":["ROC","HORSE"]}},
							"collection":{"WHEAT":10},
							"slayer":{"slayer_bosses":{
								"zombie":{"xp":100,"boss_kills_tier_0":4,"boss_kills_tier_1":2,"boss_attempts_tier_0":9},
								"spider":{"xp":500}
							}},
							"bestiary":{"kills":{"zombie":500},"deaths":{"zombie":2,"skeleton":1}},
							"dungeons":{
								"dungeon_types":{
									"catacombs":{
										"experience":1000,
										"tier_completions":{"0":5,"1":2,"2":3,"total":99},
										"fastest_time_s":{"1":1000,"2":2000},
										"fastest_time_s_plus":{"1":900}
									}
								},
								"player_classes":{"healer":{"experience":50}},
								"selected_dungeon_class":"healer",
								"secrets":42
							}
						},
						"$COOP":{
							"player_data":{
								"experience":{"SKILL_SOCIAL":40},
								"crafted_generators":["COBBLESTONE_11"]
							},
							"collection":{"WHEAT":5,"COBBLESTONE":3}
						},
						"$KICKED":{"profile":{"deletion_notice":{"timestamp":1}}}
					}
				}
			]
		}"""
	}
}
