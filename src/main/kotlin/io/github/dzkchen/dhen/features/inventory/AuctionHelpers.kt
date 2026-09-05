package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.WikiLinks
import io.github.dzkchen.dhen.data.value.ItemValue
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.FINAL_WORD
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.digits
import io.github.dzkchen.dhen.util.grouped
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object AuctionHelpers : Module(
	name = "Auction Helpers",
	category = Category.ECONOMY,
	description = "Warns you when you are outbid, paints your own auctions by state, copies an underbidding price and compares a listing against what the item is worth."
) {
	private val highlightSetting = BooleanSetting(
		"Highlight Auctions",
		default = true,
		description = "Paints your own sold and expired auctions in the Manage Auctions menu."
	)

	private val soldColorSetting = ColorSetting(
		"Sold Color",
		Color.rgba(85, 255, 85),
		description = "The colour behind an auction that sold."
	).withDependency { highlightSetting.on }

	private val expiredColorSetting = ColorSetting(
		"Expired Color",
		Color.rgba(255, 85, 85),
		description = "The colour behind an auction that expired."
	).withDependency { highlightSetting.on }

	private val underbidSetting = BooleanSetting(
		"Highlight Underbid Auctions",
		description = "Paints your own auctions that are listed for more than the item is worth."
	)

	private val underbidColorSetting = ColorSetting(
		"Underbid Color",
		Color.rgba(255, 170, 0),
		description = "The colour behind an auction someone has undercut."
	).withDependency { underbidSetting.on }

	private val autoCopySetting = BooleanSetting(
		"Auto Copy Underbid",
		description = "Copies one coin under the market price to the clipboard when you open Create BIN Auction."
	)

	private val copyKeySetting = KeybindSetting(
		"Copy Underbid Keybind",
		description = "Copies one coin under the price of the auction your cursor is on.",
		screenPolicy = KeybindScreenPolicy.NON_TEXT_SCREEN
	).onPress { copyHovered() }

	private val websiteSetting = BooleanSetting(
		"Price Website",
		description = "Puts a price-history button in the top-right of an auction search."
	)

	private val websiteUrlSetting = StringSetting(
		"Price Website URL",
		description = "The address the price-history button opens, with your search added to the end. Blank turns the button off."
	).withDependency { websiteSetting.on }

	private val outbidSetting = BooleanSetting(
		"Outbid Alert",
		description = "Puts a warning on screen when someone outbids you."
	)

	private val comparisonSetting = BooleanSetting(
		"Show Price Comparison",
		description = "Paints browsed auctions by how far their price sits from what the item is worth. This is an estimate and can be well off."
	)

	private val goodColorSetting = ColorSetting(
		"Good Color",
		Color.rgba(85, 255, 85),
		description = "The colour behind a slightly cheap auction."
	).withDependency { comparisonSetting.on }

	private val veryGoodColorSetting = ColorSetting(
		"Very Good Color",
		Color.rgba(0, 139, 0),
		description = "The colour behind the cheapest auction on the page."
	).withDependency { comparisonSetting.on }

	private val badColorSetting = ColorSetting(
		"Bad Color",
		Color.rgba(255, 255, 85),
		description = "The colour behind a slightly overpriced auction."
	).withDependency { comparisonSetting.on }

	private val veryBadColorSetting = ColorSetting(
		"Very Bad Color",
		Color.rgba(225, 43, 30),
		description = "The colour behind the most overpriced auction on the page."
	).withDependency { comparisonSetting.on }

	private val priceHold = RequirementHold(Prices::active, Prices::require)

	private val tints = IntArray(MENU_SLOTS)
	private val diffs = LongArray(MENU_SLOTS) { NO_DIFF }
	private val websiteLines = ArrayList<Component>(WEBSITE_LINES)

	private val anyPrice = matcher("(?:Buy it now|Starting bid|Top bid): ([0-9,]+) coins")
	private val binPrice = matcher("Buy it now: ([0-9,]+) coins")
	private val searchTitle = matcher("Auctions: \"(.*)\"?")
	private val copyMenus = matcher("Auctions Browser|Manage Auctions|Auctions: \".*\"?")
	private val outbidLine = matcher("§6\\[Auction].*§eoutbid you by.*§e§lCLICK")

	private val websiteIcon by lazy { ItemStack(Items.PAPER) }

	private var searchTerm = ""
	private var lastOpened = 0L
	private var lastCopied = ""
	private var best = 0L
	private var worst = 0L

	init {
		registerSetting(highlightSetting)
		registerSetting(soldColorSetting)
		registerSetting(expiredColorSetting)
		registerSetting(underbidSetting)
		registerSetting(underbidColorSetting)
		registerSetting(autoCopySetting)
		registerSetting(copyKeySetting)
		registerSetting(websiteSetting)
		registerSetting(websiteUrlSetting)
		registerSetting(outbidSetting)
		registerSetting(comparisonSetting)
		registerSetting(goodColorSetting)
		registerSetting(veryGoodColorSetting)
		registerSetting(badColorSetting)
		registerSetting(veryBadColorSetting)

		on<ClientTickEvent.End> { priceHold.ensure() }
		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { forget() }
		on<WorldChangeEvent> { forget() }
		on<ChatReceiveEvent> { outbid(it) }
		on<SlotRenderEvent.Pre> { painted(it) }
		on<TooltipEvent>(FINAL_WORD) { annotated(it) }
		on<ScreenRenderEvent.Pre> { websiteTooltip(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onEnabled() = priceHold.ensure()

	override fun onDisabled() {
		priceHold.release()
		forget()
	}

	internal fun listedPrice(lore: List<Component>, binOnly: Boolean): Long? {
		val matcher = if (binOnly) binPrice.get() else anyPrice.get()
		for (index in lore.indices) {
			if (!matcher.reset(withoutCodes(lore[index].string)).matches()) continue
			val coins = digits(matcher.group(1))
			return if (coins < 0L) null else coins
		}
		return null
	}

	private fun forget() {
		tints.fill(0)
		diffs.fill(NO_DIFF)
		websiteLines.clear()
		searchTerm = ""
		lastCopied = ""
		best = 0L
		worst = 0L
	}

	private fun opened(rawTitle: String, stacks: List<ItemStack>) {
		if (!SkyBlockLocation.inSkyBlock) {
			forget()
			return
		}
		val title = withoutCodes(rawTitle)
		highlights(title, stacks)
		comparisons(title, stacks)
		website(title)
		autoCopy(title, stacks)
	}

	private fun highlights(title: String, stacks: List<ItemStack>) {
		tints.fill(0)
		if (title != MANAGE_AUCTIONS) return
		if (!highlightSetting.on && !underbidSetting.on) return
		for (index in stacks.indices) {
			if (index >= MENU_SLOTS) break
			val stack = stacks[index]
			if (stack.isEmpty) continue
			val lore = SkyBlockItems.rawLore(stack)
			if (highlightSetting.on) {
				if (hasLine(lore, SOLD_STATUS)) {
					tints[index] = soldColorSetting.value.argb
					continue
				}
				if (hasLine(lore, EXPIRED_STATUS)) {
					tints[index] = expiredColorSetting.value.argb
					continue
				}
			}
			if (!underbidSetting.on) continue
			val listed = listedPrice(lore, binOnly = true) ?: continue
			if (listed > ItemValue.of(stack, VALUE_SOURCE).total) tints[index] = underbidColorSetting.value.argb
		}
	}

	private fun comparisons(title: String, stacks: List<ItemStack>) {
		diffs.fill(NO_DIFF)
		best = 0L
		worst = 0L
		if (!comparisonSetting.on) return
		if (!title.startsWith(AUCTIONS_PREFIX) && !title.startsWith(COSMETICS_PREFIX)) return
		for (index in stacks.indices) {
			if (index >= MENU_SLOTS) break
			val stack = stacks[index]
			if (stack.isEmpty) continue
			val listed = listedPrice(SkyBlockItems.rawLore(stack), binOnly = false) ?: continue
			val valuation = ItemValue.of(stack, VALUE_SOURCE)
			if (!valuation.modified) continue
			val diff = valuation.total.toLong() - listed
			diffs[index] = diff
			if (diff >= 0L) {
				if (diff > best) best = diff
			} else if (diff < worst) {
				worst = diff
			}
		}
	}

	private fun website(title: String) {
		val matcher = searchTitle.get().reset(title)
		val term = if (websiteSetting.on && websiteUrlSetting.value.isNotBlank() && matcher.matches()) {
			matcher.group(1).removeSuffix(QUOTE)
		} else {
			""
		}
		if (term == searchTerm) return
		searchTerm = term
		websiteLines.clear()
	}

	private fun autoCopy(title: String, stacks: List<ItemStack>) {
		if (!autoCopySetting.on || title != CREATE_BIN_AUCTION) {
			lastCopied = ""
			return
		}
		val stack = stacks.getOrNull(CREATE_ITEM_SLOT) ?: return
		if (stack.isEmpty) {
			lastCopied = ""
			return
		}
		val marketId = ItemValue.marketId(SkyBlockItems.of(stack), PriceSource.LOWEST_BIN)
		if (marketId.isEmpty() || marketId == lastCopied) return
		lastCopied = marketId
		val unit = Prices.price(marketId, PriceSource.LOWEST_BIN)?.toLong() ?: return
		if (unit <= 0L) return
		copyUnderbid(unit * stack.count)
	}

	private fun copyHovered() {
		if (!SkyBlockLocation.inSkyBlock) return
		val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return
		if (!copyMenus.get().reset(withoutCodes(screen.title.string)).matches()) return
		val stack = (screen as ContainerOrigin).dhenHoveredSlot()?.item ?: return
		if (stack.isEmpty) return
		val listed = listedPrice(SkyBlockItems.rawLore(stack), binOnly = false) ?: return
		copyUnderbid(listed)
	}

	private fun copyUnderbid(price: Long) {
		val under = price - 1L
		Minecraft.getInstance().keyboardHandler.clipboard = under.toString()
		Dhen.announce("Copied ${grouped(under)} to the clipboard.")
	}

	private fun outbid(event: ChatReceiveEvent) {
		if (!outbidSetting.on || !SkyBlockLocation.inSkyBlock) return
		if (!outbidLine.get().reset(event.styled).find()) return
		DhenAlert.show(OUTBID_TITLE, sound = SoundEvents.EXPERIENCE_ORB_PICKUP)
	}

	private fun painted(event: SlotRenderEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		if (slot.container is Inventory || slot.index < 0 || slot.index >= MENU_SLOTS) return
		if (searchTerm.isNotEmpty() && slot.index == WEBSITE_SLOT) {
			ItemGui.stack(event.graphics, websiteIcon, slot.x, slot.y)
			event.cancelled = true
			return
		}
		val tint = tints[slot.index]
		if (tint != 0) {
			SlotTint.claim(tint, TINT_PRIORITY)
			return
		}
		val diff = diffs[slot.index]
		if (diff != NO_DIFF) SlotTint.claim(comparisonTint(diff), COMPARISON_PRIORITY)
	}

	private fun annotated(event: TooltipEvent) {
		val slot = event.hoveredSlot
		if (slot.container is Inventory || slot.index < 0 || slot.index >= MENU_SLOTS) return
		if (searchTerm.isNotEmpty() && slot.index == WEBSITE_SLOT) {
			event.cancelled = true
			return
		}
		val diff = diffs[slot.index]
		if (diff == NO_DIFF) return
		val lines = event.edit()
		lines.add(Component.empty())
		if (diff >= 0L) {
			lines.add(DhenType.component("§aThis item is §6${grouped(diff)} coins §acheaper"))
			lines.add(DhenType.component("§athan the estimated item value!"))
		} else {
			lines.add(DhenType.component("§cThis item is §6${grouped(-diff)} coins §cmore"))
			lines.add(DhenType.component("§cexpensive than the estimated item value!"))
		}
	}

	private fun websiteTooltip(event: ScreenRenderEvent.Pre) {
		if (searchTerm.isEmpty()) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if ((screen as ContainerOrigin).dhenHoveredSlot()?.index != WEBSITE_SLOT) return
		if (websiteLines.isEmpty()) rebuildWebsiteLines()
		event.graphics.setComponentTooltipForNextFrame(
			Minecraft.getInstance().font,
			websiteLines,
			event.mouseX,
			event.mouseY
		)
	}

	private fun rebuildWebsiteLines() {
		websiteLines.clear()
		websiteLines.add(DhenType.component("§bPrice History"))
		websiteLines.add(Component.empty())
		websiteLines.add(DhenType.component("§7Click to open the price history"))
		websiteLines.add(DhenType.component("§7of §e$searchTerm"))
	}

	private fun clicked(event: ContainerClickEvent) {
		if (searchTerm.isEmpty()) return
		val slot = event.hoveredSlot ?: return
		if (slot.container is Inventory || slot.index != WEBSITE_SLOT) return
		event.cancelled = true
		val now = System.currentTimeMillis()
		if (now - lastOpened < OPEN_GAP_MS) return
		lastOpened = now
		val term = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8).replace(PLUS, ENCODED_SPACE)
		val url = websiteUrlSetting.value.trim() + term
		launch { WikiLinks.open(url) }
	}

	private fun comparisonTint(diff: Long): Int {
		if (diff == 0L) return goodColorSetting.value.argb
		val cheap = diff > 0L
		val span = if (cheap) best else worst
		val fraction = if (span == 0L) 0f else (diff.toDouble() / span).toFloat()
		return DhenPalette.mix(
			if (cheap) goodColorSetting.value.argb else badColorSetting.value.argb,
			if (cheap) veryGoodColorSetting.value.argb else veryBadColorSetting.value.argb,
			fraction.coerceIn(0f, 1f)
		)
	}

	private fun hasLine(lore: List<Component>, text: String): Boolean {
		for (index in lore.indices) if (withoutCodes(lore[index].string) == text) return true
		return false
	}

	private fun matcher(pattern: String) = ThreadLocal.withInitial { Pattern.compile(pattern).matcher("") }

	private val VALUE_SOURCE = PriceSource.BAZAAR_INSTANT_SELL

	private const val MANAGE_AUCTIONS = "Manage Auctions"
	private const val AUCTIONS_PREFIX = "Auctions"
	private const val COSMETICS_PREFIX = "Cosmetics Browser"
	private const val CREATE_BIN_AUCTION = "Create BIN Auction"
	private const val SOLD_STATUS = "Status: Sold!"
	private const val EXPIRED_STATUS = "Status: Expired!"
	private const val OUTBID_TITLE = "§cYou have been outbid!"
	private const val QUOTE = "\""
	private const val PLUS = "+"
	private const val ENCODED_SPACE = "%20"
	private const val CREATE_ITEM_SLOT = 13
	private const val WEBSITE_SLOT = 8
	private const val WEBSITE_LINES = 4
	private const val MENU_SLOTS = 54
	private const val NO_DIFF = Long.MIN_VALUE
	private const val OPEN_GAP_MS = 300L
	private const val TINT_PRIORITY = 20
	private const val COMPARISON_PRIORITY = 10
}
