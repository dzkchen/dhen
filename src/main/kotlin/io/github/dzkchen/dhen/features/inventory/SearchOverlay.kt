package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.SearchName
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.mixin.AbstractSignEditScreenAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import java.util.Locale

internal enum class SearchPlace(val label: String) {
	AUCTION("Auction House"),
	BAZAAR("Bazaar"),
	MUSEUM("Museum")
}

object SearchOverlay : Module(
	name = "Search Overlay",
	category = Category.ECONOMY,
	description = "Replaces the Auction House, Bazaar and Museum search sign with a box that suggests item names."
) {
	internal val auctionSetting = BooleanSetting(
		"Auction House",
		default = true,
		description = "Opens the box on the Auction House search sign."
	)

	internal val bazaarSetting = BooleanSetting(
		"Bazaar",
		default = true,
		description = "Opens the box on the Bazaar search sign."
	)

	internal val museumSetting = BooleanSetting(
		"Museum",
		default = true,
		description = "Opens the box on the Museum search sign."
	)

	internal val suggestionsSetting = NumberSetting(
		"Suggestions",
		DEFAULT_SUGGESTIONS,
		1.0,
		MAX_SUGGESTIONS,
		description = "How many matching item names the box offers you."
	)

	internal val historySetting = NumberSetting(
		"Recent Searches",
		DEFAULT_HISTORY,
		0.0,
		MAX_HISTORY,
		description = "How many of your recent searches the box keeps below the suggestions."
	)

	internal val keepPreviousSetting = BooleanSetting(
		"Keep Previous Search",
		description = "Starts the box with whatever the sign already had typed on it."
	)

	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private val recent = HashMap<SearchPlace, ArrayDeque<String>>()

	private var origin: SearchPlace? = null

	init {
		for (
			setting in listOf(
				auctionSetting,
				bazaarSetting,
				museumSetting,
				suggestionsSetting,
				historySetting,
				keepPreviousSetting
			)
		) registerSetting(setting)

		on<GuiCloseEvent> { closed(it) }
		on<GuiOpenEvent> { opened(it) }
		on<ClientTickEvent.End> { repoHold.ensure() }
	}

	override fun onDisabled() {
		repoHold.release()
		origin = null
	}

	override fun onReset() = recent.clear()

	internal fun place(title: String): SearchPlace? = when {
		title.contains(AUCTION_TITLE) || title.contains(COSMETICS_TITLE) -> SearchPlace.AUCTION
		title.contains(BAZAAR_TITLE) -> SearchPlace.BAZAAR
		title.contains(MUSEUM_TITLE) -> SearchPlace.MUSEUM
		else -> null
	}

	internal fun wanted(place: SearchPlace): Boolean = when (place) {
		SearchPlace.AUCTION -> auctionSetting.on
		SearchPlace.BAZAAR -> bazaarSetting.on
		SearchPlace.MUSEUM -> museumSetting.on
	}

	internal fun isSearchSign(messages: Array<String>): Boolean =
		withoutCodes(messages[QUERY_LINE]).trim().equals(QUERY_HINT, ignoreCase = true)

	internal fun history(place: SearchPlace): List<String> =
		recent[place].orEmpty().take(historySetting.amount.toInt())

	internal fun remember(place: SearchPlace, search: String) {
		if (search.isBlank()) return
		val kept = recent.getOrPut(place) { ArrayDeque() }
		kept.remove(search)
		kept.addFirst(search)
		while (kept.size > historySetting.amount.toInt()) kept.removeLast()
	}

	internal fun forget(place: SearchPlace, search: String) {
		recent[place]?.remove(search)
	}

	internal fun suggestions(search: String, into: MutableList<SearchName>) {
		into.clear()
		val needle = search.trim().lowercase(Locale.ROOT)
		if (needle.isEmpty()) return
		val limit = suggestionsSetting.amount.toInt()
		for (name in ItemRepo.searchNames()) {
			if (!name.lowercase.startsWith(needle)) continue
			into.add(name)
			if (into.size >= limit) return
		}
		for (name in ItemRepo.searchNames()) {
			if (into.size >= limit) return
			if (name.lowercase.startsWith(needle) || !name.lowercase.contains(needle)) continue
			into.add(name)
		}
	}

	internal fun splitOverTwoLines(search: String): Pair<String, String> {
		if (search.length <= SIGN_LINE_LIMIT) return search to ""
		val space = search.lastIndexOf(' ', SIGN_LINE_LIMIT)
		if (space == -1) return search.substring(0, SIGN_LINE_LIMIT) to ""
		val tail = search.substring(space + 1, minOf(space + 1 + SIGN_LINE_LIMIT, search.length)).trim()
		return search.substring(0, space) to tail
	}

	internal fun quoted(search: String): String =
		if (search.lowercase(Locale.ROOT).startsWith(NULL_WORD)) "\"$search\"" else search

	private fun closed(event: GuiCloseEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		origin = place(withoutCodes(screen.title.string))
	}

	private fun opened(event: GuiOpenEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val screen = event.screen as? AbstractSignEditScreen ?: return
		val place = origin ?: return
		origin = null
		if (!wanted(place)) return
		val accessor = screen as AbstractSignEditScreenAccessor
		val messages = accessor.dhenMessages()
		if (!isSearchSign(messages)) return
		val sign = accessor.dhenSign() ?: return
		val typed = if (keepPreviousSetting.on) previous(messages) else ""
		event.screen = SearchOverlayScreen(sign.blockPos, accessor.dhenFrontText(), messages.copyOf(), place, typed)
	}

	private fun previous(messages: Array<String>): String {
		val first = withoutCodes(messages[0]).trim()
		val second = withoutCodes(messages[1]).trim()
		if (second.isEmpty()) return first
		return if (first.isEmpty()) second else "$first $second"
	}

	private const val QUERY_LINE = 3
	private const val QUERY_HINT = "Enter query"
	private const val AUCTION_TITLE = "Auction"
	private const val COSMETICS_TITLE = "Cosmetics"
	private const val BAZAAR_TITLE = "Bazaar"
	private const val MUSEUM_TITLE = "Museum"
	private const val NULL_WORD = "null"
	private const val SIGN_LINE_LIMIT = 15
	private const val DEFAULT_SUGGESTIONS = 5.0
	private const val MAX_SUGGESTIONS = 10.0
	private const val DEFAULT_HISTORY = 3.0
	private const val MAX_HISTORY = 10.0
}
