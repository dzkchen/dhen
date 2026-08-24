package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.CuratedConstants
import io.github.dzkchen.dhen.data.repo.EndcapEnchant
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.StarTier
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.regex.Pattern

class ValueLine internal constructor(val label: String, val amount: Double, val priced: Boolean)

class Valuation internal constructor(val total: Double, val base: Double, val breakdown: List<ValueLine>) {
	val modified: Boolean get() = total != base
}

object ItemValue {
	private const val ENCHANTED_BOOK = "ENCHANTED_BOOK"
	private const val BOOK_BUNDLE = "ENCHANTED_BOOK_BUNDLE_"
	private const val SKYBLOCK_COIN = "SKYBLOCK_COIN"
	private const val ESSENCE = "ESSENCE_"
	private const val GEM_SUFFIX = "_gem"
	private const val UNLOCKED_SLOTS = "unlocked_slots"
	private const val QUALITY = "quality"
	private const val EFFICIENCY = "efficiency"
	private const val KUUDRA_TIER_STARS = 10
	private const val HOT_POTATO_CAP = 10
	private const val FUMING_POTATO_CAP = 5
	private const val FIRST_MASTER_STAR = 5
	private const val MAX_COMBINE_STEPS = 5
	private const val FREE_EFFICIENCY_LEVELS = 5
	private const val RUNE = "RUNE"
	private const val UNIQUE_RUNE = "UNIQUE_RUNE"
	private const val ULTIMATE_WITHER_SCROLL = "ULTIMATE_WITHER_SCROLL"
	private const val BOOSTER = "_BOOSTER"
	private const val LEGENDARY_PET_LEVEL = 100
	private const val DRAGON_PET_LEVEL = 200

	private val MASTER_STARS =
		listOf("FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR")
	private val KUUDRA_TIERS = listOf("", "HOT", "BURNING", "FIERY", "INFERNAL")
	private val KUUDRA_ARMOR: Pattern = Pattern.compile(
		"(HOT|BURNING|FIERY|INFERNAL|)_?(?:AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS)"
	)
	private val GEMSTONES = setOf(
		"JADE", "AMBER", "TOPAZ", "SAPPHIRE", "AMETHYST", "JASPER",
		"RUBY", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT"
	)
	private val QUALITIES = setOf("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT")
	private val WITHER_SCROLLS = listOf("IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL")
	private val BUILT_IN_SILEX = mapOf("STONK_PICKAXE" to 1, "PROMISING_SPADE" to 5)

	private val MODIFIERS: List<(Fold) -> Double> = listOf(
		::reforgeStone,
		::recombobulator,
		::artOfWar,
		::artOfPeace,
		::etherwarp,
		::powerScroll,
		::woodSingularity,
		::jalapenoBook,
		::statsBook,
		::enrichment,
		::divanPowderCoating,
		::mithrilInfusion,
		::freeWill,
		::crimsonPrestige,
		::stars,
		::masterStars,
		::hotPotatoBooks,
		::wetBook,
		::farmingForDummies,
		::overclocker,
		::silex,
		::transmissionTuners,
		::manaDisintegrators,
		::polarvoidBook,
		::bookwormBook,
		::pocketSackInASack,
		::helmetSkin,
		::armorDye,
		::rune,
		::abilityScrolls,
		::petCosmetics,
		::boosters,
		::drillUpgrades,
		::rodParts,
		::gemstoneSlotUnlockCost,
		::gemstones,
		::enchantments
	)

	fun of(stack: ItemStack, source: PriceSource): Valuation =
		of(SkyBlockItems.of(stack), SkyBlockItems.rarity(stack), source)

	fun of(item: SkyBlockItem, rarity: ItemRarity, source: PriceSource): Valuation =
		of(item, rarity, source, CraftCost(source))

	internal fun of(pet: PetInfo, source: PriceSource, crafts: CraftCost): Valuation =
		of(SkyBlockItem.ofPet(pet), ItemRarity.of(pet), source, crafts)

