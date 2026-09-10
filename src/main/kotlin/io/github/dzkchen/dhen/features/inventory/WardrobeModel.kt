package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal const val WARDROBE_SETS = 27
internal const val WARDROBE_PAGE_SIZE = 9

internal class WardrobeModel {
	val pieces = Array(WARDROBE_SETS) { Array(4) { ItemStack.EMPTY } }
	val locked = BooleanArray(WARDROBE_SETS) { true }
	val known = BooleanArray(WARDROBE_SETS)
	val favorites = BooleanArray(WARDROBE_SETS)
	var equipped = -1
	var page = 0
	var pages = 3
	var window = -1
	var waiting = false
	var waitingPage = -1

	fun empty(index: Int): Boolean {
		for (stack in pieces[index]) if (!stack.isEmpty) return false
		return true
	}

	fun visible(index: Int, hideLocked: Boolean, hideEmpty: Boolean, onlyFavorites: Boolean): Boolean =
		(!hideLocked || !locked[index]) && (!hideEmpty || !empty(index)) &&
			(!onlyFavorites || favorites[index] || equipped == index)

	fun read(page: Int, pages: Int, window: Int, stacks: List<ItemStack>): Boolean {
		if (page !in 0 until pages || page >= 3 || stacks.size < 45) return false
		this.page = page
		this.pages = pages.coerceIn(1, 3)
		this.window = window
		var gray = true
		var hasItems = false
		for (index in 0 until WARDROBE_PAGE_SIZE) {
			if (!stacks[36 + index].`is`(Items.DYE.gray())) gray = false
			for (part in 0 until 4) if (!vacant(stacks[part * WARDROBE_PAGE_SIZE + index])) hasItems = true
		}
		if (gray && hasItems) return false
		if (waiting && page == waitingPage && !gray) {
			waiting = false
			waitingPage = -1
		}
		if (equipped / WARDROBE_PAGE_SIZE == page) equipped = -1
		for (index in 0 until WARDROBE_PAGE_SIZE) {
			val target = page * WARDROBE_PAGE_SIZE + index
			val selector = stacks[36 + index]
			if (selector.`is`(Items.DYE.red())) {
				for (higher in target until WARDROBE_SETS) locked[higher] = true
			} else if (!gray) locked[target] = false
			if (!gray) known[target] = true
			if (withoutCodes(selector.hoverName.string).matches(EQUIPPED)) equipped = target
			for (part in 0 until 4) {
				val stack = stacks[part * WARDROBE_PAGE_SIZE + index]
				pieces[target][part] = if (gray || vacant(stack)) ItemStack.EMPTY else stack.copy()
			}
		}
		return true
	}

	fun leave() {
		window = -1
		waiting = false
		waitingPage = -1
	}

	private fun vacant(stack: ItemStack): Boolean = stack.isEmpty || stack.item in PANES

	private companion object {
		val EQUIPPED = Regex("Slot \\d+: Equipped")
		val PANES = net.minecraft.world.item.DyeColor.entries.map { Items.STAINED_GLASS_PANE.pick(it) }.toSet()
	}
}
