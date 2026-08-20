package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.event.withoutCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.world.item.ItemStack
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
	fun items(category: NetworthCategory): List<ItemStack> = emptyList()

	fun sacks(): Map<String, Long> = emptyMap()

	fun pets(): List<PetInfo> = emptyList()

	fun currency(): Map<String, Long> = emptyMap()
}

class NetworthReport internal constructor(
	val total: Long,
	val categories: Map<NetworthCategory, Map<String, Long>>
)

object Networth {
	private const val PET = "PET"

	fun of(source: NetworthSource, prices: PriceSource): NetworthReport {
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

	suspend fun ofAsync(source: NetworthSource, prices: PriceSource): NetworthReport =
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

	private fun items(stacks: List<ItemStack>, prices: PriceSource, crafts: CraftCost): Map<String, Long> {
		if (stacks.isEmpty()) return emptyMap()
		val values = LinkedHashMap<String, Long>(stacks.size)
		for (stack in stacks) {
			val data = SkyBlockItems.customData(stack) ?: continue
			val item = SkyBlockItem.parse(data)
			val valuation = ItemValue.of(item, item.rarity(stack), prices, crafts)
			values.merge(name(stack), (valuation.total * stack.count).toLong(), Long::plus)
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
			val value = Prices.priceOr("$PET-${pet.type}-${pet.tier}", prices, 0.0) +
				Prices.priceOr(pet.heldItem.orEmpty(), prices, 0.0) +
				Prices.priceOr(pet.skin.orEmpty(), prices, 0.0)
			values.merge(titleCase("${pet.tier}_${pet.type}"), value.toLong(), Long::plus)
		}
		return values
	}

	private fun name(stack: ItemStack): String = withoutCodes(stack.hoverName.string)
}

private fun titleCase(name: String): String =
	name.split('_').joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) }
