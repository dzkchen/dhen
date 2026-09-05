package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.block.StainedGlassPaneBlock
import java.util.Arrays

object BetterContainers : Module(
	name = "Better Containers",
	category = Category.INVENTORY,
	description = "Reskins SkyBlock menus: the black filler panes disappear and the rest take Dhen chrome."
) {
	private val menuStyleSetting = SelectorSetting(
		"Menu Background Style",
		STYLE_NAMES[0],
		STYLE_NAMES.toList(),
		description = "The look of the menu panel behind the slots."
	)

	private val buttonStyleSetting = SelectorSetting(
		"Button Background Style",
		STYLE_NAMES[0],
		STYLE_NAMES.toList(),
		description = "The look of the plates the items and buttons sit on."
	)

	private val kinds = ByteArray(MENU_CELLS)
	private val plates = IntArray(MAX_PLATES * PLATE_FIELDS)
	private val claimed = BooleanArray(MENU_CELLS)

	private val rowTops = IntArray(PLAYER_ROWS)
	private val rowLefts = IntArray(PLAYER_ROWS)
	private val rowRights = IntArray(PLAYER_ROWS)

	private var rowCount = 0
	private var cells = 0
	private var plateCount = 0
	private var restyling = false

	init {
		registerSetting(menuStyleSetting)
		registerSetting(buttonStyleSetting)

		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { forget() }
		on<SlotRenderEvent.Pre> { chromed(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onDisabled() = forget()

	@JvmStatic
	fun overriding(): Boolean =
		enabled && restyling && SkyBlockLocation.inSkyBlock &&
			Minecraft.getInstance().gui.screen() is ContainerScreen

	@JvmStatic
	fun highlights(slot: Slot, original: Boolean): Boolean {
		if (!overriding() || slot.container is Inventory) return original
		return kindOf(slot.index) != HIDDEN
	}

	@JvmStatic
	fun labelInk(original: Int): Int =
		if (!overriding()) original else DhenPalette.containerInk(styleIndex(menuStyleSetting))

	@JvmStatic
	fun paintPanel(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
		if (!overriding()) return
		val menu = styleIndex(menuStyleSetting)
		val panel = screen as ContainerOrigin
		RoundedGui.frame(
			graphics,
			0,
			0,
			panel.dhenContainerWidth(),
			panel.dhenContainerHeight(),
			PANEL_RADIUS,
			DhenPalette.containerPanel(menu),
			DhenPalette.containerPanelBorder(menu)
		)
		val button = styleIndex(buttonStyleSetting)
		val slots = screen.menu.slots
		var index = 0
		while (index < plateCount) {
			val base = index * PLATE_FIELDS
			val first = slots.getOrNull(plates[base]) ?: break
			val raised = plates[base + 1] == BUTTON.toInt()
			plate(
				graphics,
				first.x,
				first.y,
				first.x + plates[base + 2] * CELL_PITCH - CELL_GAP,
				first.y + plates[base + 3] * CELL_PITCH - CELL_GAP,
				if (raised) DhenPalette.containerButton(button) else DhenPalette.containerTile(button),
				if (raised) DhenPalette.containerButtonBorder(button) else DhenPalette.containerTileBorder(button)
			)
			index++
		}
		paintPlayerRows(graphics, slots, DhenPalette.containerTile(button), DhenPalette.containerTileBorder(button))
	}

	private fun paintPlayerRows(graphics: GuiGraphicsExtractor, slots: List<Slot>, fill: Int, border: Int) {
		rowCount = 0
		for (slot in slots) {
			if (slot.container !is Inventory) continue
			remember(slot.y, slot.x)
		}
		if (rowCount == 0) return
		var start = 0
		while (start < rowCount) {
			var end = start
			while (end + 1 < rowCount && rowTops[end + 1] - rowTops[end] == CELL_PITCH) end++
			var left = rowLefts[start]
			var right = rowRights[start]
			for (row in start..end) {
				left = minOf(left, rowLefts[row])
				right = maxOf(right, rowRights[row])
			}
			plate(graphics, left, rowTops[start], right + SLOT_BOX, rowTops[end] + SLOT_BOX, fill, border)
			start = end + 1
		}
	}

	private fun remember(top: Int, left: Int) {
		for (row in 0 until rowCount) {
			if (rowTops[row] != top) continue
			rowLefts[row] = minOf(rowLefts[row], left)
			rowRights[row] = maxOf(rowRights[row], left)
			return
		}
		if (rowCount >= rowTops.size) return
		var at = rowCount
		while (at > 0 && rowTops[at - 1] > top) {
			rowTops[at] = rowTops[at - 1]
			rowLefts[at] = rowLefts[at - 1]
			rowRights[at] = rowRights[at - 1]
			at--
		}
		rowTops[at] = top
		rowLefts[at] = left
		rowRights[at] = left
		rowCount++
	}

	private fun plate(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		fill: Int,
		border: Int
	) {
		RoundedGui.frame(
			graphics,
			left - TILE_BLEED,
			top - TILE_BLEED,
			right + TILE_BLEED,
			bottom + TILE_BLEED,
			TILE_RADIUS,
			fill,
			border
		)
	}

	private fun chromed(event: SlotRenderEvent.Pre) {
		if (!overriding()) return
		val slot = event.slot
		if (slot.container is Inventory) return
		when (kindOf(slot.index)) {
			HIDDEN -> event.cancelled = true
			TOGGLE_ON -> toggle(event, DhenPalette.SLOT_GREEN)
			TOGGLE_OFF -> toggle(event, DhenPalette.SLOT_RED)
		}
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!overriding()) return
		val slot = event.hoveredSlot ?: return
		if (slot.container is Inventory) return
		if (kindOf(slot.index) == HIDDEN && !slot.item.isEmpty) event.cancelled = true
	}

	private fun toggle(event: SlotRenderEvent.Pre, ink: Int) {
		val slot = event.slot
		RoundedGui.pill(
			event.graphics,
			slot.x + TOGGLE_INSET,
			slot.y + TOGGLE_LIFT,
			slot.x + SLOT_BOX - TOGGLE_INSET,
			slot.y + SLOT_BOX - TOGGLE_LIFT,
			ink
		)
		event.cancelled = true
	}

	private fun kindOf(index: Int): Byte = if (index < 0 || index >= cells) PLAIN else kinds[index]

	private fun opened(rawTitle: String, stacks: List<ItemStack>) {
		val menuCells = (stacks.size / ROW_WIDTH) * ROW_WIDTH
		if (menuCells <= 0 || menuCells > MENU_CELLS || blocked(withoutCodes(rawTitle))) {
			forget()
			return
		}
		cells = menuCells
		classify(withoutCodes(rawTitle), stacks)
		plateCount = if (restyling) mergePlates(kinds, cells, claimed, plates) else 0
	}

	private fun classify(title: String, stacks: List<ItemStack>) {
		val sequencer = title.startsWith(ULTRASEQUENCER) && !title.contains(STAKES)
		val pairs = title.startsWith(SUPERPAIRS) && !title.contains(STAKES)
		var anyItem = false
		var anyFiller = false
		for (index in 0 until cells) {
			val stack = stacks[index]
			val filler = isFiller(stack)
			val carvedOut = (sequencer && isDye(stack)) || (pairs && index > ROW_WIDTH && index < cells - ROW_WIDTH)
			kinds[index] = when {
				filler -> HIDDEN
				isToggle(stack, DISABLE) -> TOGGLE_ON
				isToggle(stack, ENABLE) -> TOGGLE_OFF
				!carvedOut && isButton(stack) -> BUTTON
				else -> PLAIN
			}
			if (filler) anyFiller = true
			if (!stack.isEmpty) anyItem = true
		}
		restyling = anyItem && anyFiller
	}

	private fun blocked(title: String): Boolean = title.startsWith(NAVIGATE_THE_MAZE, ignoreCase = true)

	private fun isFiller(stack: ItemStack): Boolean =
		paneColor(stack) == DyeColor.BLACK &&
			stack.count == 1 &&
			stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()

	private fun isButton(stack: ItemStack): Boolean =
		paneColor(stack) == null && SkyBlockItems.of(stack).id.isNotEmpty()

	private fun paneColor(stack: ItemStack): DyeColor? =
		((stack.item as? BlockItem)?.block as? StainedGlassPaneBlock)?.color

	private fun isDye(stack: ItemStack): Boolean = stack.item is DyeItem

	private fun isToggle(stack: ItemStack, verb: String): Boolean {
		if (!isDye(stack)) return false
		val lore = SkyBlockItems.rawLore(stack)
		if (lore.isEmpty()) return false
		return withoutCodes(lore[lore.size - 1].string).endsWith("Click to $verb!")
	}

	private fun styleIndex(setting: SelectorSetting): Int =
		setting.index.coerceIn(0, DhenPalette.CONTAINER_STYLES - 1)

	private fun forget() {
		restyling = false
		plateCount = 0
		cells = 0
	}

	internal const val HIDDEN: Byte = 0
	internal const val PLAIN: Byte = 1
	internal const val BUTTON: Byte = 2
	private const val TOGGLE_ON: Byte = 3
	private const val TOGGLE_OFF: Byte = 4

	private const val PLAYER_ROWS = 8
	private const val CELL_PITCH = 18
	private const val CELL_GAP = CELL_PITCH - SLOT_BOX
	private const val TILE_BLEED = 1
	private const val TOGGLE_INSET = 2
	private const val TOGGLE_LIFT = 5
	private const val PANEL_RADIUS = 5f
	private const val TILE_RADIUS = 3f
	private const val ULTRASEQUENCER = "Ultrasequencer"
	private const val SUPERPAIRS = "Superpairs"
	private const val STAKES = "Stakes"
	private const val NAVIGATE_THE_MAZE = "Navigate the maze"
	private const val ENABLE = "enable"
	private const val DISABLE = "disable"
}

private val STYLE_NAMES = arrayOf("Dark 1", "Dark 2", "Transparent", "Light 1", "Light 2", "Light 3")

internal const val ROW_WIDTH = StorageSnapshots.ROW_WIDTH
internal const val MENU_CELLS = 54
internal const val MAX_PLATES = MENU_CELLS
internal const val PLATE_FIELDS = 4

internal fun mergePlates(kinds: ByteArray, cells: Int, claimed: BooleanArray, plates: IntArray): Int {
	var count = 0
	Arrays.fill(claimed, 0, cells, false)
	val rows = cells / ROW_WIDTH
	for (index in 0 until cells) {
		val kind = kinds[index]
		if (claimed[index] || (kind != BetterContainers.PLAIN && kind != BetterContainers.BUTTON)) continue
		val column = index % ROW_WIDTH
		val row = index / ROW_WIDTH
		var width = 0
		while (column + width < ROW_WIDTH && !claimed[index + width] && kinds[index + width] == kind) width++
		var height = 1
		while (row + height < rows && spans(kinds, claimed, index + height * ROW_WIDTH, width, kind)) height++
		for (down in 0 until height) {
			for (across in 0 until width) claimed[index + down * ROW_WIDTH + across] = true
		}
		if (count >= MAX_PLATES) continue
		val base = count * PLATE_FIELDS
		plates[base] = index
		plates[base + 1] = kind.toInt()
		plates[base + 2] = width
		plates[base + 3] = height
		count++
	}
	return count
}

private fun spans(kinds: ByteArray, claimed: BooleanArray, start: Int, width: Int, kind: Byte): Boolean {
	for (across in 0 until width) {
		if (claimed[start + across] || kinds[start + across] != kind) return false
	}
	return true
}
