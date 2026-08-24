package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.item.HeldItem
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class NetworthCategory(label: String? = null) {
	CURRENCY("Purse/Bank"),
	INVENTORY,
	ARMOR,
	ENDERCHEST,
	BACKPACKS,
	SACKS,
	PETS,
	LOADOUT,
	EQUIPMENT,
	TALISMAN_BAG,
	FISHING_BAG,
	QUIVER_BAG,
	PERSONAL_VAULT;

	val label: String = label ?: titleCase(name)
}

interface NetworthSource {
	fun items(category: NetworthCategory): List<HeldItem> = emptyList()

	fun sacks(): Map<String, Long> = emptyMap()

	fun pets(): List<PetInfo> = emptyList()

	fun currency(): Map<String, Long> = emptyMap()
}

class NetworthReport internal constructor(
	val total: Long,
	val categories: Map<NetworthCategory, Map<String, Long>>
)

object Networth {
	fun of(source: NetworthSource, prices: PriceSource): NetworthReport? {
		if (!ItemRepo.ready) return null
		val crafts = CraftCost(prices)
		val categories = LinkedHashMap<NetworthCategory, Map<String, Long>>(NetworthCategory.entries.size)
		var total = 0L
		for (category in NetworthCategory.entries) {
			val values = values(source, category, prices, crafts)
			categories[category] = values
			for (value in values.values) total += value
		}
		return NetworthReport(total, categories)
	}

	suspend fun ofAsync(source: NetworthSource, prices: PriceSource): NetworthReport? =
		withContext(Dispatchers.IO) { of(source, prices) }

	private fun values(
		source: NetworthSource,
		category: NetworthCategory,
		prices: PriceSource,
		crafts: CraftCost
	): Map<String, Long> = when (category) {
		NetworthCategory.CURRENCY -> source.currency()
		NetworthCategory.SACKS -> sacks(source.sacks(), prices)
		NetworthCategory.PETS -> pets(source.pets(), prices)
		else -> items(source.items(category), prices, crafts)
	}

	private fun items(held: List<HeldItem>, prices: PriceSource, crafts: CraftCost): Map<String, Long> {
		if (held.isEmpty()) return emptyMap()
		val values = LinkedHashMap<String, Long>(held.size)
		for (entry in held) {
			val item = entry.item
			if (item === SkyBlockItem.NONE) continue
			val valuation = ItemValue.of(item, entry.rarity, prices, crafts)
			values.merge(entry.name, (valuation.total * entry.count).toLong(), Long::plus)
		}
		return values
	}

	private fun sacks(sacks: Map<String, Long>, prices: PriceSource): Map<String, Long> {
		if (sacks.isEmpty()) return emptyMap()
		val values = LinkedHashMap<String, Long>(sacks.size)
		for ((marketId, amount) in sacks) {
			val unitPrice = Prices.priceOr(marketId, prices, 0.0)
			if (unitPrice <= 0.0 || amount <= 0L) continue
			values.merge(ItemValue.displayName(marketId), (unitPrice * amount).toLong(), Long::plus)
		}
		return values
	}

	private fun pets(pets: List<PetInfo>, prices: PriceSource): Map<String, Long> {
		if (pets.isEmpty()) return emptyMap()
		val values = LinkedHashMap<String, Long>(pets.size)
		for (pet in pets) {
			val value = Prices.priceOr(ItemValue.listedPet(pet, prices), prices, 0.0) +
				Prices.priceOr(pet.heldItem.orEmpty(), prices, 0.0) +
				Prices.priceOr(pet.skin.orEmpty(), prices, 0.0)
			values.merge(titleCase("${pet.tier}_${pet.type}"), value.toLong(), Long::plus)
		}
		return values
	}
}

private fun titleCase(name: String): String =
	name.split('_').joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) }
