package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.value.ItemValue
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.MenuHudEditor
import io.github.dzkchen.dhen.util.grouped
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

internal const val VALUE_SORTING = 0
internal const val VALUE_FORMAT = 1
internal const val VALUE_LAYOUT = 2

object ChestValue : Module(
	name = "Chest Value",
	category = Category.INVENTORY,
	description = "Totals what the open container is worth and lists the items that carry the value."
) {
	private val ownInventorySetting = BooleanSetting(
		"Own Inventory",
		description = "Also totals your own inventory when you open it."
	)

	private val dungeonsSetting = BooleanSetting(
		"Dungeons",
		description = "Keeps the readout up inside a dungeon."
	)

	private val duringItemValueSetting = BooleanSetting(
		"During Item Value",
		description = "Keeps the readout up while the value breakdown panel is open."
	)

	private val showStacksSetting = BooleanSetting(
		"Show Stacks",
		default = true,
		description = "Draws each item's icon next to its row."
	)

	private val alignedSetting = BooleanSetting(
		"Aligned Display",
		default = true,
		description = "Pads the names so the prices line up in a column."
	)

	private val nameLengthSetting = NumberSetting(
		"Name Length",
		default = 100.0,
		min = 100.0,
		max = 150.0,
		description = "How many pixels of room a name gets before it is cut."
	)

	private val highlightSetting = BooleanSetting(
		"Highlight Slot",
		default = true,
		description = "Lights up the slot the row you point at came from."
	)

	private val sortingSetting = SelectorSetting(
		"Chest Sorting",
		DESCENDING,
		listOf(DESCENDING, ASCENDING),
		description = "Whether the most or the least valuable item comes first."
	)

	private val numberFormatSetting = SelectorSetting(
		"Chest Number Format",
		SHORT,
		listOf(SHORT, LONG),
		description = "Whether prices read 1.2M or 1,200,000."
	)

	private val itemsToShowSetting = NumberSetting(
		"Chest Items To Show",
		default = 15.0,
		min = 0.0,
		max = 54.0,
		description = "How many rows the readout lists. Hidden rows still count towards the total."
	)

	private val hideBelowSetting = NumberSetting(
		"Hide Below",
		default = 100_000.0,
		min = 50_000.0,
		max = 10_000_000.0,
		step = 50_000.0,
		description = "Rows worth less than this are not listed. They still count towards the total."
	)

	private val ignoreSoulboundSetting = BooleanSetting(
		"Ignore Soulbound",
		description = "Leaves soulbound items out of the total."
	)

	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val entries = LinkedHashMap<String, ChestEntry>()
	private val ordered = ArrayList<ChestEntry>()
	private val seen = arrayOfNulls<ItemStack>(MENU_SLOTS)
	private val seenCounts = IntArray(MENU_SLOTS)

	internal val element = hud(ChestValueElement())

	private var ticks = 0
	private var host: AbstractContainerScreen<*>? = null
	private var pricedAt = 0
	private var settledKey = 0

	init {
		registerSetting(ownInventorySetting)
		registerSetting(dungeonsSetting)
		registerSetting(duringItemValueSetting)
		registerSetting(showStacksSetting)
		registerSetting(alignedSetting)
		registerSetting(nameLengthSetting)
		registerSetting(highlightSetting)
		registerSetting(sortingSetting)
		registerSetting(numberFormatSetting)
		registerSetting(itemsToShowSetting)
		registerSetting(hideBelowSetting)
		registerSetting(ignoreSoulboundSetting)

		on<ClientTickEvent.End> { ticked() }
		on<ScreenRenderEvent.Post> { element.pointer(it.mouseX, it.mouseY) }
		on<SlotRenderEvent.Post> { highlighted(it) }
		on<ContainerClickEvent> { clicked(it) }
		on<GuiCloseEvent> { element.clear() }
	}

	override fun onEnabled() {
		priceHold.ensure()
	}

	override fun onDisabled() {
		priceHold.release()
		element.clear()
		host = null
	}

	private fun ticked() {
		priceHold.ensure()
		if (++ticks < REFRESH_TICKS) return
		ticks = 0
		rebuild()
	}

	private fun clicked(event: ContainerClickEvent) {
		if (MenuHudEditor.editing || event.click.button() != LEFT_BUTTON) return
		when (element.hoveredAction()) {
			VALUE_SORTING -> sortingSetting.index++
			VALUE_FORMAT -> numberFormatSetting.index++
			VALUE_LAYOUT -> alignedSetting.on = !alignedSetting.on
			else -> return
		}
		persist()
		event.cancelled = true
		refresh()
	}

	private fun highlighted(event: SlotRenderEvent.Post) {
		if (!highlightSetting.on) return
		val slot = event.slot
		if (slot.index != element.hoveredSlot()) return
		val shade = DhenPalette.withAlpha(DhenPalette.SLOT_GREEN, HIGHLIGHT_ALPHA)
		SharpGui.fill(event.graphics, slot.x, slot.y, slot.x + SLOT_BOX, slot.y + SLOT_BOX, shade)
	}

	private fun rebuild() {
		val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*>
		if (screen == null || !showing(screen)) {
			element.clear()
			host = null
			return
		}
		if (!settled(screen)) return
		val ownInventory = screen is InventoryScreen
		val title = withoutCodes(screen.title.string)
		val minion = title.contains(MINION_TITLE)
		entries.clear()
		for (slot in screen.menu.slots) {
			val playerSide = slot.container is Inventory
			if (ownInventory != playerSide) continue
			if (!ownInventory && minion && slot.index % MINION_ROW == MINION_FUEL) continue
			val stack = slot.item
			if (stack.isEmpty) continue
			collect(slot.index, stack)
		}
		element.rebuild(entries.values, ownInventory)
	}

	private fun settled(screen: AbstractContainerScreen<*>): Boolean {
		val key = settingsKey()
		val fresh = screen !== host || pricedAt != Prices.revision || key != settledKey
		if (fresh) {
			host = screen
			pricedAt = Prices.revision
			settledKey = key
			seen.fill(null)
		}
		var moved = false
		val slots = screen.menu.slots
		for (index in slots.indices) {
			if (index >= MENU_SLOTS) break
			val stack = slots[index].item
			if (seen[index] === stack && seenCounts[index] == stack.count) continue
			seen[index] = stack
			seenCounts[index] = stack.count
			moved = true
		}
		return fresh || moved
	}

	private fun settingsKey(): Int {
		var key = sortingSetting.index
		key = key * PRIME + numberFormatSetting.index
		key = key * PRIME + itemsToShowSetting.amount.toInt()
		key = key * PRIME + hideBelowSetting.amount.toInt()
		key = key * PRIME + nameLengthSetting.amount.toInt()
		key = key * PRIME + (if (alignedSetting.on) 1 else 0)
		key = key * PRIME + (if (showStacksSetting.on) 1 else 0)
		return key * PRIME + (if (ignoreSoulboundSetting.on) 1 else 0)
	}

	internal fun refresh() {
		host = null
		rebuild()
	}

	private fun collect(index: Int, stack: ItemStack) {
		val valuation = ItemValue.of(stack, VALUE_SOURCE)
		if (valuation.total <= 0.0) return
		val key = withoutCodes(stack.hoverName.string)
		val entry = entries.getOrPut(key) { ChestEntry(key, stack) }
		entry.count += stack.count
		entry.total += valuation.total * stack.count
		entry.soulbound = entry.soulbound || ItemFacts.isAnySoulbound(stack)
		if (entry.slot == NO_LINE) entry.slot = index
	}

	private fun showing(screen: AbstractContainerScreen<*>): Boolean {
		if (!SkyBlockLocation.inSkyBlock) return false
		if (ValueBreakdown.showing && !duringItemValueSetting.on) return false
		if (SkyBlockLocation.island == Island.CATACOMBS && !dungeonsSetting.on) return false
		if (screen is InventoryScreen) return ownInventorySetting.on
		if (screen !is ContainerScreen) return false
		val title = withoutCodes(screen.title.string)
		if (title.contains(MINION_TITLE)) return !title.contains(RECIPE_TITLE) && SkyBlockLocation.island == Island.PRIVATE_ISLAND
		return title == CHEST || title == LARGE_CHEST || title in NAMED_CONTAINERS ||
			title.startsWith(ENDER_CHEST) || (title.contains(BACKPACK) && title.contains(SLOT_NUMBER))
	}

	internal fun sortedEntries(source: Collection<ChestEntry>): List<ChestEntry> {
		ordered.clear()
		ordered.addAll(source)
		if (sortingSetting.value == ASCENDING) ordered.sortBy { it.total } else ordered.sortByDescending { it.total }
		return ordered
	}

	internal fun formatted(amount: Double): String =
		if (numberFormatSetting.value == LONG) grouped(amount.toLong()) else shortNumber(amount.toLong())

	internal fun rowsShown(): Int = itemsToShowSetting.amount.toInt()

	internal fun hideBelow(): Double = hideBelowSetting.amount

	internal fun ignoresSoulbound(): Boolean = ignoreSoulboundSetting.on

	internal fun showsStacks(): Boolean = showStacksSetting.on

	internal fun aligned(): Boolean = alignedSetting.on

	internal fun nameRoom(): Int = nameLengthSetting.amount.toInt()

	internal fun sortingLabel(): String = sortingSetting.value

	internal fun formatLabel(): String = numberFormatSetting.value

	internal fun layoutLabel(): String = if (alignedSetting.on) ALIGNED else PLAIN

	private val VALUE_SOURCE = PriceSource.BAZAAR_INSTANT_SELL

	private val NAMED_CONTAINERS = setOf("Personal Vault", "Chest Storage", "Wood Chest+")

	private const val DESCENDING = "Descending"
	private const val ASCENDING = "Ascending"
	private const val SHORT = "Short"
	private const val LONG = "Long"
	private const val ALIGNED = "Aligned"
	private const val PLAIN = "Normal"
	private const val CHEST = "Chest"
	private const val LARGE_CHEST = "Large Chest"
	private const val ENDER_CHEST = "Ender Chest ("
	private const val BACKPACK = "Backpack"
	private const val SLOT_NUMBER = "Slot #"
	private const val MINION_TITLE = " Minion "
	private const val RECIPE_TITLE = "Recipe"
	private const val MINION_ROW = 9
	private const val MINION_FUEL = 1
	private const val LEFT_BUTTON = 0
	private const val PRIME = 31
	private const val MENU_SLOTS = 128
	private const val REFRESH_TICKS = 5
	private const val HIGHLIGHT_ALPHA = 90
}

