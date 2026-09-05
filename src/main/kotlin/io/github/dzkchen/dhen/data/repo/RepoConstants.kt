package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import io.github.dzkchen.dhen.util.texts
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class Reforge internal constructor(
	val stone: String,
	val reforge: String,
	val costs: Map<String, Long>,
	val itemTypes: List<String>,
	val rarities: List<String>,
	val stats: Map<String, Map<String, Double>>,
	val ability: Map<String, String>
)

class AttributeShard internal constructor(val id: String, val shardId: String, val bazaarName: String)

class StarTier internal constructor(val essence: String, val essenceAmount: Int, val materials: Map<String, Int>)

class PetLevelProgress internal constructor(
	val level: Int,
	val maxLevel: Int,
	val currentLevelXp: Double,
	val nextLevelXp: Double,
	val totalXp: Double
) {
	val percentage: Double
		get() {
			if (level >= maxLevel) return 100.0
			val needed = nextLevelXp - currentLevelXp
			return if (needed <= 0.0) 0.0 else ((totalXp - currentLevelXp) / needed * 100.0).coerceIn(0.0, 100.0)
		}

	val overflowXp: Double
		get() = if (level >= maxLevel) (totalXp - currentLevelXp).coerceAtLeast(0.0) else 0.0
}

private class PetLeveling(val extraLevels: List<Int>, val maxLevel: Int, val rarityOffsets: Map<String, Int>)

enum class LevelLadder { SKILL, SLAYER, SKILL_TREE, GARDEN, CROP_MILESTONE }

private class LevelTable(private val totals: LongArray, val maxLevel: Int = totals.size) {
	fun level(experience: Double, cap: Int): Int {
		var level = 0
		while (level < cap && level < totals.size && totals[level] <= experience) level++
		return level
	}

	fun cappedAt(cap: Int?): LevelTable = if (cap == null || cap == maxLevel) this else LevelTable(totals, cap)
}

private val NO_LEVELS = LevelTable(LongArray(0))

private class Leveling(
	private val skills: Map<String, LevelTable>,
	private val anySkill: LevelTable,
	private val slayers: Map<String, LevelTable>,
	private val trees: Map<String, LevelTable>,
	private val garden: LevelTable,
	private val cropMilestones: Map<String, LevelTable>
) {
	fun table(ladder: LevelLadder, key: String): LevelTable = when (ladder) {
		LevelLadder.SKILL -> skills[key] ?: anySkill
		LevelLadder.SLAYER -> slayers[key] ?: NO_LEVELS
		LevelLadder.SKILL_TREE -> trees[key] ?: NO_LEVELS
		LevelLadder.GARDEN -> garden
		LevelLadder.CROP_MILESTONE -> cropMilestones[key] ?: NO_LEVELS
	}
}

