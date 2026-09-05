package io.github.dzkchen.dhen.data.price

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.text

internal object PriceTables {
	private const val BOOK = "ENCHANTED_BOOK"
	private const val PET = "PET"
	private const val POTION = "POTION"
	private const val RUNE = "RUNE"
	private const val ATTRIBUTE_SHARD = "ATTRIBUTE_SHARD"
	private const val POTION_NAME = "${POTION}_"
	private const val RUNE_NAME = "_$RUNE"
	private const val SHARD_NAME = "${ATTRIBUTE_SHARD}_"
	private const val NPC_SELL_PRICE = "npc_sell_price"

	val PET_TIERS = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

	fun lowestBins(body: String): Map<String, Double> = cheapest(body) { key, file -> file(key.replace(':', '-')) }

	fun neuLowestBins(body: String): Map<String, Double> = cheapest(body) { key, file ->
		neuMarketId(key)?.let(file)
		neuMaxedPetId(key)?.let(file)
		neuBookId(key)?.let(file)
	}

	fun neuMaxedPetId(key: String): String? {
		val level = key.substringAfterLast('+', "").toIntOrNull() ?: return null
		val marketId = neuMarketId(key) ?: return null
		return if (marketId.startsWith("$PET-")) "$marketId-$level" else null
	}

	fun neuBookId(key: String): String? {
		val name = key.substringBefore(';')
		if (name.isEmpty() || name.startsWith(POTION_NAME) || name.endsWith(RUNE_NAME) || name.startsWith(SHARD_NAME)) {
			return null
		}
		val level = key.substringAfter(';', "").substringBefore('+').toIntOrNull() ?: return null
		return if (level <= 0) null else "$BOOK-$name-$level"
	}

	fun npcPrices(body: String): Map<String, Double> {
		val items = JsonParser.parseString(body).asJsonObject.array("items") ?: return emptyMap()
		val prices = HashMap<String, Double>(items.size())
		for (element in items) {
			val item = element as? JsonObject ?: continue
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
			name.startsWith(POTION_NAME) -> "$POTION-${name.removePrefix(POTION_NAME)}-$tier"
			name.endsWith(RUNE_NAME) -> "$RUNE-${name.removeSuffix(RUNE_NAME)}-$tier"
			name.startsWith(SHARD_NAME) -> attributeShardId(name, tier)
			else -> PET_TIERS.getOrNull(tier.toIntOrNull() ?: -1)?.let { "$PET-$name-$it" }
		}
	}

	fun neuId(marketId: String): String? {
		val kind = marketId.substringBefore('-', "")
		if (kind.isEmpty() || kind.length + 1 >= marketId.length) return null
		val rest = marketId.substring(kind.length + 1)
		val name = rest.substringBeforeLast('-', "")
		val level = rest.substringAfterLast('-')
		if (name.isEmpty()) return null
		return when (kind) {
			BOOK -> level.toIntOrNull()?.let { "$name;$it" }
			PET -> if (level.toIntOrNull() == null) petId(name, level)
			else petId(name.substringBeforeLast('-', ""), name.substringAfterLast('-'))
			POTION -> level.toIntOrNull()?.let { "$POTION_NAME$name;$it" }
			RUNE -> level.toIntOrNull()?.let { "$name$RUNE_NAME;$it" }
			ATTRIBUTE_SHARD -> level.toIntOrNull()?.takeIf { it > 0 }?.let { "$SHARD_NAME$name;$it" }
			else -> null
		}
	}

	private fun petId(name: String, tier: String): String? =
		if (name.isEmpty()) null else PET_TIERS.indexOf(tier).takeIf { it >= 0 }?.let { "$name;$it" }

	private fun attributeShardId(name: String, tier: String): String? {
		val attribute = name.removePrefix(SHARD_NAME)
		val level = tier.toIntOrNull() ?: return null
		return if (attribute.isEmpty() || level <= 0) null else "$ATTRIBUTE_SHARD-$attribute-$level"
	}

	private inline fun cheapest(body: String, ids: (String, (String) -> Unit) -> Unit): Map<String, Double> {
		val json = JsonParser.parseString(body).asJsonObject
		val prices = HashMap<String, Double>(json.size())
		for (key in json.keySet()) {
			val price = json.number(key) ?: continue
			ids(key) { id ->
				val held = prices[id]
				if (held == null || price < held) prices[id] = price
			}
		}
		return prices
	}
}
