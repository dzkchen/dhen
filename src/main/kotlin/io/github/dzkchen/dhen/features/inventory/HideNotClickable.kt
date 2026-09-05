package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.NotClickableFilters
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.FINAL_WORD
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.slotOutline
import io.github.dzkchen.dhen.input.platformModifierHeld
import io.github.dzkchen.dhen.input.platformModifierName
import io.github.dzkchen.dhen.input.shiftHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Locale

object HideNotClickable : Module(
	name = "Hide Not Clickable",
	category = Category.INVENTORY,
	description = "Greys out and blocks the items the open SkyBlock menu will not take, and says why in the tooltip."
) {
	private val protectRarelySoldSetting = BooleanSetting(
		"Protect Rarely Sold Items",
		description = "Also greys out items an NPC shop will take but you probably did not mean to sell."
	)

	private val blockClicksSetting = BooleanSetting(
		"Block Clicks",
		default = true,
		description = "Refuses the click as well as greying the item out."
	)

	private val transparencySetting = NumberSetting(
		"Transparency",
		default = 180.0,
		min = 0.0,
		max = 255.0,
		step = 5.0,
		description = "How solid the grey shade over a blocked item is."
	)

	private val bypassKeySetting = BooleanSetting(
		"Bypass With Key",
		default = true,
		description = "Lets you hold ${platformModifierName()} to click a blocked item anyway."
	)

	private val greenLineSetting = BooleanSetting(
		"Green Line",
		default = true,
		description = "Draws a green outline around the items this menu will take."
	)

	private val configureKeySetting = MenuKeybinds.menuKey(
		"Configure Blocked Slots",
		NO_MENU_BIND,
		"Press this while pointing at a menu button to hide it, or at a hidden one to bring it back."
	)

	private val items = SkyBlockItems.memo(ContainerState.INVENTORY_SLOTS)
	private val seen = arrayOfNulls<ItemStack>(ContainerState.INVENTORY_SLOTS)
	private val reasons = arrayOfNulls<String>(ContainerState.INVENTORY_SLOTS)
	private val clickable = BooleanArray(ContainerState.INVENTORY_SLOTS)
	private val explained = arrayOfNulls<List<Component>>(ContainerState.INVENTORY_SLOTS)
	private val priceHold = RequirementHold(Prices::active, Prices::require)

	private var title = ""
	private var blockedKey = ""
	private var blocksAny = false
	private var pricedAt = 0
	private var hintedBypass = true
	private var inNpcShop = false
	private var inBazaar = false
	private var inExcavator = false
	private var reason = ""
	private var greenLine = false

	init {
		registerSetting(protectRarelySoldSetting)
		registerSetting(blockClicksSetting)
		registerSetting(transparencySetting)
		registerSetting(bypassKeySetting)
		registerSetting(greenLineSetting)
		registerSetting(configureKeySetting)
		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { closed() }
		on<ContainerClickEvent>(AFTER_PRODUCERS) { clicked(it) }
		on<ContainerKeyEvent>(AFTER_PRODUCERS) { pressed(it) }
		on<SlotRenderEvent.Pre> { hidden(it) }
		on<SlotRenderEvent.Post> { shaded(it) }
		on<TooltipEvent>(FINAL_WORD) { explain(it) }
	}

	override fun onEnabled() {
		priceHold.ensure()
	}

	override fun onDisabled() {
		priceHold.release()
		closed()
	}

	internal fun opened(rawTitle: String, stacks: List<ItemStack>) {
		priceHold.ensure()
		title = withoutCodes(rawTitle)
		rememberBlocked(title)
		inNpcShop = npcShopOpen(stacks)
		inBazaar = bazaarMenuOpen(title, stacks)
		inExcavator = title == FOSSIL_EXCAVATOR && stacks.any { ItemFacts.cleanName(it) == START_EXCAVATOR }
		forgetDecisions()
	}

	private fun closed() {
		title = ""
		rememberBlocked("")
		inNpcShop = false
		inBazaar = false
		inExcavator = false
		forgetDecisions()
	}

	private fun forgetDecisions() {
		seen.fill(null)
		explained.fill(null)
		pricedAt = Prices.revision
	}

	private fun rememberBlocked(menu: String) {
		blockedKey = menu.lowercase(Locale.ROOT)
		blocksAny = menu.isNotEmpty() && ContainerState.blocksAnySlot(blockedKey)
	}

	private fun userBlocks(slot: Slot): Boolean {
		if (!blocksAny || !configureKeySetting.isBound || slot.container is Inventory) return false
		return ContainerState.blocksSlot(blockedKey, slot.index)
	}

	private fun blockedByUser(slot: Slot): Boolean = userBlocks(slot) && !shiftHeld()

	private fun configures(slot: Slot?, code: Int, mouse: Boolean): Boolean {
		if (!MenuKeybinds.bound(configureKeySetting, code, mouse)) return false
		if (title.isEmpty() || slot == null || slot.container is Inventory) return false
		if (MenuKeybinds.heldDown(code, mouse, Util.getMillis())) return true
		ContainerState.toggleBlockedSlot(title, slot.index)
		rememberBlocked(title)
		return true
	}

	private fun hidden(event: SlotRenderEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		if (blockedByUser(event.slot)) event.cancelled = true
	}

	private fun shaded(event: SlotRenderEvent.Post) {
		if (!ready(event.screen) || bypassing()) return
		val slot = event.slot
		if (slot.container !is Inventory) return
		if (hides(slot)) {
			val shade = DhenPalette.withAlpha(DhenPalette.SLOT_SHADE, transparencySetting.amount.toInt())
			SharpGui.fill(event.graphics, slot.x, slot.y, slot.x + SLOT_BOX, slot.y + SLOT_BOX, shade)
			return
		}
		if (greenLineSetting.on && clickable[slot.containerSlot]) {
			slotOutline(event.graphics, slot.x, slot.y, DhenPalette.withAlpha(DhenPalette.SLOT_GREEN, GREEN_ALPHA))
		}
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		if (configures(event.hoveredSlot, event.click.button(), mouse = true)) {
			event.cancelled = true
			return
		}
		val slot = event.hoveredSlot ?: return
		if (userBlocks(slot)) {
			event.cancelled = true
			if (shiftHeld()) {
				clickSlot(event.screen.menu, slot.index, event.click.button(), ContainerInput.PICKUP)
			}
			return
		}
		if (blocks(event.screen, slot)) event.cancelled = true
	}

	private fun pressed(event: ContainerKeyEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		if (configures(event.hoveredSlot, event.input.key(), mouse = false)) {
			event.cancelled = true
			return
		}
		val slot = event.hoveredSlot ?: return
		if (!movesSlot(event.input)) return
		if (blocks(event.screen, slot)) event.cancelled = true
	}

	private fun blocks(screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
		if (blockedByUser(slot)) return true
		if (!blockClicksSetting.on || !ready(screen) || bypassing()) return false
		if (slot.container !is Inventory) return false
		return hides(slot)
	}

	private fun movesSlot(key: KeyEvent): Boolean {
		val options = Minecraft.getInstance().options
		if (options.keyDrop.matches(key)) return true
		val hotbar = options.keyHotbarSlots
		for (index in hotbar.indices) if (hotbar[index].matches(key)) return true
		return options.keySwapOffhand.matches(key)
	}

	private fun explain(event: TooltipEvent) {
		if (!ready(event.screen) || bypassing()) return
		if (hintedBypass != bypassKeySetting.on) {
			hintedBypass = bypassKeySetting.on
			explained.fill(null)
		}
		val slot = event.hoveredSlot
		if (slot.container !is Inventory) return
		if (!hides(slot)) return
		val index = slot.containerSlot
		val written = explained[index] ?: notice(index, event.lines[0].string).also { explained[index] = it }
		val lines = event.edit()
		lines.clear()
		lines.addAll(written)
	}

	private fun notice(index: Int, name: String): List<Component> {
		val why = reasons[index].orEmpty()
		val written = ArrayList<Component>(NOTICE_LINES)
		written.add(Component.literal("$MUTED$name"))
		written.add(Component.empty())
		written.add(Component.literal("$WARNING$why"))
		if (bypassKeySetting.on && !why.contains(SKYBLOCK_MENU_REASON)) {
			written.add(Component.literal("  $MUTED(Hold ${platformModifierName()} to click it anyway.)"))
		}
		return written
	}

	private fun ready(screen: AbstractContainerScreen<*>): Boolean =
		SkyBlockLocation.inSkyBlock && screen is ContainerScreen && title.isNotEmpty()

	private fun bypassing(): Boolean = bypassKeySetting.on && platformModifierHeld()

	private fun hides(slot: Slot): Boolean {
		val index = slot.containerSlot
		if (index !in 0 until ContainerState.INVENTORY_SLOTS) return false
		if (pricedAt != Prices.revision) forgetDecisions()
		val stack = slot.item
		if (stack.isEmpty) {
			seen[index] = null
			reasons[index] = null
			clickable[index] = false
			return false
		}
		if (seen[index] === stack && reasons[index] != null) return reasons[index]!!.isNotEmpty()
		val why = reasonFor(items.of(index, stack), stack)
		seen[index] = stack
		reasons[index] = why
		clickable[index] = greenLine && why.isEmpty()
		explained[index] = null
		return why.isNotEmpty()
	}

	internal fun reasonFor(item: SkyBlockItem, stack: ItemStack): String {
		reason = ""
		greenLine = false
		if (!hide(item, stack)) return ""
		return reason.ifEmpty { UNKNOWN_REASON }
	}

	internal fun marksClickable(): Boolean = greenLine

	private fun hide(item: SkyBlockItem, stack: ItemStack): Boolean = when {
		hideNpcSell(item, stack) -> true
		hideInStorage(stack) -> true
		hideSalvage(item, stack) -> true
		hidePlayerTrade(stack) -> true
		hideBazaarOrAuction(item, stack) -> true
		hideAccessoryBag(stack) -> true
		hideBasketOfSeeds(item, stack) -> true
		hideNetherWartPouch(item, stack) -> true
		hideTrickOrTreatBag(stack) -> true
		hideSackOfSacks(stack) -> true
		hideFishingBag(stack) -> true
		hidePotionBag(stack) -> true
		hidePrivateIslandChest(stack) -> true
		hideAttributeFusion(item) -> true
		hideYourEquipment(stack) -> true
		hideRiftTransferChest(stack) -> true
		hideFossilExcavator(item, stack) -> true
		hideResearchCenter(item) -> true
		hideBirdFeeder(item) -> true
		else -> false
	}

	private fun hideNpcSell(item: SkyBlockItem, stack: ItemStack): Boolean {
		if (SkyBlockLocation.island == Island.THE_RIFT || !inNpcShop) return false
		greenLine = true
		val name = unstacked(stack)
		val sellable = ItemFacts.codedLoreHas(stack, CLICK_TO_SELL) ||
			(item.id != PET && (Prices.price(item.id, PriceSource.NPC_SELL) ?: 0.0) > 0.0)
		if (!sellable) return blocked("This item cannot be sold at the NPC!")
		if (item.donatedMuseum) return blocked("This item cannot be sold at the NPC! (Donated to Museum)")
		if (item.isRecombobulated) return blocked("This item should not be sold at the NPC! (Recombobulated)")
		if (!protectRarelySoldSetting.on) return false
		if (ItemFacts.isVanilla(stack) && !stack.isEnchanted) return false
		if (SidebarValues.noTradeProfile() && Prices.product(item.marketId) != null) return false
		if (NotClickableFilters.npcSellAllowed.matches(name)) return false
		return blocked("This item should not be sold at the NPC!")
	}

	private fun hideInStorage(stack: ItemStack): Boolean {
		if (!title.contains(ENDER_CHEST) && !title.contains(BACKPACK) && title != STORAGE) return false
		if (ItemFacts.isSkyBlockMenu(stack)) return blocked("The SkyBlock Menu cannot be put into the storage!")
		if (!NotClickableFilters.storageBlocked.matches(ItemFacts.cleanName(stack))) return false
		return blocked("Bags cannot be put into the storage!")
	}

	private fun hideSalvage(item: SkyBlockItem, stack: ItemStack): Boolean {
		if (title != SALVAGE_ITEM && title != SALVAGE_ITEMS) return false
		greenLine = true
		if (item.isRecombobulated) return blocked("This item should not be salvaged! (Recombobulated)")
		if (ItemFacts.loreContains(stack, LEGENDARY_DUNGEON)) {
			return blocked("This item should not be salvaged! (Legendary)")
		}
		if (item.donatedMuseum) return blocked("This item cannot be salvaged! (Donated to Museum)")
		if (ItemFacts.isSkyBlockMenu(stack)) return blocked("The SkyBlock Menu cannot be salvaged!")
		val name = ItemFacts.cleanName(stack)
		for (salvageable in NotClickableFilters.salvageable) if (name.endsWith(salvageable)) return false
		return blocked("This item cannot be salvaged!")
	}

	private fun hidePlayerTrade(stack: ItemStack): Boolean {
		if (!title.startsWith(PLAYER_TRADE)) return false
		val soulbound =
			if (SidebarValues.noTradeProfile()) ItemFacts.isSoulbound(stack) else ItemFacts.isAnySoulbound(stack)
		if (soulbound) return blocked("Soulbound items cannot be traded!")
		if (ItemFacts.isSkyBlockMenu(stack)) return blocked("The SkyBlock Menu cannot be traded!")
		if (ItemFacts.isSack(stack)) return blocked("Sacks cannot be traded!")
		if (!NotClickableFilters.tradeBlocked.matches(ItemFacts.cleanName(stack))) return false
		return blocked("This item cannot be traded!")
	}

	private fun hideBazaarOrAuction(item: SkyBlockItem, stack: ItemStack): Boolean {
		val auction = title == COOP_AUCTION_HOUSE || title == AUCTION_HOUSE ||
			title == CREATE_BIN_AUCTION || title == CREATE_AUCTION
		if (!inBazaar && !auction) return false
		greenLine = true
		if (ItemFacts.isSkyBlockMenu(stack)) {
			return blocked(
				if (inBazaar) "The SkyBlock Menu is not a Bazaar Product!"
				else "The SkyBlock Menu cannot be auctioned!"
			)
		}
		if (inBazaar != (Prices.product(item.marketId) != null)) {
			return blocked(
				if (inBazaar) "This item is not a Bazaar Product!" else "Bazaar Products cannot be auctioned!"
			)
		}
		if (ItemFacts.isAnySoulbound(stack)) return blocked("Soulbound items cannot be auctioned!")
		if (ItemFacts.isSack(stack)) return blocked("Sacks cannot be auctioned!")
		if (!NotClickableFilters.auctionBlocked.matches(ItemFacts.cleanName(stack))) return false
		return blocked("This item cannot be auctioned!")
	}

	private fun hideAccessoryBag(stack: ItemStack): Boolean {
		if (!title.startsWith(ACCESSORY_BAG)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) return false
		greenLine = true
		if (ItemFacts.loreContains(stack, ACCESSORY) || ItemFacts.loreContains(stack, HATCESSORY)) return false
		return blocked("This item is not an accessory!")
	}

	private fun hideBasketOfSeeds(item: SkyBlockItem, stack: ItemStack): Boolean {
		if (!title.startsWith(BASKET_OF_SEEDS)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) {
			return blocked("The SkyBlock Menu cannot be put into the basket of seeds!")
		}
		if (item.id in SEEDS) return false
		return blocked("This item is not a seed!")
	}

	private fun hideNetherWartPouch(item: SkyBlockItem, stack: ItemStack): Boolean {
		if (!title.startsWith(NETHER_WART_POUCH)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) {
			return blocked("The SkyBlock Menu cannot be put into the nether wart pouch!")
		}
		if (item.id == NETHER_WART) return false
		return blocked("This item is not a nether wart!")
	}

	private fun hideTrickOrTreatBag(stack: ItemStack): Boolean {
		if (!title.startsWith(TRICK_OR_TREAT_BAG)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) {
			return blocked("The SkyBlock Menu cannot be put into the trick or treat bag!")
		}
		if (ItemFacts.cleanName(stack) in SPOOKY_CANDY) return false
		return blocked("This item is not a spooky candy!")
	}

	private fun hideSackOfSacks(stack: ItemStack): Boolean {
		if (!title.startsWith(SACK_OF_SACKS)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) return false
		greenLine = true
		if (ItemFacts.isSack(stack)) return false
		return blocked("This item is not a sack!")
	}

	private fun hideFishingBag(stack: ItemStack): Boolean {
		if (!title.startsWith(FISHING_BAG)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) return blocked("The SkyBlock Menu cannot be put into the fishing bag!")
		greenLine = true
		if (ItemFacts.hasLoreLine(stack, FISHING_BAIT)) return false
		return blocked("This item is not a fishing bait!")
	}

	private fun hidePotionBag(stack: ItemStack): Boolean {
		if (!title.startsWith(POTION_BAG)) return false
		if (ItemFacts.isSkyBlockMenu(stack)) return blocked("The SkyBlock Menu cannot be put into the potion bag!")
		greenLine = true
		val name = ItemFacts.cleanName(stack)
		if (name.endsWith(POTION_SUFFIX) || name == WATER_BOTTLE) return false
		return blocked("This item is not a potion!")
	}

	private fun hidePrivateIslandChest(stack: ItemStack): Boolean {
		if (title != CHEST && title != LARGE_CHEST) return false
		if (!SkyBlockLocation.island.privateIsland) return false
		if (!ItemFacts.isSoulbound(stack)) return false
		return blocked("This item cannot be stored into a chest!")
	}

	private fun hideAttributeFusion(item: SkyBlockItem): Boolean {
		if (!title.startsWith(ATTRIBUTE_FUSION)) return false
		greenLine = true
		if (item.attributes.isNotEmpty()) return false
		return blocked("This item has no attributes!")
	}

	private fun hideYourEquipment(stack: ItemStack): Boolean {
		if (title != EQUIPMENT_MENU) return false
		if (ItemFacts.category(stack) in ItemFacts.WEARABLE && ItemFacts.codedLoreHas(stack, BOLD_CODE)) {
			greenLine = true
			return false
		}
		if (ItemFacts.isSkyBlockMenu(stack)) {
			return blocked("The SkyBlock Menu cannot be put into your equipment!")
		}
		return blocked("This item cannot be put into your equipment!")
	}

	private fun hideRiftTransferChest(stack: ItemStack): Boolean {
		if (title != RIFT_TRANSFER_CHEST) return false
		greenLine = true
		if (ItemFacts.isRiftTransferable(stack) || ItemFacts.isRiftExportable(stack)) return false
		return blocked("Not Rift-Transferable or Rift-Exportable!")
	}

	private fun hideFossilExcavator(item: SkyBlockItem, stack: ItemStack): Boolean {
		if (!inExcavator) return false
		greenLine = true
		if (item.id.isEmpty()) return true
		if (item.id == SUSPICIOUS_SCRAP || ItemFacts.category(stack) == CHISEL) return false
		return blocked("Not a chisel or scrap!")
	}

	private fun hideResearchCenter(item: SkyBlockItem): Boolean {
		if (title != RESEARCH_CENTER) return false
		greenLine = true
		if (item.id.isEmpty()) return false
		if (item.id == HELIX || item.id.endsWith(FOSSIL_SUFFIX)) return false
		return blocked("Not a fossil!")
	}

	private fun hideBirdFeeder(item: SkyBlockItem): Boolean {
		if (title != BIRDFEEDER) return false
		greenLine = true
		if (item.id.isEmpty() || item.id in BIRD_FOOD) return false
		return blocked("Not bird food!")
	}

	private fun blocked(why: String): Boolean {
		reason = why
		return true
	}

	private fun unstacked(stack: ItemStack): String {
		val name = ItemFacts.cleanName(stack)
		val tail = " x${stack.count}"
		return if (name.endsWith(tail)) name.dropLast(tail.length) else name
	}

	private val SEEDS = setOf(
		"SEEDS", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN_SEEDS", "SUGAR_CANE", "MELON_SEEDS", "CACTUS",
		"INK_SACK-3", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE"
	)

	private val SPOOKY_CANDY = setOf("Green Candy", "Purple Candy", "Dark Candy")

	private val BIRD_FOOD = setOf("BAG_OF_SEEDS", "WRIGGLEWORM", "YOGI_BERRY")

	private const val GREEN_ALPHA = 200
	private const val NOTICE_LINES = 4
	private const val MUTED = "§7"
	private const val WARNING = "§c"
	private const val UNKNOWN_REASON = "This menu will not take that item."
	private const val SKYBLOCK_MENU_REASON = "SkyBlock Menu"
	private const val PET = "PET"
	private const val NETHER_WART = "NETHER_STALK"
	private const val HELIX = "HELIX"
	private const val FOSSIL_SUFFIX = "_FOSSIL"
	private const val CLICK_TO_SELL = "§eClick to sell!"
	private const val LEGENDARY_DUNGEON = "LEGENDARY DUNGEON"
	private const val FISHING_BAIT = "Fishing Bait"
	private const val ACCESSORY = "ACCESSORY"
	private const val HATCESSORY = "HATCESSORY"
	private const val BOLD_CODE = "§l"
	private const val POTION_SUFFIX = " Potion"
	private const val WATER_BOTTLE = "Water Bottle"
	private const val ENDER_CHEST = "Ender Chest"
	private const val BACKPACK = "Backpack"
	private const val STORAGE = "Storage"
	private const val SALVAGE_ITEM = "Salvage Item"
	private const val SALVAGE_ITEMS = "Salvage Items"
	private const val PLAYER_TRADE = "You    "
	private const val COOP_AUCTION_HOUSE = "Co-op Auction House"
	private const val AUCTION_HOUSE = "Auction House"
	private const val CREATE_BIN_AUCTION = "Create BIN Auction"
	private const val CREATE_AUCTION = "Create Auction"
	private const val ACCESSORY_BAG = "Accessory Bag"
	private const val BASKET_OF_SEEDS = "Basket of Seeds"
	private const val NETHER_WART_POUCH = "Nether Wart Pouch"
	private const val TRICK_OR_TREAT_BAG = "Trick or Treat Bag"
	private const val SACK_OF_SACKS = "Sack of Sacks"
	private const val FISHING_BAG = "Fishing Bag"
	private const val POTION_BAG = "Potion Bag"
	private const val CHEST = "Chest"
	private const val LARGE_CHEST = "Large Chest"
	private const val FOSSIL_EXCAVATOR = "Fossil Excavator"
	private const val START_EXCAVATOR = "Start Excavator"
	private const val SUSPICIOUS_SCRAP = "SUSPICIOUS_SCRAP"
	private const val CHISEL = "CHISEL"
	private const val ATTRIBUTE_FUSION = "Attribute Fusion"
	private const val EQUIPMENT_MENU = "Stats & Equipment"
	private const val RIFT_TRANSFER_CHEST = "Rift Transfer Chest"
	private const val RESEARCH_CENTER = "Research Center"
	private const val BIRDFEEDER = "Birdfeeder"
}
