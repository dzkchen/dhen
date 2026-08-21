package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import java.util.Locale

private const val BOSS_KILLS = "boss_kills_tier_"

enum class ProfileMode { NORMAL, IRONMAN, STRANDED, BINGO }

enum class Skill {
	FARMING, MINING, COMBAT, FORAGING, FISHING, ENCHANTING,
	ALCHEMY, TAMING, CARPENTRY, RUNECRAFTING, SOCIAL, HUNTING;

	internal val repoKey: String = name.lowercase(Locale.ROOT)

	internal val apiKey: String = "SKILL_" + name
}

class SkillProgress internal constructor(val experience: Double, val level: Int, val maxLevel: Int)

class SlayerProgress internal constructor(
	val experience: Double,
	val level: Int,
	val maxLevel: Int,
	val tierKills: Map<Int, Int>
)

class BestiaryEntry internal constructor(val kills: Long, val deaths: Long)

class SkyBlockProfile internal constructor(
	val profileId: String,
	val cuteName: String,
	val mode: ProfileMode,
	val members: List<String>,
	val skills: Map<Skill, SkillProgress>,
	val slayers: Map<String, SlayerProgress>,
	val dungeons: DungeonSlice?,
	val collections: Map<String, Long>,
	val minions: Map<String, Int>,
	val bestiary: Map<String, BestiaryEntry>,
	val mining: MiningProfile?,
	val farming: FarmingProfile?
)

internal object SkyBlockProfiles {
	fun of(uuid: String, profilesReply: JsonObject): SkyBlockProfile? {
		val profile = ProfileSlices.selectedProfile(profilesReply) ?: return null
		val member = ProfileSlices.member(profile, uuid) ?: return null
		val members = profile.obj("members") ?: JsonObject()
		val everyMember = members.keySet().mapNotNull(members::obj)
		val farming = FarmingProfiles.of(member)
		return SkyBlockProfile(
			profileId = profile.text("profile_id") ?: "",
			cuteName = profile.text("cute_name") ?: "",
			mode = mode(profile.text("game_mode")),
			members = stillOnProfile(members),
			skills = skills(member, everyMember, farming?.farmingLevelCap ?: 0),
			slayers = slayers(member.obj("slayer")?.obj("slayer_bosses")),
			dungeons = ProfileSlices.dungeons(member),
			collections = collections(everyMember),
			minions = minions(everyMember),
			bestiary = bestiary(member.obj("bestiary")),
			mining = MiningProfiles.of(member),
			farming = farming
		)
	}

	private fun mode(gameMode: String?): ProfileMode = when (gameMode) {
		"ironman" -> ProfileMode.IRONMAN
		"island" -> ProfileMode.STRANDED
		"bingo" -> ProfileMode.BINGO
		else -> ProfileMode.NORMAL
	}

	private fun stillOnProfile(members: JsonObject): List<String> =
		members.keySet().filter { members.obj(it)?.obj("profile")?.has("deletion_notice") != true }

	private fun skills(member: JsonObject, everyMember: List<JsonObject>, farmingBonus: Int): Map<Skill, SkillProgress> {
		val experience = member.obj("player_data")?.obj("experience")
		val tamingBonus = member.obj("pets_data")?.obj("pet_care")?.array("pet_types_sacrificed")?.size() ?: 0
		val constants = ItemRepo.constants
		val skills = LinkedHashMap<Skill, SkillProgress>(Skill.entries.size)
		for (skill in Skill.entries) {
			val earned = if (skill == Skill.SOCIAL) social(everyMember) else experience?.number(skill.apiKey) ?: 0.0
			val cap = constants.skillCap(skill.repoKey) + when (skill) {
				Skill.FARMING -> farmingBonus
				Skill.TAMING -> tamingBonus
				else -> 0
			}
			skills[skill] = SkillProgress(earned, constants.skillLevel(skill.repoKey, earned, cap), cap)
		}
		return skills
	}

	private fun social(everyMember: List<JsonObject>): Double =
		everyMember.sumOf { it.obj("player_data")?.obj("experience")?.number(Skill.SOCIAL.apiKey) ?: 0.0 }

	private fun slayers(bosses: JsonObject?): Map<String, SlayerProgress> {
		if (bosses == null) return emptyMap()
		val constants = ItemRepo.constants
		val slayers = LinkedHashMap<String, SlayerProgress>(bosses.size())
		for (name in bosses.keySet()) {
			val boss = bosses.obj(name) ?: continue
			val experience = boss.number("xp") ?: 0.0
			slayers[name] = SlayerProgress(
				experience = experience,
				level = constants.slayerLevel(name, experience),
				maxLevel = constants.slayerMaxLevel(name),
				tierKills = tierKills(boss)
			)
		}
		return slayers
	}

	private fun tierKills(boss: JsonObject): Map<Int, Int> {
		val kills = LinkedHashMap<Int, Int>()
		for (key in boss.keySet()) {
			if (!key.startsWith(BOSS_KILLS)) continue
			val tier = key.removePrefix(BOSS_KILLS).toIntOrNull() ?: continue
			kills[tier] = boss.number(key)?.toInt() ?: continue
		}
		return kills
	}

	private fun collections(everyMember: List<JsonObject>): Map<String, Long> {
		val collected = LinkedHashMap<String, Long>()
		for (member in everyMember) {
			val collection = member.obj("collection") ?: continue
			for (id in collection.keySet()) {
				val amount = collection.number(id)?.toLong() ?: continue
				collected.merge(id, amount, Long::plus)
			}
		}
		return collected
	}

	private fun minions(everyMember: List<JsonObject>): Map<String, Int> {
		val crafted = LinkedHashMap<String, Int>()
		for (member in everyMember) {
			val generators = member.obj("player_data")?.array("crafted_generators") ?: continue
			for (entry in generators) {
				val id = entry.textOrNull() ?: continue
				val tier = id.substringAfterLast('_').toIntOrNull() ?: continue
				val type = id.substringBeforeLast('_')
				crafted[type] = maxOf(crafted[type] ?: 0, tier)
			}
		}
		return crafted
	}

	private fun bestiary(bestiary: JsonObject?): Map<String, BestiaryEntry> {
		val kills = bestiary?.obj("kills")
		val deaths = bestiary?.obj("deaths")
		val mobs = LinkedHashMap<String, BestiaryEntry>()
		for (mob in kills.keys() + deaths.keys()) {
			mobs[mob] = BestiaryEntry(kills?.number(mob)?.toLong() ?: 0L, deaths?.number(mob)?.toLong() ?: 0L)
		}
		return mobs
	}
}