class RepoConstants private constructor(
	private val reforgeStones: Map<String, Reforge>,
	val blacksmithReforges: List<Reforge>,
	val attributeShards: List<AttributeShard>,
	private val stars: Map<String, List<StarTier>>,
	private val gemstoneSlots: Map<String, Map<String, Map<String, Int>>>,
	private val petLevels: List<Int>,
	private val petRarityOffsets: Map<String, Int>,
	private val customPets: Map<String, PetLeveling>,
	private val leveling: Leveling,
	val warps: Set<String>,
	private val sacks: Map<String, List<String>>,
	private val trophyFillets: Map<String, IntArray>
) {
	val sackItemIds: Set<String> = sacks.values.flatMapTo(LinkedHashSet()) { it }

	fun trophyFillet(marketId: String): Int {
		val fillets = trophyFillets[marketId.substringBeforeLast('_')] ?: return 0
		val tier = TROPHY_TIERS.indexOf(marketId.substringAfterLast('_'))
		return if (tier < 0 || tier >= fillets.size) 0 else fillets[tier]
	}

	val reforgeStoneCount: Int get() = reforgeStones.size

	val stoneReforges: Collection<Reforge> get() = reforgeStones.values

	val starredItemCount: Int get() = stars.size

	val starredIds: Set<String> get() = stars.keys

	fun reforgeStone(modifier: String): Reforge? = reforgeStones[modifier]

	fun starTiers(id: String): List<StarTier> = stars[id].orEmpty()

	fun gemstoneSlotCost(id: String, slot: String): Map<String, Int> = gemstoneSlots[id]?.get(slot).orEmpty()

	fun level(ladder: LevelLadder, experience: Double, key: String, cap: Int = maxLevel(ladder, key)): Int =
		leveling.table(ladder, key).level(experience, cap)

	fun maxLevel(ladder: LevelLadder, key: String): Int = leveling.table(ladder, key).maxLevel

	fun petLevel(type: String, tier: String, exp: Double, curveTier: String = tier): Int {
		return petProgress(type, tier, exp, curveTier).level
	}

	fun petProgress(type: String, tier: String, exp: Double, curveTier: String = tier): PetLevelProgress {
		val custom = customPets[type]
		val offset = custom?.rarityOffsets?.let { it[curveTier] ?: it[tier] }
			?: petRarityOffsets[curveTier] ?: petRarityOffsets[tier]
			?: return PetLevelProgress(1, custom?.maxLevel ?: DEFAULT_PET_MAX_LEVEL, 0.0, 0.0, exp)
		val maxLevel = custom?.maxLevel ?: DEFAULT_PET_MAX_LEVEL
		var threshold = 0.0
		var level = 1
		var index = offset
		while (level < maxLevel) {
			val cost = petCost(index, custom) ?: break
			if (exp < threshold + cost) break
			threshold += cost
			level++
			index++
		}
		val next = if (level >= maxLevel) threshold else petCost(index, custom)?.let { threshold + it } ?: threshold
		return PetLevelProgress(level, maxLevel, threshold, next, exp)
	}

	private fun petCost(index: Int, custom: PetLeveling?): Int? {
		if (index < petLevels.size) return petLevels[index]
		val extra = index - petLevels.size
		return custom?.extraLevels?.getOrNull(extra)
	}

	internal companion object {
		private const val DEFAULT_PET_MAX_LEVEL = 100
		private const val DEFAULT_SKILL_CAP = 50
		private const val JSON = ".json"
		private const val ANY_RARITY = "ANY"

		private val SKILLS_WITH_THEIR_OWN_LADDER = mapOf("runecrafting" to "runecrafting_xp", "social" to "social")
		private val SKILL_TREES = listOf("HOTM", "HOTF")

		private val NO_LEVELLING = Leveling(
			skills = emptyMap(),
			anySkill = NO_LEVELS.cappedAt(DEFAULT_SKILL_CAP),
			slayers = emptyMap(),
			trees = emptyMap(),
			garden = NO_LEVELS,
			cropMilestones = emptyMap()
		)

		val EMPTY = RepoConstants(
			emptyMap(), emptyList(), emptyList(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(),
			NO_LEVELLING, emptySet(), emptyMap(), emptyMap()
		)

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		private val UNSPEAKABLE = Regex("[^a-z0-9\\s_-]")
		private val SEPARATOR = Regex("[\\s-]")

		private val TROPHY_TIERS = listOf("BRONZE", "SILVER", "GOLD", "DIAMOND")

		fun read(constants: Path): RepoConstants {
			if (!Files.isDirectory(constants)) return EMPTY
			val pets = read(constants, "pets")
			val offsets = pets.obj("pet_rarity_offset")
			return RepoConstants(
				reforgeStones = reforgeStones(read(constants, "reforgestones")),
				blacksmithReforges = blacksmithReforges(read(constants, "reforges")),
				attributeShards = attributeShards(read(constants, "attribute_shards")),
				stars = stars(read(constants, "essencecosts")),
				gemstoneSlots = gemstoneSlots(read(constants, "gemstonecosts")),
				petLevels = pets.array("pet_levels").ints(),
				petRarityOffsets = offsets.ints(0),
				customPets = customPets(pets.obj("custom_pet_leveling")),
				leveling = leveling(read(constants, "leveling"), read(constants, "garden")),
				warps = warps(read(constants, "islands")),
				sacks = sacks(read(constants, "sacks")),
				trophyFillets = trophyFillets(read(constants, "trophyfish"))
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

		private fun warps(json: JsonObject): Set<String> {
			val array = json.array("island_warps") ?: return emptySet()
			val warps = LinkedHashSet<String>(array.size())
			for (element in array) {
				val entry = element as? JsonObject ?: continue
				entry.text("warp")?.let { warps += it.lowercase(Locale.ROOT) }
				entry.array("aliases")?.forEach { alias ->
					alias.textOrNull()?.let { warps += it.lowercase(Locale.ROOT) }
				}
			}
			return warps
		}

		private fun sacks(json: JsonObject): Map<String, List<String>> {
			val sacks = json.obj("sacks") ?: return emptyMap()
			val grouped = LinkedHashMap<String, List<String>>(sacks.size())
			for ((name, element) in sacks.entrySet()) {
				val sack = element as? JsonObject ?: continue
				val contents = ArrayList<String>()
				sack.array("contents")?.forEach { id ->
					id.textOrNull()?.let { contents += it.uppercase(Locale.ROOT) }
				}
				if (contents.isNotEmpty()) grouped[name] = contents
			}
			return grouped
		}

		private fun trophyFillets(json: JsonObject): Map<String, IntArray> {
			val fillets = HashMap<String, IntArray>(json.size())
			for ((id, element) in json.entrySet()) {
				val tiers = (element as? JsonArray)?.ints() ?: continue
				if (tiers.isNotEmpty()) fillets[id.uppercase(Locale.ROOT)] = tiers.toIntArray()
			}
			return fillets
		}

		private fun reforgeStones(json: JsonObject): Map<String, Reforge> {
			val stones = HashMap<String, Reforge>(json.size())
			for ((_, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val stone = entry.text("internalName") ?: continue
				val reforge = entry.text("reforgeName") ?: continue
				stones[entry.text("nbtModifier") ?: nbtModifier(reforge)] =
					parsed(entry, stone.uppercase(Locale.ROOT), reforge)
			}
			return stones
		}

		private fun blacksmithReforges(json: JsonObject): List<Reforge> {
			val reforges = ArrayList<Reforge>(json.size())
			for ((key, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				reforges += parsed(entry, "", entry.text("reforgeName") ?: key)
			}
			return reforges
		}

		private fun parsed(entry: JsonObject, stone: String, name: String): Reforge {
			val costs = entry.obj("reforgeCosts")
			return Reforge(
				stone = stone,
				reforge = name,
				costs = costs?.keySet()?.associate { it.uppercase(Locale.ROOT) to costs.long(it) }.orEmpty(),
				itemTypes = itemTypes(entry.get("itemTypes")),
				rarities = entry.array("requiredRarities").texts().map { it.uppercase(Locale.ROOT) },
				stats = stats(entry.obj("reforgeStats")),
				ability = ability(entry.get("reforgeAbility"))
			)
		}

		private fun itemTypes(element: JsonElement?): List<String> {
			val listed = when (element) {
				is JsonObject -> element.keys().flatMap { element.array(it).texts() }
				else -> element.textOrNull()?.split(',', '/').orEmpty()
			}
			return listed.map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() }
		}

		private fun stats(json: JsonObject?): Map<String, Map<String, Double>> {
			if (json == null) return emptyMap()
			return json.keySet().associate { rarity ->
				val entry = json.obj(rarity)
				rarity.uppercase(Locale.ROOT) to entry.keys().mapNotNull { stat ->
					entry?.number(stat)?.let { stat to it }
				}.toMap()
			}
		}

		private fun ability(element: JsonElement?): Map<String, String> = when (element) {
			is JsonObject -> element.keySet().mapNotNull { rarity ->
				element.text(rarity)?.let { rarity.uppercase(Locale.ROOT) to it }
			}.toMap()
			else -> element.textOrNull()?.let { mapOf(ANY_RARITY to it) }.orEmpty()
		}

		private fun attributeShards(json: JsonObject): List<AttributeShard> {
			val listed = json.array("attributes") ?: return emptyList()
			return listed.mapNotNull { element ->
				val entry = element as? JsonObject ?: return@mapNotNull null
				val id = entry.text("internalName") ?: return@mapNotNull null
				AttributeShard(
					id.uppercase(Locale.ROOT),
					entry.text("shardId").orEmpty(),
					entry.text("bazaarName").orEmpty()
				)
			}
		}

		private fun nbtModifier(reforge: String): String =
			reforge.lowercase(Locale.ROOT).replace(UNSPEAKABLE, "").replace(SEPARATOR, "_")

		private fun stars(json: JsonObject): Map<String, List<StarTier>> {
			val stars = HashMap<String, List<StarTier>>(json.size())
			for ((id, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val essence = entry.text("type")?.uppercase(Locale.ROOT) ?: continue
				val extras = entry.obj("items")
				val tiers = ArrayList<StarTier>()
				while (true) {
					val tier = (tiers.size + 1).toString()
					val amount = entry.number(tier)?.toInt() ?: break
					tiers += StarTier(essence, amount, ingredients(extras?.array(tier)))
				}
				if (tiers.isNotEmpty()) stars[id.uppercase(Locale.ROOT)] = tiers
			}
			return stars
		}

		private fun gemstoneSlots(json: JsonObject): Map<String, Map<String, Map<String, Int>>> {
			val items = HashMap<String, Map<String, Map<String, Int>>>(json.size())
			for ((id, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val slots = entry.keySet().associateWith { ingredients(entry.array(it)) }
				if (slots.isNotEmpty()) items[id.uppercase(Locale.ROOT)] = slots
			}
			return items
		}

		private fun customPets(json: JsonObject?): Map<String, PetLeveling> {
			if (json == null) return emptyMap()
			val pets = HashMap<String, PetLeveling>(json.size())
			for ((type, element) in json.entrySet()) {
				val entry = element as? JsonObject ?: continue
				val offsets = entry.obj("rarity_offset")
				pets[type.uppercase(Locale.ROOT)] = PetLeveling(
					extraLevels = entry.array("pet_levels").ints(),
					maxLevel = entry.int("max_level", DEFAULT_PET_MAX_LEVEL),
					rarityOffsets = offsets.ints(0)
				)
			}
			return pets
		}

		private fun leveling(json: JsonObject, gardenJson: JsonObject): Leveling {
			val caps = json.obj("leveling_caps").ints(DEFAULT_SKILL_CAP)
			val anySkill = incrementTable(json.array("leveling_xp"))
			val ownLadders = SKILLS_WITH_THEIR_OWN_LADDER
				.mapNotNull { (skill, table) -> json.array(table)?.let { steps -> skill to incrementTable(steps) } }
				.toMap()
			val slayers = json.obj("slayer_xp") ?: JsonObject()
			val trees = SKILL_TREES.associateWith { incrementTable(json.array(it)).cappedAt(caps[it]) }
			val milestones = gardenJson.obj("crop_milestones") ?: JsonObject()
			val gardenLevels = incrementTable(gardenJson.array("garden_exp"))
			if (anySkill.maxLevel == 0 || slayers.size() == 0) {
				log.warn("Dhen read no skill or slayer levelling table from the repo (leveling.json absent or empty), so those levels will read zero")
			}
			if (gardenLevels.maxLevel == 0 || milestones.size() == 0) {
				log.warn("Dhen read no garden level or crop milestone table from the repo (garden.json absent or empty), so those will read zero")
			}
			return Leveling(
				skills = ((caps.keys - trees.keys) + ownLadders.keys)
					.associateWith { (ownLadders[it] ?: anySkill).cappedAt(caps[it] ?: DEFAULT_SKILL_CAP) },
				anySkill = anySkill.cappedAt(DEFAULT_SKILL_CAP),
				slayers = slayers.keySet().associateWith { totalTable(slayers.array(it)) },
				trees = trees,
				garden = gardenLevels,
				cropMilestones = milestones.keys().associateWith { incrementTable(milestones.array(it)) }
			)
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
