package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.regex.Pattern

internal class PetWheelCache {
	private val petSlots = IntArray(MAX_PETS)
	private val favouriteSlots = IntArray(MAX_PETS)
	private val titleMatcher = PETS_TITLE.matcher("")
	private var petCount = 0
	private var favouriteCount = 0
	private var favouritesOnly = false

	var windowId: Int = NO_WINDOW
		private set

	var page: Int = 0
		private set

	var vanilla: Boolean = false
		private set

	val active: Boolean
		get() = windowId != NO_WINDOW

	val custom: Boolean
		get() = active && !vanilla

	val size: Int
		get() = if (favouritesOnly) favouriteCount else petCount

	val pages: Int
		get() = Math.ceilDiv(size, PETS_PER_PAGE).coerceAtLeast(1)

	fun refresh(title: Component, windowId: Int, stacks: List<ItemStack>): Boolean {
		if (!titleMatcher.reset(withoutCodes(title.string)).matches()) return false
		if (this.windowId != windowId) {
			this.windowId = windowId
			page = 0
			vanilla = false
		}
		petCount = 0
		favouriteCount = 0
		var index = FIRST_PET_SLOT
		while (index <= LAST_PET_SLOT && index < stacks.size) {
			if (index % ROW_WIDTH in FIRST_PET_COLUMN..LAST_PET_COLUMN) retain(index, stacks[index])
			index++
		}
		clampPage()
		return true
	}

	fun showFavouritesOnly(show: Boolean) {
		if (favouritesOnly == show) return
		favouritesOnly = show
		clampPage()
	}

	fun movePage(offset: Int): Int {
		page = if (pages == 1) 0 else Math.floorMod(page + offset, pages)
		return page
	}

	fun slotAt(visibleIndex: Int): Int {
		if (visibleIndex !in 0 until PETS_PER_PAGE) return NO_SLOT
		val index = page * PETS_PER_PAGE + visibleIndex
		if (index >= size) return NO_SLOT
		return if (favouritesOnly) favouriteSlots[index] else petSlots[index]
	}

	fun showVanilla(): Boolean {
		if (!active) return false
		vanilla = true
		return true
	}

	fun close(windowId: Int): Boolean {
		if (this.windowId != windowId) return false
		reset()
		return true
	}

	fun reset() {
		windowId = NO_WINDOW
		page = 0
		vanilla = false
		petCount = 0
		favouriteCount = 0
	}

	private fun retain(index: Int, stack: ItemStack) {
		if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) return
		petSlots[petCount++] = index
		if (!withoutCodes(stack.hoverName.string).startsWith(FAVOURITE_PREFIX)) return
		favouriteSlots[favouriteCount++] = index
	}

	private fun clampPage() {
		page = page.coerceAtMost(pages - 1)
	}

	companion object {
		const val NO_SLOT = -1
		const val PETS_PER_PAGE = 9
		private const val NO_WINDOW = -1
		private const val FIRST_PET_SLOT = 10
		private const val LAST_PET_SLOT = 43
		private const val ROW_WIDTH = 9
		private const val FIRST_PET_COLUMN = 1
		private const val LAST_PET_COLUMN = 7
		private const val MAX_PETS = 28
		private const val FAVOURITE_PREFIX = "⭐ "
		private val PETS_TITLE = Pattern.compile(
			"^(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?$",
			Pattern.CASE_INSENSITIVE
		)
	}
}
