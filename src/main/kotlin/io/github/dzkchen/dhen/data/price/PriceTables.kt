package io.github.dzkchen.dhen.data.price

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
			val id = item.get("id")?.takeIf(JsonElement::isJsonPrimitive)?.asString ?: continue
			prices[id.replace(':', '-')] = item.numeric(NPC_SELL_PRICE) ?: continue
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
		for ((key, element) in json.entrySet()) {
			if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) continue
			val id = marketId(key) ?: continue
			val price = element.asDouble
			val held = prices[id]
			if (held == null || price < held) prices[id] = price
		}
		return prices
	}

	private fun JsonObject.numeric(member: String): Double? =
		get(member)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