	internal fun of(item: SkyBlockItem, rarity: ItemRarity, source: PriceSource, crafts: CraftCost): Valuation {
		val fold = Fold(item, rarity, source, crafts)
		val base = baseItem(fold)
		var total = if (item.id == ENCHANTED_BOOK) 0.0 else base
		for (modifier in MODIFIERS) total += modifier(fold)
		return Valuation(total, base, fold.lines)
	}

	private fun baseItem(fold: Fold): Double {
		val marketId = fold.item.marketId
		if (marketId.isEmpty()) return 0.0
		if (fold.item.id.startsWith(BOOK_BUNDLE)) return fold.note(displayName(marketId))
		if (fold.item.id == ENCHANTED_BOOK) {
			val price = fold.price(marketId)
			return if (price.isNaN()) 0.0 else price
		}
		val pet = fold.item.pet ?: return fold.base(marketId, displayName(marketId))
		val level = petLevel(pet)
		return fold.base(listedPet(marketId, level, fold.source), "${displayName(marketId)} level $level")
	}

	private fun listedPet(marketId: String, level: Int, source: PriceSource): String {
		val leveled = marketId + levelSuffix(level)
		return if (leveled != marketId && Prices.price(leveled, source) != null) leveled else marketId
	}

	private fun petLevel(pet: PetInfo): Int = ItemRepo.constants.petLevel(pet.type, pet.tier, pet.exp)

	private fun levelSuffix(level: Int): String = when {
		level >= DRAGON_PET_LEVEL -> "-$DRAGON_PET_LEVEL"
		level >= LEGENDARY_PET_LEVEL -> "-$LEGENDARY_PET_LEVEL"
		else -> ""
	}

	private fun petCosmetics(fold: Fold): Double {
		val pet = fold.item.pet ?: return 0.0
		return fold.cosmetic("Held item", pet.heldItem.orEmpty()) + fold.cosmetic("Skin", pet.skin.orEmpty())
	}

	private fun reforgeStone(fold: Fold): Double {
		val reforge = ItemRepo.constants.reforgeStone(fold.item.reforge) ?: return 0.0
		val applyCost = applyCost(reforge.costs, fold.rarity, fold.item.isRecombobulated) ?: return 0.0
		return fold.entry(reforge.stone, label = "Reforge: ${reforge.reforge}") +
			fold.coins("Reforge apply cost", applyCost.toDouble())
	}

	private fun applyCost(costs: Map<String, Long>, rarity: ItemRarity, recombobulated: Boolean): Long? {
		if (costs.isEmpty() || rarity == ItemRarity.NONE) return null
		val applied = when {
			!recombobulated || rarity > ItemRarity.MYTHIC -> rarity
			else -> ItemRarity.entries[rarity.ordinal - 1].takeIf { it != ItemRarity.NONE } ?: return null
		}
		return costs[applied.name] ?: if (applied > ItemRarity.MYTHIC) costs[ItemRarity.LEGENDARY.name] else null
	}

	private fun recombobulator(fold: Fold): Double = fold.once(fold.item.isRecombobulated, "RECOMBOBULATOR_3000")

	private fun artOfWar(fold: Fold): Double = fold.once(fold.item.artOfWar > 0, "THE_ART_OF_WAR")

	private fun artOfPeace(fold: Fold): Double = fold.once(fold.item.artOfPeace, "THE_ART_OF_PEACE")

	private fun etherwarp(fold: Fold): Double =
		if (!fold.item.ethermerge) 0.0
		else fold.entry("ETHERWARP_CONDUIT") + fold.entry("ETHERWARP_MERGER")

	private fun powerScroll(fold: Fold): Double {
		val scroll = fold.item.powerScroll
		return if (scroll.isEmpty()) 0.0 else fold.entry(scroll)
	}

	private fun woodSingularity(fold: Fold): Double = fold.once(fold.item.woodSingularities > 0, "WOOD_SINGULARITY")

	private fun jalapenoBook(fold: Fold): Double = fold.once(fold.item.jalapenoBooks > 0, "JALAPENO_BOOK")

	private fun statsBook(fold: Fold): Double = fold.once(fold.item.hasStatsBook, "BOOK_OF_STATS")

