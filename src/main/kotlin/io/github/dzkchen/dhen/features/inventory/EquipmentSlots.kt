package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.mixin.SlotAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

object EquipmentSlots : Module(
	name = "Equipment Slots",
	category = Category.INVENTORY,
	description = "Draws your necklace, cloak, belt and gloves beside the armour in your inventory."
) {
	private val sets = HashMap<String, Array<ItemStack>>()
	private var worn = Array(PIECES) { ItemStack.EMPTY }
	private var moved: Slot? = null

	init {
		on<ContainerReadyEvent> { learn(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { learn(it.title.string, it.stacks) }
		on<GuiOpenEvent> { if (it.screen is InventoryScreen) shift(true) }
		on<GuiCloseEvent> { if (it.screen is InventoryScreen) shift(false) }
		on<SlotRenderEvent.Pre> { offhandBackdrop(it) }
		on<ScreenRenderEvent.Pre> { tooltip(it) }
		on<ScreenRenderEvent.Post> { draw(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onDisabled() {
		shift(false)
		sets.clear()
	}

	override fun onReset() {
		sets.clear()
		worn = Array(PIECES) { ItemStack.EMPTY }
	}

	@JvmStatic
	fun buttonShift(): Int = if (enabled && SkyBlockLocation.inSkyBlock) BUTTON_CLEARANCE else 0

	internal fun placeholder(name: String): Boolean {
		val plain = withoutCodes(name).trim().lowercase(Locale.ROOT)
		return plain.startsWith(EMPTY_PIECE) || plain.startsWith(NUMBERED_PIECE)
	}

	internal fun column(stacks: List<ItemStack>, column: Int): List<ItemStack> {
		val found = ArrayList<ItemStack>(PIECES)
		for (index in stacks.indices) {
			if (index % ROW != column) continue
			val stack = stacks[index]
			if (stack.`is`(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK))) continue
			found.add(if (placeholder(stack.hoverName.string)) ItemStack.EMPTY else stack)
		}
		return found
	}

	internal fun wardrobeColumn(stacks: List<ItemStack>): Int {
		for (index in SET_FIRST..SET_LAST) {
			val stack = stacks.getOrNull(index) ?: continue
			if (stack.`is`(Items.DYE.pick(DyeColor.LIME))) return index % ROW
		}
		return NO_COLUMN
	}

	private fun learn(rawTitle: String, stacks: List<ItemStack>) {
		if (!SkyBlockLocation.inSkyBlock) return
		val title = withoutCodes(rawTitle)
		val column = when {
			EQUIPMENT_MENU.matches(title) -> EQUIPMENT_COLUMN
			WARDROBE_MENU.matches(title) -> wardrobeColumn(stacks)
			else -> return
		}
		if (column == NO_COLUMN) return
		val pieces = column(stacks, column)
		if (pieces.size < PIECES) return
		val held = current()
		for (piece in 0 until PIECES) held[piece] = pieces[piece]
	}

	private fun current(): Array<ItemStack> =
		sets.getOrPut(key()) { Array(PIECES) { ItemStack.EMPTY } }.also { worn = it }

	private fun key(): String = "${ProfileHooks.profile}|${set()}"

	private fun set(): String = when (SkyBlockLocation.island) {
		Island.THE_RIFT -> RIFT_SET
		Island.CRITTER_SAFARI -> SAFARI_SET
		else -> MAIN_SET
	}

	private fun shift(on: Boolean) {
		if (!on) {
			val held = moved ?: return
			(held as SlotAccessor).dhenSetX(held.x - BUTTON_CLEARANCE)
			moved = null
			return
		}
		if (moved != null || !enabled || !SkyBlockLocation.inSkyBlock) return
		val offhand = Minecraft.getInstance().player?.inventoryMenu?.slots?.getOrNull(OFFHAND_SLOT) ?: return
		(offhand as SlotAccessor).dhenSetX(offhand.x + BUTTON_CLEARANCE)
		moved = offhand
		current()
	}

	private fun offhandBackdrop(event: SlotRenderEvent.Pre) {
		if (moved == null || event.screen !is InventoryScreen) return
		val slot = event.slot
		if (slot.index != OFFHAND_SLOT) return
		SharpGui.fill(
			event.graphics,
			slot.x - CELL_INSET,
			slot.y - CELL_INSET,
			slot.x - CELL_INSET + CELL,
			slot.y - CELL_INSET + CELL,
			GlassGui.raised()
		)
	}

	internal fun hoveredPiece(screen: InventoryScreen, mouseX: Int, mouseY: Int): Int {
		val origin = screen as ContainerOrigin
		if (origin.dhenHoveredSlot() != null) return NO_PIECE
		val left = origin.dhenContainerLeft() + COLUMN_LEFT
		if (mouseX !in left until left + SLOT_BOX) return NO_PIECE
		val offset = mouseY - (origin.dhenContainerTop() + COLUMN_TOP)
		if (offset < 0) return NO_PIECE
		val piece = offset / CELL
		if (piece >= PIECES || offset - piece * CELL >= SLOT_BOX) return NO_PIECE
		return piece
	}

	private fun tooltip(event: ScreenRenderEvent.Pre) {
		if (moved == null) return
		val screen = event.screen as? InventoryScreen ?: return
		if (!screen.menu.carried.isEmpty) return
		val piece = hoveredPiece(screen, event.mouseX, event.mouseY)
		if (piece == NO_PIECE || worn[piece].isEmpty) return
		event.graphics.setTooltipForNextFrame(Minecraft.getInstance().font, worn[piece], event.mouseX, event.mouseY)
	}

	private fun draw(event: ScreenRenderEvent.Post) {
		if (moved == null) return
		val screen = event.screen as? InventoryScreen ?: return
		val origin = screen as ContainerOrigin
		val left = origin.dhenContainerLeft() + COLUMN_LEFT
		val top = origin.dhenContainerTop() + COLUMN_TOP
		val font = Minecraft.getInstance().font
		val hovered = hoveredPiece(screen, event.mouseX, event.mouseY)
		for (piece in 0 until PIECES) {
			val cell = GlassGui.raised(piece == hovered)
			ItemGui.slot(event.graphics, font, worn[piece], left - CELL_INSET, top + piece * CELL - CELL_INSET, CELL, cell)
		}
	}

	private fun clicked(event: ContainerClickEvent) {
		if (moved == null) return
		val screen = event.screen as? InventoryScreen ?: return
		if (hoveredPiece(screen, event.click.x().toInt(), event.click.y().toInt()) == NO_PIECE) return
		event.cancelled = true
		Minecraft.getInstance().connection?.sendCommand(if (wardrobeless()) STATS_COMMAND else EQUIPMENT_COMMAND)
	}

	private fun wardrobeless(): Boolean =
		SkyBlockLocation.island == Island.THE_RIFT || SkyBlockLocation.island == Island.CRITTER_SAFARI

	private const val PIECES = 4
	private const val ROW = 9
	private const val CELL = 18
	private const val CELL_INSET = 1
	private const val COLUMN_LEFT = 77
	private const val COLUMN_TOP = 8
	private const val OFFHAND_SLOT = 45
	private const val BUTTON_CLEARANCE = 21
	private const val EQUIPMENT_COLUMN = 1
	private const val NO_COLUMN = -1
	private const val NO_PIECE = -1
	private const val SET_FIRST = 36
	private const val SET_LAST = 44
	private const val EMPTY_PIECE = "empty"
	private const val NUMBERED_PIECE = "slot "
	private const val MAIN_SET = "main"
	private const val RIFT_SET = "rift"
	private const val SAFARI_SET = "safari"
	private const val EQUIPMENT_COMMAND = "equipment"
	private const val STATS_COMMAND = "stats"

	private val EQUIPMENT_MENU = Regex("^Stats & Equipment$|^(?:\\(\\d/\\d\\) )?Loadouts$")
	private val WARDROBE_MENU = Regex("^(?:\\(\\d/\\d\\) )?Equipment Sets$")
}
