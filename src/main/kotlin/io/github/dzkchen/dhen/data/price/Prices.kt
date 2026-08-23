package io.github.dzkchen.dhen.data.price

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.CachedFeed
import io.github.dzkchen.dhen.data.RequirementPump
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.BazaarUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.nanoseconds

object Prices {
	private const val BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar"
	private const val LOWEST_BIN_URL = "https://lb.tricked.dev/lowestbins"
	private const val SPARE_LOWEST_BIN_URL = "https://api.eliteskyblock.com/resources/auctions/neu?mode=smooth"
	private const val NPC_URL = "https://api.hypixel.net/v2/resources/skyblock/items"

	private const val UNPRICED = Double.NaN

	private val POLL = 30.seconds

	private val bazaarFeed =
		CachedFeed("bazaar prices", BAZAAR_URL, 2.minutes, BazaarSnapshot::size, BazaarSnapshot::parse)
	private val lowestBinFeed =
		CachedFeed("lowest BIN prices", LOWEST_BIN_URL, 5.minutes, Map<String, Double>::size, PriceTables::lowestBins)
	private val spareLowestBinFeed =
		CachedFeed("spare lowest BIN prices", SPARE_LOWEST_BIN_URL, 5.minutes, Map<String, Double>::size, PriceTables::neuLowestBins)
	private val npcFeed =
		CachedFeed("NPC prices", NPC_URL, 6.hours, Map<String, Double>::size, PriceTables::npcPrices)

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val pump = RequirementPump()
	private val announced = AtomicBoolean()

	@Volatile
	private var host: Host? = null

	internal val feeds: Array<CachedFeed<*>> = arrayOf(bazaarFeed, lowestBinFeed, spareLowestBinFeed, npcFeed)

	val required: Int get() = pump.count

	internal val polling: Boolean get() = pump.polling

	val bazaar: BazaarSnapshot? get() = bazaarFeed.value

	fun product(marketId: String): BazaarProduct? = bazaarFeed.value?.product(marketId)

	fun price(marketId: String, source: PriceSource): Double? {
		val price = priceOr(marketId, source, UNPRICED)
		return if (price.isNaN()) null else price
	}

	fun priceOr(marketId: String, source: PriceSource, unpriced: Double): Double {
		if (marketId.isEmpty()) return unpriced
		return when (source) {
			PriceSource.NPC_SELL -> npc(marketId, unpriced)
			PriceSource.LOWEST_BIN -> lowestBin(marketId, unpriced)
			PriceSource.BAZAAR_INSTANT_BUY, PriceSource.BAZAAR_INSTANT_SELL -> {
				val bazaar = bazaarSide(marketId, source)
				if (!bazaar.isNaN()) bazaar else {
					val bin = lowestBin(marketId, UNPRICED)
					if (!bin.isNaN()) bin else npc(marketId, unpriced)
				}
			}
		}
	}

	fun require(): Handle {
		val host = host ?: return Handle {}
		return pump.require({ this@Prices.host === host }) { host.scope.launch { poll(host) } }
	}

	internal fun ageSeconds(feed: CachedFeed<*>): Long {
		val host = host ?: return 0L
		return if (!feed.loaded) 0L else (host.clock.nanoTime() - feed.updatedAt).nanoseconds.inWholeSeconds
	}

	internal fun install(
		scope: CoroutineScope,
		bus: EventBus,
		clientDispatcher: CoroutineDispatcher,
		web: WebSource = WebClient(),
		clock: NanoClock = NanoClock.SYSTEM,
		onHypixel: () -> Boolean = { SkyBlockLocation.onHypixel }
	) {
		uninstall()
		host = Host(scope, bus, clientDispatcher, web, clock, onHypixel)
	}

	internal fun uninstall() {
		host = null
		pump.reset()
		announced.set(false)
		for (feed in feeds) feed.reset()
	}

	private fun npc(marketId: String, unpriced: Double): Double = npcFeed.value?.get(marketId) ?: unpriced

	private fun lowestBin(marketId: String, unpriced: Double): Double =
		lowestBinFeed.value?.get(marketId) ?: spareLowestBinFeed.value?.get(marketId) ?: unpriced

	private fun bazaarSide(marketId: String, source: PriceSource): Double {
		val product = bazaarFeed.value?.product(marketId) ?: return UNPRICED
		val price = if (source == PriceSource.BAZAAR_INSTANT_BUY) product.instantBuy else product.instantSell
		return if (price > 0.0) price else UNPRICED
	}

	private suspend fun poll(host: Host) = coroutineScope {
		while (isActive) {
			if (pump.count > 0 && host.onHypixel()) refresh(host)
			delay(POLL)
		}
	}

	private suspend fun refresh(host: Host) = coroutineScope {
		val now = host.clock.nanoTime()
		val refreshed = feeds.filter { it.stale(now) }
			.map { feed -> async { feed to feed.refresh(host.web, now) { this@Prices.host === host && isActive } } }
			.awaitAll()
		if (refreshed.none { (feed, landed) -> landed && feed === bazaarFeed }) return@coroutineScope
		val snapshot = bazaarFeed.value ?: return@coroutineScope
		if (announced.compareAndSet(false, true)) {
			log.info(
				"Dhen priced {} bazaar products, {} lowest BINs, {} spare lowest BINs and {} NPC items",
				snapshot.size,
				lowestBinFeed.size,
				spareLowestBinFeed.size,
				npcFeed.size
			)
		}
		val update = host.bus.type<BazaarUpdateEvent>()
		if (update.hasSubscribers) launch(host.clientDispatcher) { update.dispatch(BazaarUpdateEvent(snapshot)) }
	}

	private class Host(
		val scope: CoroutineScope,
		val bus: EventBus,
		val clientDispatcher: CoroutineDispatcher,
		val web: WebSource,
		val clock: NanoClock,
		val onHypixel: () -> Boolean
	)
}
