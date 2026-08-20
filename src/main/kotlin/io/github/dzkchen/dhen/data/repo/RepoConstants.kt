package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.text
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class ReforgeStone internal constructor(val stone: String, val reforge: String, val costs: Map<String, Long>)

class StarTier internal constructor(val essence: String, val essenceAmount: Int, val materials: Map<String, Int>)

private class PetLeveling(val extraLevels: List<Int>, val maxLevel: Int, val rarityOffsets: Map<String, Int>)

class RepoConstants private constructor(
	private val reforgeStones: Map<String, ReforgeStone>,
	private val stars: Map<String, List<StarTier>>,
	private val gemstoneSlots: Map<String, Map<String, Map<String, Int>>>,
	private val petLevels: List<Int>,
	private val petRarityOffsets: Map<String, Int>,
	private val customPets: Map<String, PetLeveling>
) {
	val reforgeStoneCount: Int get() = reforgeStones.size

	val starredItemCount: Int get() = stars.size

	fun reforgeStone(modifier: String): ReforgeStone? = reforgeStones[modifier]

	fun starTiers(id: String): List<StarTier> = stars[id].orEmpty()

	fun gemstoneSlotCost(id: String, slot: String): Map<String, Int> = gemstoneSlots[id]?.get(slot).orEmpty()

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

	internal companion object {
		private const val DEFAULT_PET_MAX_LEVEL = 100
		private const val JSON = ".json"

		val EMPTY = RepoConstants(emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap())

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
				petRarityOffsets = offsets?.keySet()?.associateWith { offsets.number(it)?.toInt() ?: 0 }.orEmpty(),
				customPets = customPets(pets.getAsJsonObject("custom_pet_leveling"))
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
					rarityOffsets = offsets?.keySet()?.associateWith { offsets.number(it)?.toInt() ?: 0 }.orEmpty()
				)
			}
			return pets
		}

		private fun JsonArray?.ints(): List<Int> {
			if (this == null || isEmpty) return emptyList()
			return mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asInt }
		}
	}
}
