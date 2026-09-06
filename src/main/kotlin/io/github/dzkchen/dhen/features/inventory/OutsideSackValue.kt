package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.sack.SackState
import io.github.dzkchen.dhen.data.sack.SackStatus
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HUD_MARGIN
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

	private var settledRevision = 0

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
		if (SackState.revision != settledRevision) {
			settledRevision = SackState.revision
			ticks = 0
			refresh()
			return
		}
		if (++ticks < REFRESH_TICKS) return
		ticks = 0
		refresh()
	}

	private fun refresh() {
		if (!SkyBlockLocation.inSkyBlock || SackState.inSack) {
			element.clear()
			return
		}
		val source = SackDisplay.priceSource()
		val bazaar = ItemRepo.constants.sackItemIds
		var worth = 0.0
		var items = 0L
		var guessed = false
		for ((marketId, amount) in SackState.contents) {
			if (amount <= 0L || marketId !in bazaar) continue
			worth += Prices.priceOr(marketId, source, 0.0) * amount
			items += amount
			if (SackState.statusOf(marketId) == SackStatus.OUTDATED) guessed = true
		}
		element.show(worth.toLong(), items, guessed)
	}

	private const val REFRESH_TICKS = 100
}

internal class OutsideSackValueElement : HudElement(
	"Outside Sack Value",
	HudAnchor.BOTTOM_RIGHT,
	-HUD_MARGIN,
	-HUD_MARGIN,
	inMenus = true
) {
	private val memo = DhenType.memo()

	private var text = ""

	private var guessed = false

	fun clear() {
		text = ""
		guessed = false
	}

	fun show(worth: Long, items: Long, guessed: Boolean) {
		this.guessed = guessed
		text = if (items <= 0L) "" else "${shortNumber(worth)} in sacks (${grouped(items)} items)"
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
		val ink = if (guessed && text.isNotEmpty()) DhenPalette.TEXT_SECONDARY else DhenPalette.SLOT_GOLD
		memo.shadowed(graphics, font, shown(), 0, 0, ink, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	private fun shown(): String = text.ifEmpty { PREVIEW }

	private companion object {
		const val PREVIEW = "0 in sacks (0 items)"
	}
}
