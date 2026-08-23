package io.github.dzkchen.dhen.diagnostic

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.TablistState
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.profile.PlayerProfiles
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.stats.ActionBarSegment
import io.github.dzkchen.dhen.data.stats.PlayerStats
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.data.value.ItemValue
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.TickHooks
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.ServerClock
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import java.net.URI
import java.util.Locale

class Diagnostics(
	private val manager: ModuleManager,
	private val heldItem: () -> ItemStack? = { Minecraft.getInstance().player?.mainHandItem }
) {
	private var forcedRequirement: Handle? = null
	private var forcedPrices: Handle? = null
	private var forcedMayor: Handle? = null

	var deepMode: Boolean
		get() = manager.profiler.deepMode
		set(value) {
			manager.profiler.deepMode = value
		}

	fun partyLines(): List<String> = buildList {
		if (!PartyHooks.active()) {
			add("${PartyHooks.feed}: no feed, off until restart")
			return@buildList
		}
		add(
			"Party: inParty=${PartyState.inParty}, leader=${PartyState.leader ?: "none"}, " +
				"members=${PartyState.members.size}, you=${PartyState.self ?: "unknown"}, " +
				"youLead=${PartyState.isLeader}, " +
				"packet=${if (PartyHooks.requesting) "available" else "refused"}"
		)
		for (member in PartyState.members) {
			add("  $member: role=${PartyState.roles[member] ?: "unconfirmed"}")
		}
	}

	fun scoreboardLines(): List<String> = buildList {
		if (!ScoreboardHooks.active()) {
			add("${ScoreboardHooks.feed}: no feed, off until restart")
		} else {
			add("Scoreboard title: '${ScoreboardState.strippedTitle}' (objective ${ScoreboardState.objective.ifEmpty { "none" }})")
			for (line in ScoreboardState.stripped) add("  $line")
		}
		if (!TablistHooks.active()) {
			add("${TablistHooks.feed}: no feed, off until restart")
			return@buildList
		}
		add("Tab list header: '${TablistState.strippedHeader}'")
		add("Tab list footer: '${TablistState.strippedFooter}'")
		for (line in TablistState.stripped) add("  $line")
	}

	fun tablistWidgetLines(): List<String> = buildList {
		if (!TabWidgetHooks.active()) {
			add("${TabWidgetHooks.feed}: no feed, off until restart")
			return@buildList
		}
		val active = TabWidget.entries.filter(TabWidgetState::active)
		add("Tab list widgets: ${active.size} active of ${TabWidget.entries.size}")
		for (widget in active) {
			add("  ${widget.name}")
			for (line in TabWidgetState.stripped(widget)) add("    $line")
		}
	}

	fun statsLines(): List<String> = buildList {
		if (!PlayerStatsHooks.active()) {
			add("${PlayerStatsHooks.feed}: no feed, off until restart")
			return@buildList
		}
		add("Player stats: health=${PlayerStats.health}/${PlayerStats.maxHealth}, defense=${PlayerStats.defense}, ehp=${PlayerStats.effectiveHp}")
		add("  mana=${PlayerStats.mana}/${PlayerStats.maxMana}, overflow=${PlayerStats.overflowMana}, speed=${PlayerStats.speed}")
		add("  vitality=${PlayerStats.vitality}/${PlayerStats.maxVitality}, shown=${PlayerStats.vitalityShown}")
		add("  stacks=${PlayerStats.netherArmorStacks}${PlayerStats.stackSymbol}, salvation=${PlayerStats.salvation}, secrets=${PlayerStats.secrets}/${PlayerStats.maxSecrets}")
		add("  hidden from the action bar: ${hiddenSegments()}")
	}

	private fun hiddenSegments(): String =
		ActionBarSegment.entries.filter(PlayerStats::hidden).joinToString().ifEmpty { "nothing" }

	fun repoLines(toggle: Boolean): List<String> = buildList {
		if (toggle) add(toggleRequirement())
		add("Item repo: state=${ItemRepo.state}, items=${ItemRepo.size}, needed by ${ItemRepo.required}, " +
			"commit=${ItemRepo.commit ?: "none"}")
		add("  constants: reforgeStones=${ItemRepo.constants.reforgeStoneCount}, " +
			"starredItems=${ItemRepo.constants.starredItemCount}")
	}

	private fun toggleRequirement(): String {
		val held = forcedRequirement
		forcedRequirement = if (held == null) ItemRepo.require() else null.also { held.unsubscribe() }
		return if (held == null) "Item repo: asked for, downloading in the background."
		else "Item repo: no longer asked for by this toggle."
	}

	fun priceLines(toggle: Boolean): List<String> = buildList {
		if (toggle) add(togglePrices())
		add("Prices: needed by ${Prices.required}, bazaar snapshot=${Prices.bazaar?.lastUpdated ?: 0L}")
		for (feed in Prices.feeds) {
			add("  ${feed.name}: entries=${feed.size}, age=${Prices.ageSeconds(feed)}s, failedRefreshes=${feed.failures}")
		}
	}

	private fun togglePrices(): String {
		val held = forcedPrices
		forcedPrices = if (held == null) Prices.require() else null.also { held.unsubscribe() }
		return if (held == null) "Prices: asked for, fetching in the background."
		else "Prices: no longer asked for by this toggle."
	}

	fun mayorLines(toggle: Boolean): List<String> = buildList {
		if (toggle) add(toggleMayor())
		if (!MayorService.active()) {
			add("Mayor: no service, the mayor data layer is not installed")
			return@buildList
		}
		val mayor = MayorService.mayor
		val date = MayorService.date
		add("Mayor: ${mayor?.name ?: "unknown"}, needed by ${MayorService.required}, failedRefreshes=${MayorService.failedRefreshes}")
		add("  SkyBlock date: year ${date.year}, month ${date.month}, day ${date.day}")
		add("  elected in year ${MayorService.electedYear}, next election in year ${MayorService.electedYear + 1}")
		add("  perks: ${mayor?.perks?.joinToString()?.ifEmpty { "none" } ?: "unknown"}")
		add("  minister: ${MayorService.minister ?: "none"}, perk=${MayorService.ministerPerk ?: "none"}")
		add("  perkpocalypse perk: ${MayorService.perkpocalypsePerk ?: "unknown"}")
	}

	private fun toggleMayor(): String {
		val held = forcedMayor
		forcedMayor = if (held == null) MayorService.require() else null.also { held.unsubscribe() }
		return if (held == null) "Mayor: asked for, fetching in the background."
		else "Mayor: no longer asked for by this toggle."
	}

	fun profileLines(): List<String> = buildList {
		add("Profiles: ${if (PlayerProfiles.available) "available" else "unavailable"}, " +
			"needed by ${PlayerProfiles.required}, proxy=${PlayerProfiles.proxyHost ?: "not set"}")
		add("  caches: ${PlayerProfiles.cacheSummary()}")
		add("  in flight=${PlayerProfiles.inFlight} of ${PlayerProfiles.maxInFlight}, " +
			"peak=${PlayerProfiles.peakInFlight}, failedRequests=${PlayerProfiles.failures}")
	}

	fun profileLookup(name: String, notify: (String) -> Unit) = PlayerProfiles.lookup(name, notify)

	fun profileProxyShown(): String {
		val saved = ClientPrefs.profileProxy.value
		if (saved.isEmpty()) return "No profile proxy address is saved. Type one after 'url' to set it."
		return "The profile proxy address is $saved. Type 'url clear' to forget it."
	}

	fun profileProxyCleared(): String {
		ClientPrefs.profileProxy.value = ""
		PlayerProfiles.clearCaches()
		return "Cleared the profile proxy address."
	}

	fun profileProxy(address: String, save: () -> Unit): String {
		val wanted = address.trim()
		val limit = ClientPrefs.profileProxy.maxLength
		if (wanted.length > limit) return "A proxy address can be at most $limit characters, so that one was not saved."
		val site = named(wanted) ?: return "A proxy address has to look like https://example.com, so '$wanted' was not saved."
		ClientPrefs.profileProxy.value = wanted
		save()
		PlayerProfiles.clearCaches()
		return "Profile proxy set to $site."
	}

	private fun named(address: String): String? {
		val parsed = runCatching { URI(address) }.getOrNull() ?: return null
		if (parsed.scheme?.lowercase(Locale.ROOT) !in SCHEMES) return null
		if (parsed.query != null || parsed.fragment != null) return null
		return parsed.host?.takeIf(String::isNotEmpty)
	}

	fun marketLines(query: String): List<String> = buildList {
		val marketId = query.trim().uppercase(Locale.ROOT)
		add("Price of '$marketId':")
		for (source in PriceSource.entries) add("  $source=${Prices.price(marketId, source) ?: "unknown"}")
		val product = Prices.product(marketId)
		if (product == null) {
			add("  not a bazaar product")
			return@buildList
		}
		add("  bazaar id=${product.productId}, instantBuy=${product.instantBuy}, instantSell=${product.instantSell}")
		val status = product.quickStatus
		add("  book=${product.buySummary.size} buy / ${product.sellSummary.size} sell")
		add("  buy: price=${status.buyPrice}, volume=${status.buyVolume}, orders=${status.buyOrders}, week=${status.buyMovingWeek}")
		add("  sell: price=${status.sellPrice}, volume=${status.sellVolume}, orders=${status.sellOrders}, week=${status.sellMovingWeek}")
		for (order in product.buySummary.take(TOP_ORDERS)) {
			add("  buy ${order.pricePerUnit} x${order.amount} in ${order.orders} order(s)")
		}
		for (order in product.sellSummary.take(TOP_ORDERS)) {
			add("  sell ${order.pricePerUnit} x${order.amount} in ${order.orders} order(s)")
		}
	}

	fun itemLines(query: String): List<String> = buildList {
		val item = ItemRepo.item(query) ?: ItemRepo.idFor(query)?.let(ItemRepo::item)
		if (item == null) {
			add("Item repo: nothing named '$query' (state=${ItemRepo.state}, items=${ItemRepo.size})")
			return@buildList
		}
		add("${item.id}: '${item.displayName}'")
		add("  vanilla=${item.itemId}, damage=${item.damage}, loreLines=${item.lore.size}")
		for (line in item.lore) add("  $line")
	}

	fun heldItemLines(): List<String> = buildList {
		val stack = heldItem()
		if (stack == null || stack.isEmpty) {
			add("Held item: nothing in your main hand")
			return@buildList
		}
		add("Held item: '${withoutCodes(stack.hoverName.string)}'")
		val item = SkyBlockItems.of(stack)
		if (item === SkyBlockItem.NONE) {
			add("  no SkyBlock data on this item")
			return@buildList
		}
		add("  id=${item.id.ifEmpty { "none" }}, marketId=${item.marketId.ifEmpty { "none" }}, uuid=${item.uuid.ifEmpty { "none" }}")
		val rarity = SkyBlockItems.rarity(stack)
		add("  rarity=$rarity (magicalPower=${rarity.magicalPower}), upgradeLevel=${item.upgradeLevel}, " +
			"rarityUpgrades=${item.rarityUpgrades}, reforge=${item.reforge.ifEmpty { "none" }}")
		add("  hotPotato=${item.hotPotatoCount}, artOfWar=${item.artOfWar}, tunedTransmission=${item.tunedTransmission}, " +
			"ethermerge=${item.ethermerge}, museum=${item.donatedMuseum}, timestamp=${item.timestamp}")
		add("  enchantments=${item.enchantments}, runes=${item.runes}, attributes=${item.attributes}, gems=${item.gems != null}")
		item.pet?.let {
			add("  pet: type=${it.type}, tier=${it.tier}, exp=${it.exp}, heldItem=${it.heldItem ?: "none"}, " +
				"candy=${it.candyUsed}, skin=${it.skin ?: "none"}")
		}
		add("  loreLines=${SkyBlockItems.lore(stack).size}, skull=${SkyBlockItems.skullTexture(stack) != null}, " +
			"glint=${SkyBlockItems.hasGlint(stack)}, recordsCached=${SkyBlockItems.cachedRecords}")
	}

	fun valueLines(): List<String> = buildList {
		val stack = heldItem()
		if (stack == null || stack.isEmpty) {
			add("Held item: nothing in your main hand")
			return@buildList
		}
		val name = withoutCodes(stack.hoverName.string)
		if (SkyBlockItems.of(stack) === SkyBlockItem.NONE) {
			add("Value of '$name': no SkyBlock data on this item")
			return@buildList
		}
		val valuation = ItemValue.of(stack, VALUE_SOURCE)
		add("Value of '$name': ${valuation.total} from $VALUE_SOURCE (base ${valuation.base})")
		for (line in valuation.breakdown) {
			add("  ${line.label}: ${if (line.priced) line.amount else "no price"}")
		}
	}

	fun lines(): List<String> = buildList {
		add(
			"Dhen debug: deep profiling ${if (deepMode) "on" else "off"}, " +
				"modules.json v${ModulePersistence.version}"
		)
		for (hook in Dhen.hooks) if (!hook.active()) add("${hook.feed}: no feed, off until restart")
		add(
			if (!TickHooks.serverFeedActive()) "Server tick: no feed, the server tick feed is off until restart"
			else "Server tick: tps=${String.format(Locale.ROOT, "%.1f", ServerClock.tps)}, " +
				"serverTicks=${ServerClock.ticks}, " +
				"clientTicksSincePing=${ServerClock.clientTicksSinceServerTick}"
		)
		if (HypixelLocationHooks.active()) {
			add(
				"Location: hypixel=${SkyBlockLocation.onHypixel}, skyblock=${SkyBlockLocation.inSkyBlock}, " +
					"island=${SkyBlockLocation.island}, area=${SkyBlockLocation.area ?: "none"}, " +
					"mode=${SkyBlockLocation.mode ?: "none"}, server=${SkyBlockLocation.serverName ?: "none"}, " +
					"islandChanges=${HypixelLocationHooks.islandChanges}, " +
					"areaChanges=${HypixelLocationHooks.areaChanges}, " +
					"guest=${SkyBlockLocation.isGuest}, " +
					"awaitingGuestTitle=${SkyBlockLocation.awaitingGuestTitle}"
			)
		}
		if (ScoreboardHooks.active()) {
			add(
				"Scoreboard: title='${ScoreboardState.strippedTitle}', lines=${ScoreboardState.lines.size}, " +
					"scoreboardArea=${ScoreboardState.area ?: "none"}"
			)
		}
		if (TablistHooks.active()) {
			add(
				"Tab list: lines=${TablistState.lines.size}, header=${TablistState.strippedHeader.isNotEmpty()}, " +
					"footer=${TablistState.strippedFooter.isNotEmpty()}"
			)
		}
		if (TabWidgetHooks.active()) {
			add("Tab list widgets: active=${TabWidget.entries.count(TabWidgetState::active)} of ${TabWidget.entries.size}")
		}
		if (PlayerStatsHooks.active()) {
			add(
				"Player stats: health=${PlayerStats.health}/${PlayerStats.maxHealth}, mana=${PlayerStats.mana}/${PlayerStats.maxMana}, " +
					"defense=${PlayerStats.defense}, speed=${PlayerStats.speed}"
			)
		}
		add("Item repo: state=${ItemRepo.state}, items=${ItemRepo.size}, needed by ${ItemRepo.required}")
		add("Prices: needed by ${Prices.required}, bazaar=${Prices.bazaar?.size ?: 0} products")
		for (module in manager.modules) {
			add(
				"${module.name}: subscriptions=${module.subscriptionCount}, " +
					"keybinds=${manager.keybindCount(module)}, hud=${module.hudElements.size}, " +
					"errors=${module.errorCount}"
			)
			for (timing in module.handlerTimings) {
				val snapshot = timing.snapshot()
				add(
					"  ${snapshot.eventName}: calls=${snapshot.totalInvocations}, " +
						"rollingAvg=${snapshot.averageNanos}ns, rollingMax=${snapshot.maxNanos}ns, " +
						"samples=${snapshot.sampleCount}"
				)
			}
		}
	}

	private companion object {
		private const val TOP_ORDERS = 3

		private val VALUE_SOURCE = PriceSource.BAZAAR_INSTANT_SELL
		private val SCHEMES = setOf("http", "https")
	}
}
