package io.github.dzkchen.dhen.data.price

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

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
			if (!json.flag("success")) return null
			val products = json.obj("products") ?: return null
			val parsed = HashMap<String, BazaarProduct>(products.size())
			for ((key, element) in products.entrySet()) {
				val fields = element as? JsonObject ?: continue
				parsed[marketId(key)] = product(key, fields)
			}
			return BazaarSnapshot(json.long("lastUpdated"), parsed)
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
				productId = json.text("product_id") ?: key,
				instantBuy = buy.minOfOrNull(BazaarOrder::pricePerUnit) ?: 0.0,
				instantSell = sell.maxOfOrNull(BazaarOrder::pricePerUnit) ?: 0.0,
				buySummary = buy,
				sellSummary = sell,
				quickStatus = quickStatus(json.obj("quick_status"))
			)
		}

		private fun summary(json: JsonObject, member: String): List<BazaarOrder> {
			val array = json.array(member) ?: return emptyList()
			return array.mapNotNull { element ->
				val order = element as? JsonObject ?: return@mapNotNull null
				BazaarOrder(
					order.number("pricePerUnit") ?: 0.0,
					order.long("amount"),
					order.long("orders")
				)
			}
		}

		private val NO_QUICK_STATUS = BazaarQuickStatus(0.0, 0L, 0L, 0L, 0.0, 0L, 0L, 0L)

		private fun quickStatus(json: JsonObject?): BazaarQuickStatus {
			if (json == null) return NO_QUICK_STATUS
			return BazaarQuickStatus(
				buyPrice = json.number("buyPrice") ?: 0.0,
				buyVolume = json.long("buyVolume"),
				buyMovingWeek = json.long("buyMovingWeek"),
				buyOrders = json.long("buyOrders"),
				sellPrice = json.number("sellPrice") ?: 0.0,
				sellVolume = json.long("sellVolume"),
				sellMovingWeek = json.long("sellMovingWeek"),
				sellOrders = json.long("sellOrders")
			)
		}
	}
}
