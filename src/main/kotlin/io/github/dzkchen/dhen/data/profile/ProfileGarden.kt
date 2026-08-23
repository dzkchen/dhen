package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.LevelLadder
import io.github.dzkchen.dhen.data.repo.RepoConstants
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.texts

enum class ComposterUpgrade {
	SPEED, MULTI_DROP, FUEL_CAP, ORGANIC_MATTER_CAP, COST_REDUCTION;

	internal val apiKey: String = lowercaseApiKey()
}

enum class GreenhouseUpgrade { GROWTH_SPEED, YIELD, PLOT_LIMIT }

enum class ContestMedal {
	BRONZE, SILVER, GOLD, PLATINUM, DIAMOND;

	internal val apiKey: String = lowercaseApiKey()

	internal companion object {
		private val byApiKey = entries.associateBy(ContestMedal::apiKey)

		fun of(medal: String?): ContestMedal? = byApiKey[medal]
	}
}

class Composter internal constructor(
	val organicMatter: Double,
	val fuel: Double,
	val compostUnits: Double,
	val compostItems: Int,
	val upgrades: Map<ComposterUpgrade, Int>
)

class CropProgress internal constructor(val collected: Long, val milestone: Int, val upgrade: Int)

class GreenhouseSlot internal constructor(val x: Int, val z: Int)

class Greenhouse internal constructor(
	val slots: List<GreenhouseSlot>,
	val upgrades: Map<GreenhouseUpgrade, Int>
)

class VisitorLog internal constructor(val offered: Int, val accepted: Int)

class GardenVisitors internal constructor(
	val totalCompleted: Int,
	val uniqueServed: Int,
	val perVisitor: Map<String, VisitorLog>
)

class GardenProfile internal constructor(
	val experience: Double,
	val level: Int,
	val maxLevel: Int,
	val crops: Map<String, CropProgress>,
	val unlockedPlots: List<String>,
	val composter: Composter,
	val visitors: GardenVisitors,
	val greenhouse: Greenhouse,
	val selectedBarnSkin: String?,
	val unlockedBarnSkins: List<String>
)

class FarmingContest internal constructor(
	val contestId: String,
	val collected: Int,
	val position: Int?,
	val participants: Int?,
	val medal: ContestMedal?
) {
	fun isCrop(crop: String): Boolean = contestId.endsWith(crop)
}

class FarmingProfile internal constructor(
	val medals: Map<ContestMedal, Int>,
	val uniqueBrackets: Map<ContestMedal, Set<String>>,
	val personalBests: Map<String, Int>,
	val contests: List<FarmingContest>,
	val farmingLevelCap: Int,
	val doubleDrops: Int,
	val chips: Map<String, Int>
)

internal object GardenProfiles {
	private const val WHOLE_GARDEN = ""

	fun of(gardenReply: JsonObject): GardenProfile? {
		val garden = gardenReply.obj("garden") ?: return null
		val experience = garden.number("garden_experience") ?: 0.0
		val constants = ItemRepo.constants
		return GardenProfile(
			experience = experience,
			level = constants.level(LevelLadder.GARDEN, experience, WHOLE_GARDEN),
			maxLevel = constants.maxLevel(LevelLadder.GARDEN, WHOLE_GARDEN),
			crops = crops(constants, garden.obj("resources_collected"), garden.obj("crop_upgrade_levels")),
			unlockedPlots = garden.array("unlocked_plots_ids").texts(),
			composter = composter(garden.obj("composter_data")),
			visitors = visitors(garden.obj("commission_data")),
			greenhouse = greenhouse(garden.array("greenhouse_slots"), garden.obj("garden_upgrades")),
			selectedBarnSkin = garden.text("selected_barn_skin"),
			unlockedBarnSkins = garden.array("unlocked_barn_skins").texts()
		)
	}

	private fun visitors(data: JsonObject?): GardenVisitors {
		val offered = data?.obj("visits")
		val accepted = data?.obj("completed")
		return GardenVisitors(
			totalCompleted = data.int("total_completed"),
			uniqueServed = data.int("unique_npcs_served"),
			perVisitor = (offered.keys() + accepted.keys()).associateWith { visitor ->
				VisitorLog(offered.int(visitor), accepted.int(visitor))
			}
		)
	}

	private fun greenhouse(slots: JsonArray?, upgrades: JsonObject?): Greenhouse = Greenhouse(
		slots = slots?.mapNotNull { plot ->
			(plot as? JsonObject)?.let { GreenhouseSlot(it.int("x"), it.int("z")) }
		} ?: emptyList(),
		upgrades = GreenhouseUpgrade.entries.associateWith { upgrades.int(it.name) }
	)

	private fun crops(constants: RepoConstants, collected: JsonObject?, upgrades: JsonObject?): Map<String, CropProgress> {
		val grown = collected.keys() + upgrades.keys()
		val crops = LinkedHashMap<String, CropProgress>(grown.size)
		for (crop in grown) {
			val amount = collected?.number(crop) ?: 0.0
			crops[crop] = CropProgress(
				collected = amount.toLong(),
				milestone = constants.level(LevelLadder.CROP_MILESTONE, amount, crop),
				upgrade = upgrades.int(crop)
			)
		}
		return crops
	}

	private fun composter(data: JsonObject?): Composter {
		val upgrades = data?.obj("upgrades").ints()
		return Composter(
			organicMatter = data?.number("organic_matter") ?: 0.0,
			fuel = data?.number("fuel_units") ?: 0.0,
			compostUnits = data?.number("compost_units") ?: 0.0,
			compostItems = data.int("compost_items"),
			upgrades = ComposterUpgrade.entries.associateWith { upgrades[it.apiKey] ?: 0 }
		)
	}
}

internal object FarmingProfiles {
	fun of(member: JsonObject): FarmingProfile? {
		val jacob = member.obj("jacobs_contest")
		val chips = member.obj("player_data")?.obj("garden_chips")
		if (jacob == null && chips == null) return null
		val perks = jacob?.obj("perks")
		return FarmingProfile(
			medals = counted(jacob?.obj("medals_inv")),
			uniqueBrackets = brackets(jacob?.obj("unique_brackets")),
			personalBests = jacob?.obj("personal_bests").ints(),
			contests = contests(jacob?.obj("contests")),
			farmingLevelCap = perks.int("farming_level_cap"),
			doubleDrops = perks.int("double_drops"),
			chips = chips.ints()
		)
	}

	private fun counted(medals: JsonObject?): Map<ContestMedal, Int> =
		medals.ints().let { won -> ContestMedal.entries.associateWith { won[it.apiKey] ?: 0 } }

	private fun brackets(brackets: JsonObject?): Map<ContestMedal, Set<String>> =
		ContestMedal.entries.associateWith { medal ->
			brackets?.array(medal.apiKey).texts().toSet()
		}

	private fun contests(contests: JsonObject?): List<FarmingContest> {
		if (contests == null) return emptyList()
		val entered = ArrayList<FarmingContest>(contests.size())
		for (id in contests.keySet()) {
			val contest = contests.obj(id) ?: continue
			entered += FarmingContest(
				contestId = id,
				collected = contest.int("collected"),
				position = contest.number("claimed_position")?.toInt(),
				participants = contest.number("claimed_participants")?.toInt(),
				medal = ContestMedal.of(contest.text("claimed_medal"))
			)
		}
		return entered
	}
}