	private fun enrichment(fold: Fold): Double {
		val enrichment = fold.item.enrichment
		return if (enrichment.isEmpty()) 0.0 else fold.entry("TALISMAN_ENRICHMENT_$enrichment")
	}

	private fun divanPowderCoating(fold: Fold): Double =
		fold.once(fold.item.divanPowderCoating, "DIVAN_POWDER_COATING")

	private fun mithrilInfusion(fold: Fold): Double = fold.once(fold.item.mithrilInfusion, "MITHRIL_INFUSION")

	private fun freeWill(fold: Fold): Double = fold.once(fold.item.freeWill, "FREE_WILL")

	private fun wetBook(fold: Fold): Double = fold.counted(fold.item.wetBooks, "WET_BOOK")

	private fun farmingForDummies(fold: Fold): Double =
		fold.counted(fold.item.farmingForDummies, "FARMING_FOR_DUMMIES")

	private fun overclocker(fold: Fold): Double = fold.counted(fold.item.overclockers, "OVERCLOCKER_3000")

	private fun silex(fold: Fold): Double = fold.counted(silexTiers(fold.item), "SIL_EX")

	private fun silexTiers(item: SkyBlockItem): Int {
		val efficiency = item.enchantments[EFFICIENCY] ?: return 0
		return efficiency - FREE_EFFICIENCY_LEVELS - (BUILT_IN_SILEX[item.id] ?: 0)
	}

	private fun transmissionTuners(fold: Fold): Double =
		fold.counted(fold.item.tunedTransmission, "TRANSMISSION_TUNER")

	private fun manaDisintegrators(fold: Fold): Double =
		fold.counted(fold.item.manaDisintegrators, "MANA_DISINTEGRATOR")

	private fun polarvoidBook(fold: Fold): Double = fold.counted(fold.item.polarvoidBooks, "POLARVOID_BOOK")

	private fun bookwormBook(fold: Fold): Double = fold.counted(fold.item.bookwormBooks, "BOOKWORM_BOOK")

	private fun pocketSackInASack(fold: Fold): Double =
		fold.counted(fold.item.pocketSacksInASack, "POCKET_SACK_IN_A_SACK")

	private fun helmetSkin(fold: Fold): Double = fold.cosmetic("Skin", fold.item.helmetSkin)

	private fun armorDye(fold: Fold): Double = fold.cosmetic("Dye", fold.item.armorDye)

	private fun rune(fold: Fold): Double {
		if (fold.item.id == RUNE || fold.item.id == UNIQUE_RUNE) return 0.0
		val applied = fold.item.runes.entries.firstOrNull() ?: return 0.0
		if (applied.value <= 0) return 0.0
		return fold.cosmetic("Rune", "$RUNE-${applied.key.uppercase(Locale.ROOT)}-${applied.value}")
	}

	private fun abilityScrolls(fold: Fold): Double {
		val scrolls = LinkedHashSet<String>()
		for (scroll in fold.item.abilityScrolls) {
			if (scroll == ULTIMATE_WITHER_SCROLL) scrolls += WITHER_SCROLLS else scrolls += scroll
		}
		return fold.each(scrolls)
	}

	private fun boosters(fold: Fold): Double = fold.each(fold.item.boosters.map { it + BOOSTER })

	private fun drillUpgrades(fold: Fold): Double = fold.each(fold.item.drillUpgrades)

	private fun rodParts(fold: Fold): Double = fold.each(fold.item.rodParts)

	private fun hotPotatoBooks(fold: Fold): Double {
		val count = fold.item.hotPotatoCount
		if (count <= 0) return 0.0
		var total = fold.entry("HOT_POTATO_BOOK", count.coerceAtMost(HOT_POTATO_CAP))
		val fuming = (count - HOT_POTATO_CAP).coerceAtMost(FUMING_POTATO_CAP)
		if (fuming > 0) total += fold.entry("FUMING_POTATO_BOOK", fuming)
		return total
	}

