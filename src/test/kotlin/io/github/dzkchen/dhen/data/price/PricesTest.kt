package io.github.dzkchen.dhen.data.price

import io.github.dzkchen.dhen.event.BazaarUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PricesTest {
	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val bus = EventBus()
	private val source = FakeSource()
	private var onHypixel = true
	private var nanos = 0L

	@AfterEach
	fun uninstall() {
		Prices.uninstall()
	}

	@Test
	fun `a client that never asks for prices fetches nothing`() {
		install()

		assertEquals(0, source.requests.size)
		assertNull(Prices.bazaar)
		assertNull(Prices.price("HYPERION", PriceSource.LOWEST_BIN))
	}

	@Test
	fun `the first module that needs prices starts every feed`() {
		install()

		Prices.require()

		assertEquals(4, source.requests.size)
		assertEquals(1, Prices.required)
		assertEquals(1, Prices.bazaar?.size)
	}

	@Test
	fun `a client that is not on hypixel asks the api for nothing`() {
		onHypixel = false
		install()

		Prices.require()

		assertEquals(0, source.requests.size)
	}

	@Test
	fun `a bazaar item is priced from the side the caller asked for`() {
		install()
		Prices.require()

		assertEquals(1349.2, Prices.price("ENCHANTED_DIAMOND", PriceSource.BAZAAR_INSTANT_BUY))
		assertEquals(1262.8, Prices.price("ENCHANTED_DIAMOND", PriceSource.BAZAAR_INSTANT_SELL))
	}

	@Test
	fun `an auction item nobody sells on the bazaar falls back to the lowest bin`() {
		install()
		Prices.require()

		assertEquals(500.0, Prices.price("HYPERION", PriceSource.BAZAAR_INSTANT_BUY))
		assertEquals(500.0, Prices.price("HYPERION", PriceSource.LOWEST_BIN))
	}

	@Test
	fun `a source that names a list answers from that list alone, never from another`() {
		install()
		Prices.require()

		assertNull(Prices.price("MANDRAA", PriceSource.LOWEST_BIN))
		assertNull(Prices.price("ENCHANTED_DIAMOND", PriceSource.NPC_SELL))
		assertNull(Prices.price("ENCHANTED_DIAMOND", PriceSource.LOWEST_BIN))
	}

	@Test
	fun `a bazaar item priced while the bazaar is down falls to the auction list, not to nothing`() {
		source.unreachable += "bazaar"
		install()

		Prices.require()

		assertNull(Prices.bazaar)
		assertEquals(500.0, Prices.price("HYPERION", PriceSource.BAZAAR_INSTANT_BUY))
		assertNull(Prices.price("ENCHANTED_DIAMOND", PriceSource.BAZAAR_INSTANT_BUY))
	}

	@Test
	fun `an unpriced item reads back as the caller's own stand-in`() {
		install()
		Prices.require()

		assertEquals(-1.0, Prices.priceOr("NOTHING_LIKE_THIS", PriceSource.LOWEST_BIN, -1.0))
		assertEquals(500.0, Prices.priceOr("HYPERION", PriceSource.LOWEST_BIN, -1.0))
	}

	@Test
	fun `an item only the spare lowest bin lists is still priced`() {
		install()
		Prices.require()

		assertEquals(1200000.0, Prices.price("PET-AMMONITE-LEGENDARY", PriceSource.LOWEST_BIN))
	}

	@Test
	fun `an item that is neither traded nor auctioned falls back to the npc price`() {
		install()
		Prices.require()

		assertEquals(1.0, Prices.price("MANDRAA", PriceSource.BAZAAR_INSTANT_BUY))
		assertEquals(1.0, Prices.price("MANDRAA", PriceSource.NPC_SELL))
		assertNull(Prices.price("HYPERION", PriceSource.NPC_SELL))
	}

	@Test
	fun `a new bazaar snapshot tells its consumers`() {
		install()
		var landed: BazaarUpdateEvent? = null
		bus.type<BazaarUpdateEvent>().subscribe { landed = it }

		Prices.require()

		assertEquals(1787171886917L, landed?.snapshot?.lastUpdated)
	}

	@Test
	fun `every source being down leaves prices absent rather than broken`() {
		source.reachable = false
		install()

		Prices.require()

		assertNull(Prices.bazaar)
		assertNull(Prices.price("HYPERION", PriceSource.LOWEST_BIN))
		assertNull(Prices.product("ENCHANTED_DIAMOND"))
	}

	@Test
	fun `a reply that parses to nothing leaves the service unpriced rather than emptily priced`() {
		source.empty = true
		install()

		Prices.require()

		assertNull(Prices.bazaar)
		assertNull(Prices.price("HYPERION", PriceSource.LOWEST_BIN))
	}

	@Test
	fun `tearing the service down stops it fetching, and a fresh one fetches again`() {
		install()
		Prices.require()
		Prices.uninstall()

		Prices.require()

		assertEquals(4, source.requests.size)
		assertNull(Prices.bazaar)

		install()
		Prices.require()

		assertEquals(8, source.requests.size)
		assertEquals(1, Prices.bazaar?.size)
	}

	@Test
	fun `a second module needing prices does not refetch`() {
		install()

		Prices.require()
		Prices.require()

		assertEquals(4, source.requests.size)
		assertEquals(2, Prices.required)
	}

	@Test
	fun `releasing a requirement drops it once however often it is released`() {
		install()
		val handle = Prices.require()

		handle.unsubscribe()
		handle.unsubscribe()

		assertEquals(0, Prices.required)
	}

	@Test
	fun `a requirement released after the service was torn down cannot drive the count negative`() {
		install()
		val handle = Prices.require()

		Prices.uninstall()
		install()
		handle.unsubscribe()

		assertEquals(0, Prices.required)
	}

	@Test
	fun `needing prices before the service is installed is a no-op`() {
		Prices.require()

		assertEquals(0, Prices.required)
		assertTrue(source.requests.isEmpty())
	}

	private fun install() {
		Prices.install(scope, bus, Dispatchers.Unconfined, source, { nanos }) { onHypixel }
	}

	private class FakeSource : WebSource {
		val requests = mutableListOf<String>()
		val unreachable = mutableSetOf<String>()
		var reachable = true
		var empty = false

		override fun text(url: String): String? {
			requests += url
			if (!reachable || unreachable.any(url::contains)) return null
			if (empty) return "{}"
			return when {
				url.contains("bazaar") -> BAZAAR
				url.contains("tricked") -> "{\"HYPERION\":500.0}"
				url.contains("eliteskyblock") -> "{\"AMMONITE;4\":1200000}"
				else -> "{\"items\":[{\"id\":\"MANDRAA\",\"npc_sell_price\":1}]}"
			}
		}
	}

	private companion object {
		private val BAZAAR = """
			{
			  "success": true,
			  "lastUpdated": 1787171886917,
			  "products": {
			    "ENCHANTED_DIAMOND": {
			      "product_id": "ENCHANTED_DIAMOND",
			      "sell_summary": [{"amount": 7386, "pricePerUnit": 1262.8, "orders": 1}],
			      "buy_summary": [{"amount": 100, "pricePerUnit": 1349.2, "orders": 1}],
			      "quick_status": {"sellPrice": 1262.7, "buyPrice": 1349.2, "buyOrders": 110}
			    }
			  }
			}
		""".trimIndent()
	}
}
