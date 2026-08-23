package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val COMPLETED_FLOORS = 1..7
private val DUNGEON_CLASSES = listOf("healer", "mage", "berserk", "archer", "tank")

enum class OnlineReading { ONLINE, OFFLINE, UNKNOWN }

class ProfileStatus internal constructor(
	val reading: OnlineReading,
	val gameType: String?,
	val mode: String?,
	val map: String?
)

class DungeonFloor internal constructor(
	val completions: Int,
	val bestS: Duration?,
	val bestSPlus: Duration?
)

class DungeonFloors internal constructor(floors: Map<Int, DungeonFloor>) : Map<Int, DungeonFloor> by floors {
	val runs: Int = COMPLETED_FLOORS.sumOf { floors[it]?.completions ?: 0 }
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

class ProfileNames internal constructor(val count: Int, val selected: String?)

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
		return member(profile, uuid)?.let { of(profile, it) }
	}

	fun of(profile: JsonObject, member: JsonObject): ProfileSlice {
		val magicalPower =
			MagicalPower.of(member, CrimsonIsleProfiles.abiphoneContacts(member), RiftProfiles.consumedPrism(member))
		return ProfileSlice(
			profileId = profile.text("profile_id") ?: "",
			cuteName = profile.text("cute_name") ?: "",
			dungeons = dungeons(member),
			magicalPower = magicalPower,
			assumedMagicalPower = MagicalPower.assumed(MaxwellProfiles.tunings(member), magicalPower),
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

	fun names(profilesReply: JsonObject): ProfileNames = ProfileNames(
		count = profilesReply.array("profiles")?.size() ?: 0,
		selected = selectedProfile(profilesReply)?.text("cute_name")
	)

	fun selectedProfile(profilesReply: JsonObject): JsonObject? = profilesReply.array("profiles")
		?.firstOrNull { it is JsonObject && it.flag("selected") }
		?.asJsonObject

	internal fun member(profile: JsonObject, uuid: String): JsonObject? =
		profile.obj("members")?.obj(uuid.replace("-", ""))

	private fun dungeons(member: JsonObject): DungeonSlice? =
		member.obj("dungeons")?.let { dungeonSlice(it, member) }

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
			secrets = dungeons.long("secrets"),
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

	private fun floors(type: JsonObject?): DungeonFloors {
		val completions = type?.obj("tier_completions")
		val bestS = type?.obj("fastest_time_s")
		val bestSPlus = type?.obj("fastest_time_s_plus")
		val floors = LinkedHashMap<Int, DungeonFloor>()
		for (key in completions.keys() + bestS.keys() + bestSPlus.keys()) {
			val number = key.toIntOrNull() ?: continue
			floors[number] = DungeonFloor(
				completions = completions.int(key),
				bestS = bestS?.number(key)?.toLong()?.milliseconds,
				bestSPlus = bestSPlus?.number(key)?.toLong()?.milliseconds
			)
		}
		return DungeonFloors(floors)
	}
}