	private fun stars(fold: Fold): Double {
		val kuudraTier = kuudraTier(fold.item.id)
		val tiers = starTiers(fold.item.id, kuudraTier)
		if (tiers.isEmpty()) return 0.0
		val earned = fold.item.upgradeLevel + (kuudraTier ?: 0) * KUUDRA_TIER_STARS
		if (earned <= 0) return 0.0
		val applied = earned.coerceAtMost(tiers.size)
		val essence = LinkedHashMap<String, Int>()
		val materials = LinkedHashMap<String, Int>()
		for (index in 0 until applied) {
			val tier = tiers[index]
			essence.merge(ESSENCE + tier.essence, tier.essenceAmount, Int::plus)
			for ((id, amount) in tier.materials) materials.merge(id, amount, Int::plus)
		}
		var total = fold.note("Stars $applied of ${tiers.size}")
		for ((id, amount) in essence) total += fold.entry(id, amount)
		for ((id, amount) in materials) total += fold.ingredient(id, amount, "Star coins")
		return total
	}

	private fun starTiers(id: String, kuudraTier: Int?): List<StarTier> {
		val constants = ItemRepo.constants
		if (kuudraTier == null) return constants.starTiers(id)
		val set = id.removePrefix(KUUDRA_TIERS[kuudraTier] + "_")
		return KUUDRA_TIERS.flatMap { constants.starTiers(if (it.isEmpty()) set else it + "_" + set) }
	}

	private fun kuudraTier(id: String): Int? {
		val matcher = KUUDRA_ARMOR.matcher(id)
		return if (matcher.matches()) KUUDRA_TIERS.indexOf(matcher.group(1)) else null
	}

	private fun crimsonPrestige(fold: Fold): Double {
		val tier = kuudraTier(fold.item.id) ?: return 0.0
		if (tier <= 0) return 0.0
		val costs = LinkedHashMap<String, Int>()
		for (index in 1..tier) {
			for ((id, amount) in CuratedConstants.crimsonPrestigeCost(KUUDRA_TIERS[index])) costs.merge(id, amount, Int::plus)
		}
		if (costs.isEmpty()) return 0.0
		var total = fold.note("Prestige: ${KUUDRA_TIERS[tier]}")
		for ((id, amount) in costs) total += fold.ingredient(id, amount, "Prestige coins")
		return total
	}

	private fun masterStars(fold: Fold): Double {
		if (kuudraTier(fold.item.id) != null) return 0.0
		val applied = (fold.item.upgradeLevel - FIRST_MASTER_STAR).coerceAtMost(MASTER_STARS.size)
		var total = 0.0
		for (index in 0 until applied) total += fold.entry(MASTER_STARS[index])
		return total
	}

	private fun gemstones(fold: Fold): Double {
		val gems = fold.item.gems ?: return 0.0
		val applied = LinkedHashMap<String, Int>()
		for (key in gems.keySet()) {
			if (key == UNLOCKED_SLOTS || key.endsWith(GEM_SUFFIX)) continue
			val quality = gems.getStringOr(key, "")
				.ifEmpty { gems.getCompoundOrEmpty(key).getStringOr(QUALITY, "") }
				.uppercase(Locale.ROOT)
			if (quality !in QUALITIES) continue
			val slot = key.substringBefore('_').uppercase(Locale.ROOT)
			val gemstone = if (slot in GEMSTONES) slot else gems.getStringOr(key + GEM_SUFFIX, "").uppercase(Locale.ROOT)
			if (gemstone !in GEMSTONES) continue
			applied.merge("${quality}_${gemstone}_GEM", 1, Int::plus)
		}
		var total = 0.0
		for ((id, count) in applied) total += fold.entry(id, count)
		return total
	}

	private fun gemstoneSlotUnlockCost(fold: Fold): Double {
		val gems = fold.item.gems ?: return 0.0
		val unlocked = gems.getListOrEmpty(UNLOCKED_SLOTS)
		if (unlocked.isEmpty) return 0.0
		val costs = LinkedHashMap<String, Int>()
		var priced = 0
		for (index in unlocked.indices) {
			val slot = ItemRepo.constants.gemstoneSlotCost(fold.item.id, unlocked.getStringOr(index, ""))
			if (slot.isEmpty()) continue
			priced++
			for ((id, amount) in slot) costs.merge(id, amount, Int::plus)
		}
		if (costs.isEmpty()) return 0.0
		var total = fold.note("Unlocked gemstone slots: $priced")
		for ((id, amount) in costs) total += fold.ingredient(id, amount, "Slot coins")
		return total
	}

