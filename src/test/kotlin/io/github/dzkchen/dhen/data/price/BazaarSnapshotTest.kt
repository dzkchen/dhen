package io.github.dzkchen.dhen.data.price

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BazaarSnapshotTest {
	@Test
	fun `a reply the api refused is no snapshot at all`() {
		assertNull(BazaarSnapshot.parse("{\"success\":false,\"cause\":\"down\"}"))
	}

	@Test
	fun `the reply timestamp every consumer refreshes on is kept`() {
		assertEquals(1787171886917L, snapshot().lastUpdated)
	}

	@Test
	fun `the cheapest offer is the instant buy and the richest order is the instant sell`() {
		val product = snapshot().product("ENCHANTED_DIAMOND")

		assertNotNull(product)
		assertEquals(1349.2, product?.instantBuy)
		assertEquals(1262.8, product?.instantSell)
	}

	@Test
	fun `the whole order book and quick status reach a consumer`() {
		val product = snapshot().product("ENCHANTED_DIAMOND") ?: error("no product")

		assertEquals(2, product.buySummary.size)
		assertEquals(1, product.sellSummary.size)
		assertEquals(7386L, product.sellSummary[0].amount)
		assertEquals(2L, product.buySummary[1].orders)
		assertEquals(809227L, product.quickStatus.sellVolume)
		assertEquals(110L, product.quickStatus.buyOrders)
	}

	@Test
	fun `an enchantment product answers to the market id of the book`() {
		val product = snapshot().product("ENCHANTED_BOOK-ANGLER-6")

		assertEquals("ENCHANTMENT_ANGLER_6", product?.productId)
		assertEquals(2, snapshot().size)
	}

	private fun snapshot(): BazaarSnapshot = BazaarSnapshot.parse(REPLY) ?: error("no snapshot")

	private companion object {
		private val REPLY = """
			{
			  "success": true,
			  "lastUpdated": 1787171886917,
			  "products": {
			    "ENCHANTED_DIAMOND": {
			      "product_id": "ENCHANTED_DIAMOND",
			      "sell_summary": [{"amount": 7386, "pricePerUnit": 1262.8, "orders": 1}],
			      "buy_summary": [
			        {"amount": 100, "pricePerUnit": 1349.2, "orders": 1},
			        {"amount": 200, "pricePerUnit": 1350.5, "orders": 2}
			      ],
			      "quick_status": {
			        "productId": "ENCHANTED_DIAMOND",
			        "sellPrice": 1262.7, "sellVolume": 809227, "sellMovingWeek": 23005907, "sellOrders": 25,
			        "buyPrice": 1349.2, "buyVolume": 1393316, "buyMovingWeek": 5624829, "buyOrders": 110
			      }
			    },
			    "ENCHANTMENT_ANGLER_6": {
			      "product_id": "ENCHANTMENT_ANGLER_6",
			      "sell_summary": [],
			      "buy_summary": [],
			      "quick_status": {}
			    }
			  }
			}
		""".trimIndent()
	}
}
