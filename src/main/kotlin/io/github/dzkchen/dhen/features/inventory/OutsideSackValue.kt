package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.sack.SackState
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.grouped
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.GuiGraphicsExtractor

object OutsideSackValue : Module(
	name = "Outside Sack Value",
	category = Category.INVENTORY,
	description = "Shows what everything sitting in your sacks is worth without opening them."
) {
	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)

	internal val element = hud(OutsideSackValueElement())

	private var ticks = 0

	init {
		on<ClientTickEvent.End> { ticked() }
		on<ContainerReadyEvent> { refresh() }
		on<ContainerUpdatedEvent> { refresh() }
		on<ContainerClosedEvent> { refresh() }
	}

	override fun onEnabled() {
		priceHold.ensure()
		repoHold.ensure()
		refresh()
	}

	override fun onDisabled() {
		priceHold.release()
		repoHold.release()
		element.clear()
	}

	private fun ticked() {
		priceHold.ensure()
		repoHold.ensure()
		if (++ticks < REFRESH_TICKS) return
		ticks = 0
		refresh()
	}

	private fun refresh() {
		if (!SkyBlockLocation.inSkyBlock || SackState.inSack) {
			element.clear()
			return
		}
		val bazaar = ItemRepo.constants.sackItemIds
		var lowest = 0.0
		var highest = 0.0
		var items = 0L
		for ((marketId, amount) in SackState.contents) {
			if (amount <= 0L || marketId !in bazaar) continue
			val sell = Prices.price(marketId, PriceSource.BAZAAR_INSTANT_SELL) ?: continue
			val buy = Prices.priceOr(marketId, PriceSource.BAZAAR_INSTANT_BUY, sell)
			lowest += sell * amount
			highest += buy * amount
			items += amount
		}
		element.show(lowest.toLong(), highest.toLong(), items)
	}

	private const val REFRESH_TICKS = 100
}

internal class OutsideSackValueElement : HudElement(
	"Outside Sack Value",
	HudAnchor.BOTTOM_RIGHT,
	-MARGIN,
	-MARGIN,
	inMenus = true
) {
	private val memo = DhenType.memo()

	private var text = ""

	fun clear() {
		text = ""
	}

	fun show(lowest: Long, highest: Long, items: Long) {
		text = if (items <= 0L) "" else "${shortNumber(lowest)}-${shortNumber(highest)} in sacks (${grouped(items)} items)"
	}

	override val hasContent: Boolean
		get() = (text.isNotEmpty() && overOwnScreen()) || editingHud()

	private fun overOwnScreen(): Boolean {
		val screen = Minecraft.getInstance().gui.screen()
		return screen == null || screen is InventoryScreen
	}

	override fun width(font: Font): Int = memo.width(font, shown())

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, shown(), 0, 0, DhenPalette.SLOT_GOLD, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	private fun shown(): String = text.ifEmpty { PREVIEW }

	private companion object {
		const val PREVIEW = "0-0 in sacks (0 items)"
	}
}

private const val MARGIN = 8
