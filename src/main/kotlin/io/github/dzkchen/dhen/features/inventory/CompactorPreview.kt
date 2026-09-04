package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.regex.Pattern

internal class CompactorShape(val rows: Int, val columns: Int) {
	val cells: Int get() = rows * columns
}

object CompactorPreview : Module(
	name = "Compactor Preview",
	category = Category.INVENTORY,
	description = "Shows what a Personal Compactor or Deletor is loaded with when you hover it."
) {
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private val identity = Pattern.compile("PERSONAL_(?<type>COMPACTOR|DELETOR)_(?<size>\\d+)").matcher("")
	private val headerMemo = DhenType.memo()

	private var cachedData: CompoundTag? = null
	private var cachedGrid: CompactorGrid? = null
	private var cachedGap = NO_LINE

	init {
		on<TooltipEvent> { preview(it) }
		on<ClientTickEvent.End> { repoHold.ensure() }
	}

	override fun onDisabled() {
		repoHold.release()
		forget()
	}

	override fun onReset() = forget()

	internal fun forget() {
		cachedData = null
		cachedGrid = null
		cachedGap = NO_LINE
	}

	internal fun shape(size: String): CompactorShape = SHAPES[size] ?: DEFAULT_SHAPE

	internal fun slotKeyPrefix(type: String): String = type.lowercase(Locale.ROOT).substring(0, PREFIX_LENGTH)

	internal fun insertionPoint(lines: List<Component>): Int {
		var blanks = 0
		for (index in lines.indices) {
			if (lines[index].string.isEmpty()) blanks++
			if (blanks == SECOND_BLANK) return index
		}
		return NO_LINE
	}

	private fun preview(event: TooltipEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val item = SkyBlockItems.of(event.stack)
		if (!identity.reset(item.id).matches()) return
		val shape = shape(identity.group("size"))
		if (item.tag !== cachedData || cachedGap >= event.lines.size) {
			build(item.tag, identity.group("type"), shape, event.lines)
		}
		val gap = cachedGap
		if (gap == NO_LINE) return
		val grid = cachedGrid
		if (grid == null) {
			event.edit().add(gap, Component.literal("§7${shape.cells} ${if (shape.cells == 1) "slot" else "slots"}"))
			return
		}
		val components = ArrayList<ClientTooltipComponent>(event.lines.size + 2)
		for (line in event.lines) components.add(ClientTooltipComponent.create(line.visualOrderText))
		components.add(gap, grid)
		activeLine(item.tag)?.let { components.add(gap, it) }
		event.cancelled = true
		event.graphics.tooltip(
			Minecraft.getInstance().font,
			components,
			event.x,
			event.y,
			DefaultTooltipPositioner.INSTANCE,
			event.stack.get(DataComponents.TOOLTIP_STYLE)
		)
	}

	private fun activeLine(tag: CompoundTag): ClientTooltipComponent? {
		if (!tag.contains(DELETOR_ACTIVE)) return null
		val text = if (tag.getBooleanOr(DELETOR_ACTIVE, false)) "§lActive: §a§lYES" else "§lActive: §c§lNO"
		return ClientTooltipComponent.create(Component.literal(text).visualOrderText)
	}

	private fun build(tag: CompoundTag, type: String, shape: CompactorShape, lines: List<Component>) {
		cachedData = tag
		cachedGap = insertionPoint(lines)
		val prefix = slotKeyPrefix(type)
		val cells = arrayOfNulls<ItemStack>(shape.cells)
		var loaded = 0
		for (key in tag.keySet()) {
			if (!key.contains(prefix)) continue
			val cell = key.substringAfterLast('_').toIntOrNull() ?: continue
			if (cell < 0 || cell >= cells.size) continue
			val id = tag.getStringOr(key, "")
			if (id.isEmpty()) continue
			cells[cell] = ItemRepo.ingredientStack(id)
			loaded++
		}
		cachedGrid = if (loaded == 0) null else CompactorGrid(cells, shape, headerMemo)
	}

	private const val PREFIX_LENGTH = 7
	private const val SECOND_BLANK = 2
	private const val NO_LINE = -1
	private const val DELETOR_ACTIVE = "PERSONAL_DELETOR_ACTIVE"

	private val DEFAULT_SHAPE = CompactorShape(1, 6)

	private val SHAPES = mapOf(
		"4000" to CompactorShape(1, 1),
		"5000" to CompactorShape(1, 3),
		"6000" to CompactorShape(1, 7),
		"7000" to CompactorShape(2, 6)
	)
}

internal class CompactorGrid(
	private val cells: Array<ItemStack?>,
	private val shape: CompactorShape,
	private val headerMemo: TextMemo
) : ClientTooltipComponent {
	private val panelColumns = maxOf(shape.columns, MIN_COLUMNS)
	private val indent = (panelColumns - shape.columns) / 2

	override fun getHeight(font: Font): Int = HEADER + shape.rows * CELL

	override fun getWidth(font: Font): Int = SIDE_PAD * 2 + panelColumns * CELL

	override fun extractImage(font: Font, x: Int, y: Int, width: Int, height: Int, graphics: GuiGraphicsExtractor) {
		headerMemo.text(graphics, font, HEADER_LABEL, x + SIDE_PAD, y + HEADER_TOP, DhenPalette.TEXT_SECONDARY)
		for (cell in cells.indices) {
			val left = x + SIDE_PAD + (indent + cell % shape.columns) * CELL
			val top = y + HEADER + cell / shape.columns * CELL
			ItemGui.slot(graphics, font, cells[cell] ?: ItemStack.EMPTY, left, top, CELL, DhenPalette.SURFACE)
		}
	}

	private companion object {
		private const val CELL = 18
		private const val HEADER = 18
		private const val HEADER_TOP = 5
		private const val SIDE_PAD = 7
		private const val MIN_COLUMNS = 3
		private const val HEADER_LABEL = "Contents"
	}
}
