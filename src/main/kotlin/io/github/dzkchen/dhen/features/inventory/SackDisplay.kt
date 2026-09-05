package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.sack.GEMSTONE_QUALITIES
import io.github.dzkchen.dhen.data.sack.MAGMA_FISH
import io.github.dzkchen.dhen.data.sack.RUNE_LEVELS
import io.github.dzkchen.dhen.data.sack.SackMenu
import io.github.dzkchen.dhen.data.sack.SackRow
import io.github.dzkchen.dhen.data.sack.UNREPORTED
import io.github.dzkchen.dhen.data.sack.SackState
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.MenuHudEditor
import io.github.dzkchen.dhen.util.grouped
import io.github.dzkchen.dhen.util.shortNumber

internal const val SACK_SORTING = 0
internal const val SACK_NUMBER_FORMAT = 1
internal const val SACK_PRICE_SOURCE = 2
internal const val SACK_PRICE_FORMAT = 3

object SackDisplay : Module(
	name = "Sack Display",
	category = Category.INVENTORY,
	description = "Lists what an open sack holds and what it is worth."
) {
	private val numberFormatSetting = SelectorSetting(
		"Sack Number Format",
		FORMATTED,
		listOf(DEFAULT, FORMATTED, UNFORMATTED),
		description = "How the stored and capacity numbers read."
	)

	private val alignmentSetting = SelectorSetting(
		"Text Alignment",
		LEFT,
		listOf(LEFT, CENTER, RIGHT),
		description = "Which edge of the panel the rows line up against."
	)

	private val extraSpaceSetting = NumberSetting(
		"Extra Space",
		default = 1.0,
		min = 0.0,
		max = 10.0,
		description = "Extra pixels between the rows."
	)

	private val sortingSetting = SelectorSetting(
		"Sack Sorting",
		STORED_DESC,
		listOf(STORED_DESC, STORED_ASC, PRICE_DESC, PRICE_ASC),
		description = "What the rows are ordered by."
	)

	private val itemsToShowSetting = NumberSetting(
		"Sack Items To Show",
		default = 15.0,
		min = 0.0,
		max = 45.0,
		description = "How many rows the readout lists."
	)

	private val showEmptySetting = BooleanSetting(
		"Show Empty",
		default = true,
		description = "Keeps rows for items the sack holds none of."
	)

	private val showPriceSetting = BooleanSetting(
		"Show Price",
		default = true,
		description = "Puts each row's value at the end of its line."
	)

	private val priceFormatSetting = SelectorSetting(
		"Price Format",
		FORMATTED,
		listOf(FORMATTED, UNFORMATTED),
		description = "Whether prices read 1.2M or 1,200,000."
	).withDependency { showPriceSetting.on }

	private val priceSourceSetting = SelectorSetting(
		"Price Source",
		INSTANT_BUY,
		listOf(INSTANT_BUY, INSTANT_SELL, NPC_SELL),
		description = "Which market price each row is valued at."
	).withDependency { showPriceSetting.on }

	private val sackOfSacksSetting = BooleanSetting(
		"Sack of Sacks",
		default = true,
		description = "Also lists everything your sacks hold when you open the Sack of Sacks."
	)

	private val hideNoValueSetting = BooleanSetting(
		"Hide No-Value",
		default = true,
		description = "Drops rows the price service cannot price."
	)

	private val showTotalSetting = BooleanSetting(
		"Show Total",
		default = true,
		description = "Adds a line with everything in the sack added up."
	)

	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private val ordered = ArrayList<SackRow>()
	private val stored = ArrayList<SackRow>()

	internal val element = hud(SackDisplayElement())

	private var settledKey = 0

	init {
		registerSetting(numberFormatSetting)
		registerSetting(alignmentSetting)
		registerSetting(extraSpaceSetting)
		registerSetting(sortingSetting)
		registerSetting(itemsToShowSetting)
		registerSetting(showEmptySetting)
		registerSetting(showPriceSetting)
		registerSetting(priceFormatSetting)
		registerSetting(priceSourceSetting)
		registerSetting(sackOfSacksSetting)
		registerSetting(hideNoValueSetting)
		registerSetting(showTotalSetting)

		on<ClientTickEvent.End> { ticked() }
		on<ContainerReadyEvent> { opened() }
		on<ContainerUpdatedEvent> { opened() }
		on<ContainerClosedEvent> { element.clear() }
		on<ScreenRenderEvent.Post> { element.pointer(it.mouseX, it.mouseY) }
		on<SlotRenderEvent.Post> { highlighted(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onEnabled() {
		priceHold.ensure()
		repoHold.ensure()
	}

	override fun onDisabled() {
		priceHold.release()
		repoHold.release()
		element.clear()
	}

	private fun ticked() {
		priceHold.ensure()
		repoHold.ensure()
		if (settingsKey() != settledKey) opened()
	}

	private fun opened() {
		if (!SackState.inSack || !SkyBlockLocation.inSkyBlock) {
			element.clear()
			return
		}
		priceHold.ensure()
		settledKey = settingsKey()
		element.rebuild(if (SackMenu.isSackOfSacks(SackState.sackTitle)) storedRows() else SackState.rows)
	}

	private fun storedRows(): List<SackRow> {
		stored.clear()
		if (!sackOfSacksSetting.on) return stored
		for ((marketId, amount) in SackState.contents) {
			if (amount <= 0L) continue
			val stack = ItemRepo.stack(marketId) ?: continue
			stored += SackRow(
				label = withoutCodes(stack.hoverName.string),
				marketId = marketId,
				stack = stack,
				stored = amount,
				capacity = 0L,
				full = false,
				slot = NO_LINE,
				magmafish = 0L,
				parts = null,
				partIds = null
			)
		}
		return stored
	}

	private fun settingsKey(): Int {
		var key = sortingSetting.index
		key = key * PRIME + numberFormatSetting.index
		key = key * PRIME + priceSourceSetting.index
		key = key * PRIME + priceFormatSetting.index
		key = key * PRIME + itemsToShowSetting.amount.toInt()
		key = key * PRIME + extraSpaceSetting.amount.toInt()
		key = key * PRIME + alignmentSetting.index
		key = key * PRIME + (if (showEmptySetting.on) 1 else 0)
		key = key * PRIME + (if (showPriceSetting.on) 1 else 0)
		key = key * PRIME + (if (hideNoValueSetting.on) 1 else 0)
		key = key * PRIME + (if (sackOfSacksSetting.on) 1 else 0)
		return key * PRIME + (if (showTotalSetting.on) 1 else 0)
	}

	private fun clicked(event: ContainerClickEvent) {
		if (MenuHudEditor.editing || event.click.button() != LEFT_BUTTON) return
		when (element.hoveredAction()) {
			SACK_SORTING -> sortingSetting.index++
			SACK_NUMBER_FORMAT -> numberFormatSetting.index++
			SACK_PRICE_SOURCE -> priceSourceSetting.index++
			SACK_PRICE_FORMAT -> priceFormatSetting.index++
			else -> return
		}
		persist()
		event.cancelled = true
		opened()
	}

	private fun highlighted(event: SlotRenderEvent.Post) {
		val slot = event.slot
		if (slot.index != element.hoveredSlot()) return
		val shade = DhenPalette.withAlpha(DhenPalette.SLOT_GREEN, HIGHLIGHT_ALPHA)
		SharpGui.fill(event.graphics, slot.x, slot.y, slot.x + SLOT_BOX, slot.y + SLOT_BOX, shade)
	}

	internal fun priceOf(row: SackRow): Long {
		val source = priceSource()
		val parts = row.parts
		val ids = row.partIds
		if (row.magmafish > 0L) return (Prices.priceOr(MAGMA_FISH, source, 0.0) * row.magmafish).toLong()
		if (parts != null && ids != null) {
			var total = 0.0
			for (index in ids.indices) total += Prices.priceOr(ids[index], source, 0.0) * parts[index]
			return total.toLong()
		}
		return (Prices.priceOr(row.marketId, source, 0.0) * row.stored).toLong()
	}

	internal fun sorted(source: List<SackRow>): List<SackRow> {
		ordered.clear()
		for (row in source) {
			if (row.stored == 0L && !showEmptySetting.on) continue
			if (hideNoValueSetting.on && priceOf(row) == 0L) continue
			ordered += row
		}
		when (sortingSetting.value) {
			STORED_ASC -> ordered.sortBy { it.stored }
			PRICE_DESC -> ordered.sortByDescending { priceOf(it) }
			PRICE_ASC -> ordered.sortBy { priceOf(it) }
			else -> ordered.sortByDescending { it.stored }
		}
		return ordered
	}

	internal fun amountText(amount: Long): String = when (numberFormatSetting.value) {
		UNFORMATTED -> grouped(amount)
		DEFAULT -> grouped(amount)
		else -> shortNumber(amount)
	}

	internal fun capacityText(amount: Long): String =
		if (numberFormatSetting.value == UNFORMATTED) grouped(amount) else shortNumber(amount)

	internal fun priceText(amount: Long): String =
		if (priceFormatSetting.value == UNFORMATTED) grouped(amount) else shortNumber(amount)

	internal fun rowsShown(): Int = itemsToShowSetting.amount.toInt()

	internal fun extraSpace(): Int = extraSpaceSetting.amount.toInt()

	internal fun alignment(): Int = when (alignmentSetting.value) {
		CENTER -> ALIGN_CENTER
		RIGHT -> ALIGN_RIGHT
		else -> ALIGN_LEFT
	}

	internal fun showsPrice(): Boolean = showPriceSetting.on

	internal fun showsTotal(): Boolean = showTotalSetting.on

	internal fun sortingLabel(): String = sortingSetting.value

	internal fun numberFormatLabel(): String = numberFormatSetting.value

	internal fun priceSourceLabel(): String = priceSourceSetting.value

	internal fun priceFormatLabel(): String = priceFormatSetting.value

	internal fun priceSource(): PriceSource = when (priceSourceSetting.value) {
		INSTANT_SELL -> PriceSource.BAZAAR_INSTANT_SELL
		NPC_SELL -> PriceSource.NPC_SELL
		else -> PriceSource.BAZAAR_INSTANT_BUY
	}

	internal const val DEFAULT = "Default"
	internal const val FORMATTED = "Formatted"
	internal const val UNFORMATTED = "Unformatted"
	internal const val STORED_DESC = "Stored, most first"
	internal const val STORED_ASC = "Stored, least first"
	internal const val PRICE_DESC = "Price, most first"
	internal const val PRICE_ASC = "Price, least first"
	internal const val LEFT = "Left"
	internal const val CENTER = "Center"
	internal const val RIGHT = "Right"
	internal const val INSTANT_BUY = "Bazaar Instant Buy"
	internal const val INSTANT_SELL = "Bazaar Instant Sell"
	internal const val NPC_SELL = "NPC Sell"

	private const val LEFT_BUTTON = 0
	private const val PRIME = 31
	private const val HIGHLIGHT_ALPHA = 90
}

internal class SackDisplayElement : MenuListElement("Sack Display", HudAnchor.TOP_RIGHT, -MARGIN, MARGIN) {
	fun clear() = clearLines()

	override fun alignment(): Int = SackDisplay.alignment()

	override fun rowHeight(font: net.minecraft.client.gui.Font): Int =
		super.rowHeight(font) + SackDisplay.extraSpace()

	fun rebuild(source: List<SackRow>) {
		clearLines()
		if (source.isEmpty()) return
		val sorted = SackDisplay.sorted(source)
		val room = SackDisplay.rowsShown()
		var total = 0L
		var drawn = 0
		var magmafish = 0L
		line().text = "$HEADER (${minOf(room, sorted.size)} of ${sorted.size})"
		for (row in sorted) {
			val price = SackDisplay.priceOf(row)
			total += price
			magmafish += row.magmafish
			if (drawn >= room) continue
			drawn++
			row(row, price)
		}
		if (magmafish > 0L) line().text = MAGMAFISH_LABEL + grouped(magmafish)
		button(SORTING_LABEL + SackDisplay.sortingLabel(), SACK_SORTING)
		button(NUMBER_LABEL + SackDisplay.numberFormatLabel(), SACK_NUMBER_FORMAT)
		if (SackDisplay.showsPrice()) {
			button(SOURCE_LABEL + SackDisplay.priceSourceLabel(), SACK_PRICE_SOURCE)
			button(PRICE_FORMAT_LABEL + SackDisplay.priceFormatLabel(), SACK_PRICE_FORMAT)
		}
		if (SackDisplay.showsTotal()) line().text = TOTAL_LABEL + SackDisplay.priceText(total)
	}

	private fun row(row: SackRow, price: Long) {
		val line = line()
		line.icon = row.stack
		line.slot = row.slot
		val counts = countText(row)
		val tail = if (SackDisplay.showsPrice() && price > 0L) "  ${SackDisplay.priceText(price)}" else ""
		line.text = "${row.label}  $counts$tail"
	}

	private fun countText(row: SackRow): String {
		val parts = row.parts ?: return if (row.capacity > 0L) {
			"${SackDisplay.amountText(row.stored)}/${SackDisplay.capacityText(row.capacity)}"
		} else {
			SackDisplay.amountText(row.stored)
		}
		val labels = if (row.partIds == null) RUNE_LEVELS else GEMSTONE_QUALITIES
		val text = StringBuilder()
		for (index in parts.indices) {
			if (parts[index] == UNREPORTED) continue
			if (text.isNotEmpty()) text.append(' ')
			text.append(labels[index].lowercase().replaceFirstChar(Char::uppercaseChar))
				.append(' ')
				.append(SackDisplay.amountText(parts[index]))
		}
		return text.toString()
	}

	private fun button(text: String, action: Int) {
		val line = line()
		line.text = text
		line.action = action
	}

	private companion object {
		const val HEADER = "Items in sacks"
		const val MAGMAFISH_LABEL = "Total magmafish: "
		const val SORTING_LABEL = "Sorted by: "
		const val NUMBER_LABEL = "Numbers: "
		const val SOURCE_LABEL = "Price source: "
		const val PRICE_FORMAT_LABEL = "Price format: "
		const val TOTAL_LABEL = "Total price: "
	}
}

private const val MARGIN = 8