internal class ChestEntry(val label: String, val stack: ItemStack) {
	var count = 0
	var total = 0.0
	var soulbound = false
	var slot = NO_LINE
}

internal class ChestValueElement : MenuListElement("Chest Value", HudAnchor.TOP_RIGHT, -MARGIN, MARGIN) {
	private val nameMemo = DhenType.memo()

	fun rebuild(source: Collection<ChestEntry>, ownInventory: Boolean) {
		clearLines()
		if (source.isEmpty()) return
		val sorted = ChestValue.sortedEntries(source)
		val room = ChestValue.rowsShown()
		val floor = ChestValue.hideBelow()
		var total = 0.0
		var shown = 0
		for (entry in sorted) {
			if (ChestValue.ignoresSoulbound() && entry.soulbound) continue
			total += entry.total
			if (shown < room && entry.total >= floor) shown++
		}
		line().text = "${if (ownInventory) INVENTORY_TITLE else CHEST_TITLE}: ($shown of ${sorted.size})"
		var drawn = 0
		for (entry in sorted) {
			if (ChestValue.ignoresSoulbound() && entry.soulbound) continue
			if (drawn >= room || entry.total < floor) continue
			drawn++
			row(entry)
		}
		button(SORTING_LABEL + ChestValue.sortingLabel(), VALUE_SORTING)
		button(FORMAT_LABEL + ChestValue.formatLabel(), VALUE_FORMAT)
		button(LAYOUT_LABEL + ChestValue.layoutLabel(), VALUE_LAYOUT)
		line().text = TOTAL_LABEL + ChestValue.formatted(total)
	}

