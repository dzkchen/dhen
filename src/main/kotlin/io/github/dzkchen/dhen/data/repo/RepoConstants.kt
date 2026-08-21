package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class ReforgeStone internal constructor(val stone: String, val reforge: String, val costs: Map<String, Long>)

class StarTier internal constructor(val essence: String, val essenceAmount: Int, val materials: Map<String, Int>)

private class PetLeveling(val extraLevels: List<Int>, val maxLevel: Int, val rarityOffsets: Map<String, Int>)

private class LevelTable(private val totals: LongArray) {
	val maxLevel: Int get() = totals.size

	fun level(experience: Double, cap: Int = maxLevel): Int {
		var level = 0
		while (level < cap && level < totals.size && totals[level] <= experience) level++
		return level
	}
}

private class Leveling(
	val skills: LevelTable,
	val perSkill: Map<String, LevelTable>,
	val slayers: Map<String, LevelTable>,
	val trees: Map<String, LevelTable>,
	val caps: Map<String, Int>
)

private class Garden(val levels: LevelTable, val cropMilestones: Map<String, LevelTable>)

class RepoConstants private constructor(
	private val reforgeStones: Map<String, ReforgeStone>,
	private val stars: Map<String, List<StarTier>>,
	private val gemstoneSlots: Map<String, Map<String, Map<String, Int>>>,
	private val petLevels: List<Int>,
	private val petRarityOffsets: Map<String, Int>,
	private val customPets: Map<String, PetLeveling>,
	private val leveling: Leveling,
	private val garden: Garden
) {
	val reforgeStoneCount: Int get() = reforgeStones.size

	val starredItemCount: Int get() = stars.size

	fun reforgeStone(modifier: String): ReforgeStone? = reforgeStones[modifier]

	fun starTiers(id: String): List<StarTier> = stars[id].orEmpty()

	fun gemstoneSlotCost(id: String, slot: String): Map<String, Int> = gemstoneSlots[id]?.get(slot).orEmpty()

	fun skillCap(skill: String): Int = leveling.caps[skill] ?: DEFAULT_SKILL_CAP

	fun skillLevel(skill: String, experience: Double, cap: Int): Int = skillTable(skill).level(experience, cap)

	fun slayerLevel(slayer: String, experience: Double): Int = slayerTable(slayer).level(experience)

	fun slayerMaxLevel(slayer: String): Int = slayerTable(slayer).maxLevel

	fun treeLevel(tree: String, experience: Double): Int = treeTable(tree).level(experience, treeMaxLevel(tree))

	fun treeMaxLevel(tree: String): Int = leveling.caps[tree] ?: treeTable(tree).maxLevel

	val gardenMaxLevel: Int get() = garden.levels.maxLevel

	fun gardenLevel(experience: Double): Int = garden.levels.level(experience)

	fun cropMilestone(crop: String, collected: Double): Int = garden.cropMilestones[crop]?.level(collected) ?: 0

	fun petLevel(type: String, tier: String, exp: Double): Int {
		val custom = customPets[type]
		val offset = custom?.rarityOffsets?.get(tier) ?: petRarityOffsets[tier] ?: return 1
		val tree = if (custom == null || custom.extraLevels.isEmpty()) petLevels else petLevels + custom.extraLevels
		val maxLevel = custom?.maxLevel ?: DEFAULT_PET_MAX_LEVEL
		var remaining = exp
		var level = 1
		for (index in offset until tree.size) {
			if (level >= maxLevel || remaining < tree[index]) break
			remaining -= tree[index]
			level++
		}
		return level
	}

	private fun skillTable(skill: String): LevelTable = leveling.perSkill[skill] ?: leveling.skills

	private fun slayerTable(slayer: String): LevelTable = leveling.slayers[slayer] ?: NO_LEVELS

	private fun treeTable(tree: String): LevelTable = leveling.trees[tree] ?: NO_LEVELS

	internal companion object {
		private const val DEFAULT_PET_MAX_LEVEL = 100
		private const val DEFAULT_SKILL_CAP = 50
		private const val JSON = ".json"

		private val SKILLS_WITH_THEIR_OWN_LADDER = mapOf("runecrafting" to "runecrafting_xp", "social" to "social")
		private val SKILL_TREES = listOf("HOTM", "HOTF")

		private val NO_LEVELS = LevelTable(LongArray(0))
		private val NO_LEVELLING = Leveling(NO_LEVELS, emptyMap(), emptyMap(), emptyMap(), emptyMap())
		private val NO_GARDEN = Garden(NO_LEVELS, emptyMap())

		val EMPTY = RepoConstants(emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(), NO_LEVELLING, NO_GARDEN)

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		private val UNSPEAKABLE = Regex("[^a-z0-9\\s_-]")
		private val SEPARATOR = Regex("[\\s-]")

		fun read(constants: Path): RepoConstants {
			if (!Files.isDirectory(constants)) return EMPTY
			val pets = read(constants, "pets")
			val offsets = pets.getAsJsonObject("pet_rarity_offset")
			return RepoConstants(
				reforgeStones = reforgeStones(read(constants, "reforgestones")),
				stars = stars(read(constants, "essencecosts")),
				gemstoneSlots = gemstoneSlots(read(constants, "gemstonecosts")),
				petLevels = pets.getAsJsonArray("pet_levels").ints(),
				petRarityOffsets = offsets.ints(0),
				customPets = customPets(pets.getAsJsonObject("custom_pet_leveling")),
				leveling = leveling(read(constants, "leveling")),
				garden = garden(read(constants, "garden"))
			)
		}

		private fun read(constants: Path, name: String): JsonObject {
			val file = constants.resolve(name + JSON)
			if (!Files.isRegularFile(file)) return JsonObject()
			return try {
				Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
			} catch (throwable: Throwable) {
				log.warn("Dhen could not read the repo constant {}", name, throwable)
				JsonObject()
			}
		}

		private fun reforgeStones(json: JsonObject): Map<String, ReforgeStone> {
			val stones = HashMap<String, ReforgeStone>(json.size())
			for ((_, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val stone = entry.text("internalName") ?: continue
				val reforge = entry.text("reforgeName") ?: continue
				val costs = entry.getAsJsonObject("reforgeCosts")
				stones[entry.text("nbtModifier") ?: nbtModifier(reforge)] = ReforgeStone(
					stone = stone.uppercase(Locale.ROOT),
					reforge = reforge,
					costs = costs?.keySet()?.associate { it.uppercase(Locale.ROOT) to (costs.number(it)?.toLong() ?: 0L) }.orEmpty()
				)
			}
			return stones
		}

		private fun nbtModifier(reforge: String): String =
			reforge.lowercase(Locale.ROOT).replace(UNSPEAKABLE, "").replace(SEPARATOR, "_")

		private fun stars(json: JsonObject): Map<String, List<StarTier>> {
			val stars = HashMap<String, List<StarTier>>(json.size())
			for ((id, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val essence = entry.text("type")?.uppercase(Locale.ROOT) ?: continue
				val extras = entry.getAsJsonObject("items")
				val tiers = ArrayList<StarTier>()
				while (true) {
					val tier = (tiers.size + 1).toString()
					val amount = entry.number(tier)?.toInt() ?: break
					tiers += StarTier(essence, amount, ingredients(extras?.getAsJsonArray(tier)))
				}
				if (tiers.isNotEmpty()) stars[id.uppercase(Locale.ROOT)] = tiers
			}
			return stars
		}

		private fun gemstoneSlots(json: JsonObject): Map<String, Map<String, Map<String, Int>>> {
			val items = HashMap<String, Map<String, Map<String, Int>>>(json.size())
			for ((id, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val slots = entry.keySet().associateWith { ingredients(entry.getAsJsonArray(it)) }
				if (slots.isNotEmpty()) items[id.uppercase(Locale.ROOT)] = slots
			}
			return items
		}

		private fun customPets(json: JsonObject?): Map<String, PetLeveling> {
			if (json == null) return emptyMap()
			val pets = HashMap<String, PetLeveling>(json.size())
			for ((type, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val offsets = entry.getAsJsonObject("rarity_offset")
				pets[type.uppercase(Locale.ROOT)] = PetLeveling(
					extraLevels = entry.getAsJsonArray("pet_levels").ints(),
					maxLevel = entry.number("max_level")?.toInt() ?: DEFAULT_PET_MAX_LEVEL,
					rarityOffsets = offsets.ints(0)
				)
			}
			return pets
		}

		private fun leveling(json: JsonObject): Leveling {
			val slayers = json.obj("slayer_xp") ?: JsonObject()
			val leveling = Leveling(
				skills = incrementTable(json.array("leveling_xp")),
				perSkill = SKILLS_WITH_THEIR_OWN_LADDER
					.mapNotNull { (skill, table) -> json.array(table)?.let { steps -> skill to incrementTable(steps) } }
					.toMap(),
				slayers = slayers.keySet().associateWith { totalTable(slayers.array(it)) },
				trees = SKILL_TREES.associateWith { incrementTable(json.array(it)) },
				caps = json.obj("leveling_caps").ints(DEFAULT_SKILL_CAP)
			)
			if (json.size() > 0 && (leveling.skills.maxLevel == 0 || leveling.slayers.isEmpty())) {
				log.warn("Dhen found no skill or slayer levelling table in the repo, so those levels will read zero")
			}
			return leveling
		}

		private fun garden(json: JsonObject): Garden {
			val milestones = json.obj("crop_milestones") ?: JsonObject()
			val garden = Garden(
				levels = incrementTable(json.array("garden_exp")),
				cropMilestones = milestones.keys().associateWith { incrementTable(milestones.array(it)) }
			)
			if (json.size() > 0 && (garden.levels.maxLevel == 0 || garden.cropMilestones.isEmpty())) {
				log.warn("Dhen found no garden level or crop milestone table in the repo, so those will read zero")
			}
			return garden
		}

		private fun incrementTable(steps: JsonArray?): LevelTable {
			val costs = steps.longs()
			var total = 0L
			for (level in costs.indices) {
				total += costs[level]
				costs[level] = total
			}
			return LevelTable(costs)
		}

		private fun totalTable(steps: JsonArray?): LevelTable = LevelTable(steps.longs())

		private fun JsonArray?.ints(): List<Int> {
			if (this == null || isEmpty) return emptyList()
			return map { it.numberOrNull()?.toInt() ?: 0 }
		}

		private fun JsonArray?.longs(): LongArray {
			if (this == null || isEmpty) return LongArray(0)
			return LongArray(size()) { get(it).numberOrNull()?.toLong() ?: 0L }
		}
	}
}
