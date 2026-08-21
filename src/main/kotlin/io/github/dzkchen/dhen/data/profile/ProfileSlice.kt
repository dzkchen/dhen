package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

private val COMPLETED_FLOORS = 1..7
private val DUNGEON_CLASSES = listOf("healer", "mage", "berserk", "archer", "tank")

enum class OnlineReading { ONLINE, OFFLINE, UNKNOWN }

class ProfileStatus internal constructor(
	val reading: OnlineReading,
	val gameType: String?,
	val mode: String?,
	val map: String?
)

class DungeonFloors internal constructor(
	val completions: Map<Int, Int>,
	val fastestSMillis: Map<Int, Long>,
	val fastestSPlusMillis: Map<Int, Long>
) {
	val runs: Int = COMPLETED_FLOORS.sumOf { completions[it] ?: 0 }
}

class DungeonSlice internal constructor(
	val catacombsExperience: Double,
	val catacombsLevel: Int,
	val classLevels: Map<String, Int>,
	val classAverage: Double,
	val selectedClass: String?,
	val secrets: Long,
	val bloodMobKills: Int,
	val catacombs: DungeonFloors,
	val masterCatacombs: DungeonFloors
) {
	val runs: Int = catacombs.runs + masterCatacombs.runs

	val secretsPerRun: Double = if (runs == 0) 0.0 else secrets.toDouble() / runs
}

class ProfileSlice internal constructor(
	val profileId: String,
	val cuteName: String,
	val dungeons: DungeonSlice?,
	val magicalPower: Int?,
	val assumedMagicalPower: Int,
	val inventoryApi: Boolean
)

internal object CatacombsLevels {
	private const val EXPERIENCE_PER_LEVEL_BEYOND_TABLE = 200_000_000.0

	private val requirements = longArrayOf(
		50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040,
		97640, 135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1683640, 2284640, 3084640, 4149640,
		5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640, 51559640, 66559640, 85559640,
		109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640, 569809640
	)

	fun of(experience: Double): Int {
		for (level in requirements.indices) if (experience < requirements[level]) return level
		return requirements.size + ((experience - requirements.last()) / EXPERIENCE_PER_LEVEL_BEYOND_TABLE).toInt()
	}
}

internal object ProfileSlices {
	private val unreadableStatus = ProfileStatus(OnlineReading.UNKNOWN, null, null, null)

	fun of(uuid: String, profilesReply: JsonObject): ProfileSlice? {
		val profile = selectedProfile(profilesReply) ?: return null
		val member = member(profile, uuid) ?: return null
		val magicalPower = MagicalPower.of(member)
		return ProfileSlice(
			profileId = profile.text("profile_id") ?: "",
			cuteName = profile.text("cute_name") ?: "",
			dungeons = member.obj("dungeons")?.let { dungeonSlice(it, member) },
			magicalPower = magicalPower,
			assumedMagicalPower = MagicalPower.assumed(member, magicalPower),
			inventoryApi = inventoryApi(member)
		)
	}

	fun status(statusReply: JsonObject?): ProfileStatus {
		val session = statusReply?.obj("session") ?: return unreadableStatus
		return ProfileStatus(
			reading = if (session.flag("online")) OnlineReading.ONLINE else OnlineReading.OFFLINE,
			gameType = session.text("gameType"),
			mode = session.text("mode"),
			map = session.text("map")
		)
	}

	fun secrets(playerReply: JsonObject): Long? =
		playerReply.obj("player")?.obj("achievements")?.number("skyblock_treasure_hunter")?.toLong()

	fun selectedProfile(profilesReply: JsonObject): JsonObject? = profilesReply.array("profiles")
		?.firstOrNull { it is JsonObject && it.flag("selected") }
		?.asJsonObject

	internal fun member(profile: JsonObject, uuid: String): JsonObject? =
		profile.obj("members")?.obj(uuid.replace("-", ""))

	internal fun inventoryApi(member: JsonObject): Boolean =
		member.obj("inventory")?.obj("ender_chest_contents")?.text("data") != null

	private fun dungeonSlice(dungeons: JsonObject, member: JsonObject): DungeonSlice {
		val types = dungeons.obj("dungeon_types")
		val catacombs = types?.obj("catacombs")
		val experience = catacombs?.number("experience") ?: 0.0
		val classLevels = classLevels(dungeons)
		return DungeonSlice(
			catacombsExperience = experience,
			catacombsLevel = CatacombsLevels.of(experience),
			classLevels = classLevels,
			classAverage = DUNGEON_CLASSES.sumOf { classLevels[it] ?: 0 }.toDouble() / DUNGEON_CLASSES.size,
			selectedClass = dungeons.text("selected_dungeon_class"),
			secrets = dungeons.number("secrets")?.toLong() ?: 0L,
			bloodMobKills = bloodMobKills(member),
			catacombs = floors(catacombs),
			masterCatacombs = floors(types?.obj("master_catacombs"))
		)
	}

	private fun classLevels(dungeons: JsonObject): Map<String, Int> {
		val classes = dungeons.obj("player_classes") ?: return emptyMap()
		val levels = LinkedHashMap<String, Int>(classes.size())
		for (name in classes.keySet()) levels[name] = CatacombsLevels.of(classes.obj(name)?.number("experience") ?: 0.0)
		return levels
	}

	private fun bloodMobKills(member: JsonObject): Int {
		val kills = member.obj("player_stats")?.obj("kills") ?: return 0
		return ((kills.number("watcher_summon_undead") ?: 0.0) + (kills.number("master_watcher_summon_undead") ?: 0.0)).toInt()
	}

	private fun floors(type: JsonObject?): DungeonFloors = DungeonFloors(
		completions = byFloor(type?.obj("tier_completions"), Double::toInt),
		fastestSMillis = byFloor(type?.obj("fastest_time_s"), Double::toLong),
		fastestSPlusMillis = byFloor(type?.obj("fastest_time_s_plus"), Double::toLong)
	)

	private fun <V> byFloor(readings: JsonObject?, read: (Double) -> V): Map<Int, V> {
		if (readings == null) return emptyMap()
		val values = LinkedHashMap<Int, V>(readings.size())
		for (key in readings.keySet()) {
			val floor = key.toIntOrNull() ?: continue
			val reading = readings.number(key) ?: continue
			values[floor] = read(reading)
		}
		return values
	}
}
