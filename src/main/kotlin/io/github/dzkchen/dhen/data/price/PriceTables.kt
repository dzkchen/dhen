package io.github.dzkchen.dhen.data.price

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.text

internal object PriceTables {
	private const val POTION = "POTION_"
	private const val RUNE = "_RUNE"
	private const val NPC_SELL_PRICE = "npc_sell_price"

	private val PET_TIERS = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

	fun lowestBins(body: String): Map<String, Double> = cheapest(body) { it.replace(':', '-') }

	fun neuLowestBins(body: String): Map<String, Double> = cheapest(body, ::neuMarketId)

	fun npcPrices(body: String): Map<String, Double> {
		val items = JsonParser.parseString(body).asJsonObject.getAsJsonArray("items") ?: return emptyMap()
		val prices = HashMap<String, Double>(items.size())
		for (element in items) {
			val item = element.asJsonObject
			val id = item.text("id") ?: continue
			prices[id.replace(':', '-')] = item.number(NPC_SELL_PRICE) ?: continue
		}
		return prices
	}

	fun neuMarketId(key: String): String? {
		val level = key.substringAfter(';', "")
		if (level.isEmpty()) return key.substringBefore('+')
		val name = key.substringBefore(';')
		val tier = level.substringBefore('+')
		return when {
			name.startsWith(POTION) -> "POTION-${name.removePrefix(POTION)}-$tier"
			name.endsWith(RUNE) -> "RUNE-${name.removeSuffix(RUNE)}-$tier"
			else -> PET_TIERS.getOrNull(tier.toIntOrNull() ?: -1)?.let { "PET-$name-$it" }
		}
	}

	private inline fun cheapest(body: String, marketId: (String) -> String?): Map<String, Double> {
		val json = JsonParser.parseString(body).asJsonObject
		val prices = HashMap<String, Double>(json.size())
		for (key in json.keySet()) {
			val price = json.number(key) ?: continue
			val id = marketId(key) ?: continue
			val held = prices[id]
			if (held == null || price < held) prices[id] = price
		}
		return prices
	}
}