	private fun row(entry: ChestEntry) {
		val line = line()
		line.slot = entry.slot
		if (ChestValue.showsStacks()) line.icon = entry.stack
		val amount = " x${grouped(entry.count.toLong())}:"
		val price = ChestValue.formatted(entry.total)
		val name = if (ChestValue.aligned()) padded(entry.label, amount) else entry.label
		line.text = "$name$amount $price"
	}

	private fun padded(label: String, amount: String): String {
		val font = Minecraft.getInstance().font
		val room = (ChestValue.nameRoom() - DhenType.width(font, amount)).coerceAtLeast(0)
		val fitted = nameMemo.fit(font, label, room)
		val space = DhenType.width(font, " ").coerceAtLeast(1)
		val missing = (room - DhenType.width(font, fitted)) / space
		return if (missing <= 0) fitted else fitted + " ".repeat(missing)
	}

	override fun invalidateMeasurement() {
		super.invalidateMeasurement()
		nameMemo.invalidate()
	}

	private companion object {
		const val CHEST_TITLE = "Chest value"
		const val INVENTORY_TITLE = "Inventory value"
		const val SORTING_LABEL = "Sorting: "
		const val FORMAT_LABEL = "Format: "
		const val LAYOUT_LABEL = "Layout: "
		const val TOTAL_LABEL = "Total: "
	}
}

private const val MARGIN = 8
