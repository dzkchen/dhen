package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.util.matcher
import net.minecraft.world.item.ItemStack

internal fun npcShopOpen(stacks: List<ItemStack>): Boolean {
	val probe = stacks.getOrNull(stacks.indexOfLast { !it.isEmpty } - SELL_SLOT_BACK) ?: return false
	val lore = SkyBlockItems.rawLore(probe)
	if (lore.isEmpty()) return false
	return SHOP_LORE.get().reset(legacyCodes(lore[lore.size - 1])).matches()
}

internal fun bazaarMenuOpen(title: String, stacks: List<ItemStack>): Boolean {
	if (backToBazaar(stacks, stacks.size - GO_BACK_NEAR) || backToBazaar(stacks, stacks.size - GO_BACK_FAR)) return true
	if (customAmount(stacks.getOrNull(BUY_AMOUNT_SLOT))) return true
	return BAZAAR_ORDERS.get().reset(title).matches() || BAZAAR_MENU.get().reset(title).matches()
}

private fun backToBazaar(stacks: List<ItemStack>, index: Int): Boolean {
	val stack = stacks.getOrNull(index) ?: return false
	if (ItemFacts.cleanName(stack) != GO_BACK) return false
	return firstLore(stack) == TO_BAZAAR
}

private fun customAmount(stack: ItemStack?): Boolean {
	if (stack == null || stack.isEmpty) return false
	if (stack.hoverName.string != CUSTOM_AMOUNT) return false
	return firstLore(stack) == BUY_ORDER_QUANTITY
}

private fun firstLore(stack: ItemStack): String {
	val lore = SkyBlockItems.rawLore(stack)
	return if (lore.isEmpty()) "" else legacyCodes(lore[0])
}

private const val SELL_SLOT_BACK = 4
private const val GO_BACK_NEAR = 5
private const val GO_BACK_FAR = 6
private const val BUY_AMOUNT_SLOT = 16
private const val GO_BACK = "Go Back"
private const val TO_BAZAAR = "§7To Bazaar"
private const val CUSTOM_AMOUNT = "Custom Amount"
private const val BUY_ORDER_QUANTITY = "§8Buy Order Quantity"

private val SHOP_LORE = matcher("§7them to this Shop!|§eClick to buyback!")

private val BAZAAR_ORDERS = matcher("Your Bazaar Orders|Co-op Bazaar Orders")

private val BAZAAR_MENU = matcher(
	"Bazaar ➜ .*|How many do you want\\?|How much do you want to pay\\?|Confirm Buy Order|" +
		"Confirm Instant Buy|At what price are you selling\\?|Confirm Sell Offer|Order options"
)
