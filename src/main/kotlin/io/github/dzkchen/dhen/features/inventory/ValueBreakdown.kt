package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.value.ItemValue
import io.github.dzkchen.dhen.data.value.ValueLine
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.grouped
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Locale

object ValueBreakdown : Module(
	name = "Value Breakdown",
	category = Category.INVENTORY,
	description = "Opens a panel that lists, line by line, why the item under your cursor is worth what it is."
) {
	private val openSetting = KeybindSetting(
		"Open Breakdown",
		GLFW.GLFW_KEY_I,
		"Opens the breakdown for the item your cursor is on.",
		KeybindScreenPolicy.NON_TEXT_SCREEN
	).onPress { pressed() }

	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)

	private val labelMemos = Array(VISIBLE_ROWS) { DhenType.memo() }
	private val amountMemos = Array(VISIBLE_ROWS) { DhenType.memo() }
	private val shareMemos = Array(VISIBLE_ROWS) { DhenType.memo() }
	private val titleMemo = DhenType.memo()
	private val totalMemo = DhenType.memo()
	private val totalAmountMemo = DhenType.memo()

	private val rows = ArrayList<ValueRow>()

	private var icon: ItemStack = ItemStack.EMPTY
	private var title = ""
	private var totalText = ""
	private var scroll = 0

	internal var showing = false
		private set

	init {
		registerSetting(openSetting)

		on<ScreenRenderEvent.Post> { draw(it) }
		on<ContainerKeyEvent> { typed(it) }
		on<ContainerClickEvent> { if (showing) { close(); it.cancelled = true } }
		on<ContainerScrollEvent> { scrolled(it) }
		on<TooltipEvent> { if (showing) it.cancelled = true }
		on<GuiCloseEvent> { close() }
	}

	override fun onEnabled() {
		priceHold.ensure()
		repoHold.ensure()
	}

	override fun onDisabled() {
		priceHold.release()
		repoHold.release()
		close()
	}

	internal fun close() {
		showing = false
		icon = ItemStack.EMPTY
		rows.clear()
		scroll = 0
	}

	private fun pressed() {
		if (showing || !SkyBlockLocation.inSkyBlock) return
		val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return
		val stack = (screen as ContainerOrigin).dhenHoveredSlot()?.item ?: return
		if (stack.isEmpty) return
		if (!ItemRepo.ready) {
			Dhen.announce(NOT_READY)
			return
		}
		build(stack)
	}

	private fun build(stack: ItemStack) {
		val valuation = ItemValue.of(stack, VALUE_SOURCE)
		if (valuation.total <= 0.0 && valuation.breakdown.isEmpty()) {
			Dhen.announce(NO_VALUE)
			return
		}
		rows.clear()
		for (line in valuation.breakdown) {
			rows += ValueRow(line.label, amountText(line.amount, line.priced), shareText(line, valuation.total))
		}
		icon = stack
		showing = true
		title = withoutCodes(stack.hoverName.string)
		totalText = grouped(valuation.total.toLong())
		scroll = 0
	}

	private fun amountText(amount: Double, priced: Boolean): String =
		if (!priced) NO_PRICE else grouped(amount.toLong())

	private fun shareText(line: ValueLine, total: Double): String {
		if (!line.priced || total <= 0.0 || line.amount <= 0.0) return ""
		return String.format(Locale.US, SHARE_FORMAT, line.amount / total * PERCENT)
	}

	private fun typed(event: ContainerKeyEvent) {
		if (!showing) return
		when (event.input.key()) {
			GLFW.GLFW_KEY_UP -> scrollBy(-1)
			GLFW.GLFW_KEY_DOWN -> scrollBy(1)
			else -> close()
		}
		event.cancelled = true
	}

	private fun scrolled(event: ContainerScrollEvent) {
		if (!showing) return
		scrollBy(if (event.scrollY > 0.0) -1 else 1)
		event.cancelled = true
	}

	private fun scrollBy(steps: Int) {
		scroll = (scroll + steps).coerceIn(0, maxOf(0, rows.size - VISIBLE_ROWS))
	}

	private fun draw(event: ScreenRenderEvent.Post) {
		if (!showing) return
		val graphics = event.graphics
		val screen = event.screen
		val font = Minecraft.getInstance().font
		val shown = minOf(rows.size, VISIBLE_ROWS)
		val panelHeight = HEADER_HEIGHT + shown * ROW_HEIGHT + FOOTER_HEIGHT + PADDING
		val left = (screen.width - PANEL_WIDTH) / 2
		val top = (screen.height - panelHeight) / 2
		GlassGui.scrim(graphics, screen.width, screen.height)
		GlassGui.roundedFrame(
			graphics,
			left,
			top,
			left + PANEL_WIDTH,
			top + panelHeight,
			PANEL_RADIUS,
			GlassGui.canvas(),
			DhenPalette.BORDER
		)
		ItemGui.stack(graphics, icon, left + PADDING, top + PADDING)
		val headerText = titleMemo.fit(font, title, PANEL_WIDTH - 3 * PADDING - SLOT_BOX)
		titleMemo.text(graphics, font, headerText, left + PADDING * 2 + SLOT_BOX, top + PADDING + TEXT_LIFT, DhenPalette.TEXT_PRIMARY)
		drawRows(graphics, left, top, shown)
		drawTotal(graphics, left, top + panelHeight - FOOTER_HEIGHT)
		if (rows.size > VISIBLE_ROWS) drawScrollBar(graphics, left, top, shown)
	}

	private fun drawRows(graphics: GuiGraphicsExtractor, left: Int, top: Int, shown: Int) {
		val font = Minecraft.getInstance().font
		val labelLeft = left + PADDING
		val amountRight = left + PANEL_WIDTH - PADDING - SHARE_WIDTH
		for (index in 0 until shown) {
			val row = rows[scroll + index]
			val rowTop = top + HEADER_HEIGHT + index * ROW_HEIGHT
			val labelMemo = labelMemos[index]
			val amountMemo = amountMemos[index]
			val label = labelMemo.fit(font, row.label, amountRight - labelLeft - AMOUNT_WIDTH - GUTTER)
			labelMemo.text(graphics, font, label, labelLeft, rowTop, DhenPalette.TEXT_SECONDARY)
			val amountInk = if (row.amount == NO_PRICE) DhenPalette.TEXT_DISABLED else DhenPalette.TEXT_PRIMARY
			amountMemo.text(
				graphics,
				font,
				row.amount,
				amountRight - amountMemo.width(font, row.amount),
				rowTop,
				amountInk
			)
			if (row.share.isEmpty()) continue
			shareMemos[index].text(
				graphics,
				font,
				row.share,
				left + PANEL_WIDTH - PADDING - shareMemos[index].width(font, row.share),
				rowTop,
				DhenPalette.TEXT_DISABLED
			)
		}
	}

	private fun drawTotal(graphics: GuiGraphicsExtractor, left: Int, top: Int) {
		val font = Minecraft.getInstance().font
		totalMemo.text(graphics, font, TOTAL_LABEL, left + PADDING, top, DhenPalette.TEXT_PRIMARY)
		val right = left + PANEL_WIDTH - PADDING
		totalAmountMemo.text(
			graphics,
			font,
			totalText,
			right - totalAmountMemo.width(font, totalText),
			top,
			DhenPalette.accent
		)
	}

	private fun drawScrollBar(graphics: GuiGraphicsExtractor, left: Int, top: Int, shown: Int) {
		val trackTop = top + HEADER_HEIGHT
		val trackHeight = shown * ROW_HEIGHT
		val knobHeight = maxOf(MIN_KNOB, trackHeight * shown / rows.size)
		val travel = trackHeight - knobHeight
		val knobTop = trackTop + travel * scroll / maxOf(1, rows.size - shown)
		val barLeft = left + PANEL_WIDTH - BAR_INSET
		RoundedGui.pill(graphics, barLeft, trackTop, barLeft + BAR_WIDTH, trackTop + trackHeight, GlassGui.surface())
		RoundedGui.pill(graphics, barLeft, knobTop, barLeft + BAR_WIDTH, knobTop + knobHeight, DhenPalette.accentMuted)
	}

	private class ValueRow(val label: String, val amount: String, val share: String)

	private val VALUE_SOURCE = PriceSource.BAZAAR_INSTANT_SELL

	private const val NOT_READY = "The item catalog is still loading, so a breakdown would be missing lines."
	private const val NO_VALUE = "Dhen prices nothing on that item."
	private const val NO_PRICE = "no price"
	private const val TOTAL_LABEL = "Total"
	private const val SHARE_FORMAT = "%.1f%%"
	private const val PERCENT = 100.0
	private const val PANEL_WIDTH = 260
	private const val PANEL_RADIUS = 6f
	private const val PADDING = 8
	private const val ROW_HEIGHT = 11
	private const val VISIBLE_ROWS = 16
	private const val HEADER_HEIGHT = 28
	private const val FOOTER_HEIGHT = 16
	private const val TEXT_LIFT = 4
	private const val AMOUNT_WIDTH = 60
	private const val SHARE_WIDTH = 34
	private const val GUTTER = 6
	private const val BAR_WIDTH = 2
	private const val BAR_INSET = 5
	private const val MIN_KNOB = 8
}
