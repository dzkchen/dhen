package io.github.dzkchen.dhen.data.price

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class BazaarOrder internal constructor(val pricePerUnit: Double, val amount: Long, val orders: Long)

class BazaarQuickStatus internal constructor(
	val buyPrice: Double,
	val buyVolume: Long,
	val buyMovingWeek: Long,
	val buyOrders: Long,
	val sellPrice: Double,
	val sellVolume: Long,
	val sellMovingWeek: Long,
	val sellOrders: Long
)

class BazaarProduct internal constructor(
	val productId: String,
	val instantBuy: Double,
	val instantSell: Double,
	val buySummary: List<BazaarOrder>,
	val sellSummary: List<BazaarOrder>,
	val quickStatus: BazaarQuickStatus
)

class BazaarSnapshot internal constructor(
	val lastUpdated: Long,
	private val products: Map<String, BazaarProduct>
) {
	val size: Int get() = products.size

	fun product(marketId: String): BazaarProduct? = products[marketId]

	internal companion object {
		private const val ENCHANTMENT = "ENCHANTMENT_"
		private const val ENCHANTED_BOOK = "ENCHANTED_BOOK"

		fun parse(body: String): BazaarSnapshot? {
			val json = JsonParser.parseString(body).asJsonObject
			if (json.get("success")?.asBoolean != true) return null
			val products = json.getAsJsonObject("products") ?: return null
			val parsed = HashMap<String, BazaarProduct>(products.size())
			for ((key, element) in products.entrySet()) parsed[marketId(key)] = product(key, element.asJsonObject)
			return BazaarSnapshot(json.get("lastUpdated")?.asLong ?: 0L, parsed)
		}

		fun marketId(productId: String): String {
			if (productId.startsWith(ENCHANTMENT)) {
				val level = productId.substringAfterLast('_')
				if (level.isNotEmpty() && level.all(Char::isDigit)) {
					val enchant = productId.substring(ENCHANTMENT.length, productId.length - level.length - 1)
					if (enchant.isNotEmpty()) return "$ENCHANTED_BOOK-$enchant-$level"
				}
			}
			return productId.replace(':', '-')
		}

		private fun product(key: String, json: JsonObject): BazaarProduct {
			val buy = summary(json, "buy_summary")
			val sell = summary(json, "sell_summary")
			return BazaarProduct(
				productId = json.get("product_id")?.takeIf(JsonElement::isJsonPrimitive)?.asString ?: key,
				instantBuy = buy.minOfOrNull(BazaarOrder::pricePerUnit) ?: 0.0,
				instantSell = sell.maxOfOrNull(BazaarOrder::pricePerUnit) ?: 0.0,
				buySummary = buy,
				sellSummary = sell,
				quickStatus = quickStatus(json.getAsJsonObject("quick_status"))
			)
		}

		private fun summary(json: JsonObject, member: String): List<BazaarOrder> {
			val array = json.getAsJsonArray(member) ?: return emptyList()
			return array.map {
				val order = it.asJsonObject
				BazaarOrder(order.number("pricePerUnit"), order.long("amount"), order.long("orders"))
			}
		}

		private fun quickStatus(json: JsonObject?): BazaarQuickStatus = BazaarQuickStatus(
			buyPrice = json.number("buyPrice"),
			buyVolume = json.long("buyVolume"),
			buyMovingWeek = json.long("buyMovingWeek"),
			buyOrders = json.long("buyOrders"),
			sellPrice = json.number("sellPrice"),
			sellVolume = json.long("sellVolume"),
			sellMovingWeek = json.long("sellMovingWeek"),
			sellOrders = json.long("sellOrders")
		)

		private fun JsonObject?.number(member: String): Double = numeric(member)?.asDouble ?: 0.0

		private fun JsonObject?.long(member: String): Long = numeric(member)?.asLong ?: 0L

		private fun JsonObject?.numeric(member: String): JsonElement? =
			this?.get(member)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
	}
}