	private fun enchantments(fold: Fold): Double {
		val bundled = fold.item.id.startsWith(BOOK_BUNDLE)
		var total = 0.0
		for ((raw, level) in fold.item.enchantments) {
			if (raw == EFFICIENCY || level <= 0) continue
			val enchantment = raw.uppercase(Locale.ROOT)
			if (CuratedConstants.grantedFree(enchantment, level, fold.item.id)) continue
			val endcaps = CuratedConstants.endcaps(enchantment)
			for (endcap in endcaps) if (level > endcap.requiredLevel) total += fold.entry(endcap.endcapItem)
			val count = if (bundled) CuratedConstants.bookBundleAmount(enchantment) else 1
			total += bookPrice(fold, enchantment, bookLevel(enchantment, endcaps, level), count)
		}
		return total
	}

	private fun bookLevel(enchantment: String, endcaps: List<EndcapEnchant>, level: Int): Int = when {
		enchantment in CuratedConstants.stackingEnchants -> 1
		endcaps.isEmpty() -> level
		else -> level.coerceAtMost(endcaps.minOf(EndcapEnchant::requiredLevel))
	}

	private fun bookPrice(fold: Fold, enchantment: String, level: Int, count: Int): Double {
		val listed = listedBookLevel(fold, enchantment, level) ?: return 0.0
		return fold.entry(bookId(enchantment, listed), count shl (level - listed))
	}

	private fun listedBookLevel(fold: Fold, enchantment: String, level: Int): Int? {
		val floor = (level - MAX_COMBINE_STEPS).coerceAtLeast(1)
		for (listed in level downTo floor) if (!fold.price(bookId(enchantment, listed)).isNaN()) return listed
		return null
	}

	private fun bookId(enchantment: String, level: Int): String = "$ENCHANTED_BOOK-$enchantment-$level"

	internal fun displayName(id: String): String =
		ItemRepo.item(id)?.displayName?.let(::withoutCodes)?.takeIf(String::isNotEmpty) ?: id

	private class Fold(
		val item: SkyBlockItem,
		val rarity: ItemRarity,
		val source: PriceSource,
		val crafts: CraftCost
	) {
		val lines = ArrayList<ValueLine>()

		fun price(marketId: String): Double = Prices.priceOr(marketId, source, Double.NaN)

		fun entry(marketId: String, count: Int = 1, label: String = displayName(marketId)): Double =
			priced(if (count > 1) "$label x$count" else label, price(marketId) * count)

		fun base(marketId: String, label: String): Double = priced(label, floored(marketId))

		private fun floored(marketId: String): Double {
			val npc = Prices.priceOr(marketId, PriceSource.NPC_SELL, Double.NaN)
			val price = price(marketId)
			if (npc.isNaN() || price != npc) return price
			val craft = crafts.of(marketId)
			return if (craft > npc) craft else price
		}

		private fun priced(label: String, amount: Double): Double {
			if (amount.isNaN()) {
				lines += ValueLine(label, 0.0, false)
				return 0.0
			}
			return coins(label, amount)
		}

		fun once(present: Boolean, marketId: String): Double = if (!present) 0.0 else entry(marketId)

		fun counted(count: Int, marketId: String): Double = if (count <= 0) 0.0 else entry(marketId, count)

		fun ingredient(marketId: String, amount: Int, coinLabel: String): Double =
			if (marketId == SKYBLOCK_COIN) coins("$coinLabel x$amount", amount.toDouble()) else entry(marketId, amount)

		fun cosmetic(kind: String, marketId: String): Double =
			if (marketId.isEmpty()) 0.0 else entry(marketId, label = "$kind: ${displayName(marketId)}")

		fun each(marketIds: Iterable<String>): Double {
			var total = 0.0
			for (marketId in marketIds) total += entry(marketId)
			return total
		}

		fun coins(label: String, amount: Double): Double {
			lines += ValueLine(label, amount, true)
			return amount
		}

		fun note(label: String): Double = coins(label, 0.0)
	}
}
