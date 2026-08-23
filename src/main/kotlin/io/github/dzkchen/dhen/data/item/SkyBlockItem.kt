package io.github.dzkchen.dhen.data.item

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.text
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import org.slf4j.LoggerFactory
import java.util.Locale

class PetInfo internal constructor(
	val type: String,
	val tier: String,
	val exp: Double,
	val heldItem: String?,
	val candyUsed: Int,
	val skin: String?
)

class SkyBlockItem internal constructor(
	val id: String,
	val uuid: String,
	val marketId: String,
	val pet: PetInfo?,
	val upgradeLevel: Int,
	val rarityUpgrades: Int,
	val hotPotatoCount: Int,
	val artOfWar: Int,
	val tunedTransmission: Int,
	val reforge: String,
	val enchantments: Map<String, Int>,
	val runes: Map<String, Int>,
	val attributes: Map<String, Int>,
	val gems: CompoundTag?,
	val ethermerge: Boolean,
	val donatedMuseum: Boolean,
	val timestamp: Long,
	val tag: CompoundTag,
	knownRarity: ItemRarity? = null
) {
	private var resolvedRarity: ItemRarity? = knownRarity

	val isRecombobulated: Boolean get() = rarityUpgrades > 0

	val isStarred: Boolean get() = upgradeLevel > 0

	val artOfPeace: Boolean get() = tag.getBooleanOr("artOfPeaceApplied", false)

	val powerScroll: String get() = tag.getStringOr("power_ability_scroll", "")

	val woodSingularities: Int get() = tag.getIntOr("wood_singularity_count", 0)

	val jalapenoBooks: Int get() = tag.getIntOr("jalapeno_count", 0)

	val hasStatsBook: Boolean get() = tag.contains("stats_book")

	val enrichment: String get() = tag.getStringOr("talisman_enrichment", "")

	val divanPowderCoating: Boolean get() = tag.getBooleanOr("divan_powder_coating", false)

	val mithrilInfusion: Boolean get() = tag.getBooleanOr("mithril_infusion", false)

	val freeWill: Boolean get() = tag.getBooleanOr("free_will", false)

	val wetBooks: Int get() = tag.getIntOr("wet_book_count", 0)

	val farmingForDummies: Int get() = tag.getIntOr("farming_for_dummies_count", 0)

	val overclockers: Int get() = tag.getIntOr("levelable_overclocks", 0)

	val polarvoidBooks: Int get() = tag.getIntOr("polarvoid", 0)

	val bookwormBooks: Int get() = tag.getIntOr("bookworm_books", 0)

	val pocketSacksInASack: Int get() = tag.getIntOr("sack_pss", 0)

	val manaDisintegrators: Int get() = tag.getIntOr("mana_disintegrator_count", 0)

	val helmetSkin: String get() = tag.getStringOr("skin", "")

	val armorDye: String get() = tag.getStringOr("dye_item", "")

	val abilityScrolls: List<String> get() = strings("ability_scroll")

	val boosters: List<String> get() = strings("boosters")

	val drillUpgrades: List<String> get() = DRILL_PARTS.mapNotNull { named(tag.getStringOr(it, "")) }

	val rodParts: List<String> get() = ROD_PARTS.mapNotNull { named(tag.getCompoundOrEmpty(it).getStringOr("part", "")) }

	private fun strings(key: String): List<String> {
		val list = tag.getListOrEmpty(key)
		if (list.isEmpty) return emptyList()
		return list.indices.mapNotNull { named(list.getStringOr(it, "")) }
	}

	internal fun rarity(stack: ItemStack): ItemRarity =
		resolvedRarity ?: ItemRarity.of(stack).also { resolvedRarity = it }

	companion object {
		val NONE = SkyBlockItem(
			id = "",
			uuid = "",
			marketId = "",
			pet = null,
			upgradeLevel = 0,
			rarityUpgrades = 0,
			hotPotatoCount = 0,
			artOfWar = 0,
			tunedTransmission = 0,
			reforge = "",
			enchantments = emptyMap(),
			runes = emptyMap(),
			attributes = emptyMap(),
			gems = null,
			ethermerge = false,
			donatedMuseum = false,
			timestamp = 0L,
			tag = CompoundTag(),
			knownRarity = ItemRarity.NONE
		)

		private const val ENCHANTED_BOOK = "ENCHANTED_BOOK"
		private const val RUNE = "RUNE"
		private const val UNIQUE_RUNE = "UNIQUE_RUNE"
		private const val POTION = "POTION"
		private const val PET = "PET"
		private const val ENHANCED = "-ENHANCED"

		private val DRILL_PARTS = listOf("drill_part_upgrade_module", "drill_part_engine", "drill_part_fuel_tank")
		private val ROD_PARTS = listOf("hook", "line", "sinker")

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

		private var warnedPetInfo: String? = null

		internal fun parse(data: CustomData): SkyBlockItem {
			val tag = data.copyTag()
			val id = hypixelId(tag)
			val pet = if (id == PET) petInfo(tag) else null
			val enchantments = levels(tag, "enchantments")
			val runes = levels(tag, "runes")
			return SkyBlockItem(
				id = id,
				uuid = tag.getStringOr("uuid", ""),
				marketId = marketId(id, pet, enchantments, runes, tag),
				pet = pet,
				upgradeLevel = tag.getIntOr("upgrade_level", tag.getIntOr("dungeon_item_level", 0)),
				rarityUpgrades = tag.getIntOr("rarity_upgrades", 0),
				hotPotatoCount = tag.getIntOr("hot_potato_count", 0),
				artOfWar = tag.getIntOr("art_of_war_count", 0),
				tunedTransmission = tag.getIntOr("tuned_transmission", 0),
				reforge = tag.getStringOr("modifier", ""),
				enchantments = enchantments,
				runes = runes,
				attributes = levels(tag, "attributes", upperCaseKeys = true),
				gems = tag.getCompound("gems").orElse(null),
				ethermerge = tag.getBooleanOr("ethermerge", false),
				donatedMuseum = tag.getBooleanOr("donated_museum", false),
				timestamp = tag.getLongOr("timestamp", 0L),
				tag = tag
			)
		}

		private fun hypixelId(tag: CompoundTag): String {
			val raw = tag.getStringOr("id", "")
			return if (raw.indexOf(':') < 0) raw else raw.replace(':', '-')
		}

		private fun marketId(
			id: String,
			pet: PetInfo?,
			enchantments: Map<String, Int>,
			runes: Map<String, Int>,
			tag: CompoundTag
		): String = when (id) {
			ENCHANTED_BOOK -> keyedLevel(ENCHANTED_BOOK, enchantments)
			RUNE, UNIQUE_RUNE -> keyedLevel(RUNE, runes)
			POTION -> potionId(tag)
			PET -> if (pet == null) "" else "$PET-${pet.type}-${pet.tier}"
			else -> id
		}

		private fun keyedLevel(prefix: String, levels: Map<String, Int>): String {
			val single = levels.entries.singleOrNull() ?: return ""
			return if (single.value > 0) "$prefix-${single.key.uppercase(Locale.ROOT)}-${single.value}" else ""
		}

		private fun potionId(tag: CompoundTag): String {
			val potion = tag.getStringOr("potion", "")
			if (potion.isEmpty()) return ""
			val level = tag.getIntOr("potion_level", 0)
			if (level <= 0) return ""
			val enhanced = if (tag.getBooleanOr("enhanced", false)) ENHANCED else ""
			return "$POTION-${potion.uppercase(Locale.ROOT)}-$level$enhanced"
		}

		private fun levels(tag: CompoundTag, key: String, upperCaseKeys: Boolean = false): Map<String, Int> {
			val compound = tag.getCompoundOrEmpty(key)
			if (compound.isEmpty) return emptyMap()
			val levels = LinkedHashMap<String, Int>(compound.size())
			for (entry in compound.keySet()) {
				levels[if (upperCaseKeys) entry.uppercase(Locale.ROOT) else entry] = compound.getIntOr(entry, 0)
			}
			return levels
		}

		internal fun petInfoOf(json: JsonObject): PetInfo? {
			val type = json.text("type") ?: return null
			val tier = json.text("tier") ?: return null
			return PetInfo(
				type = type,
				tier = tier,
				exp = json.number("exp") ?: 0.0,
				heldItem = json.text("heldItem"),
				candyUsed = (json.number("candyUsed") ?: 0.0).toInt(),
				skin = json.text("skin")
			)
		}

		private fun petInfo(tag: CompoundTag): PetInfo? {
			val raw = tag.getStringOr("petInfo", "")
			if (raw.isEmpty()) return null
			val json = parsePetInfo(raw) ?: return null
			return petInfoOf(json) ?: unreadablePetInfo(raw)
		}

		private fun parsePetInfo(raw: String): JsonObject? = try {
			JsonParser.parseString(raw).takeIf(JsonElement::isJsonObject)?.asJsonObject
		} catch (malformed: JsonParseException) {
			unreadablePetInfo(raw, malformed)
		}

		private fun unreadablePetInfo(raw: String, malformed: JsonParseException? = null): Nothing? {
			if (warnedPetInfo != raw) {
				warnedPetInfo = raw
				log.warn("Dhen could not read the pet info {}", raw, malformed)
			}
			return null
		}

		private fun named(raw: String): String? = raw.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT)
	}
}
