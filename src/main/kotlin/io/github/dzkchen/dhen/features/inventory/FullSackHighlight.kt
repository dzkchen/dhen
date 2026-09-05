package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.sack.SackState
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.world.entity.player.Inventory

object FullSackHighlight : Module(
	name = "Full Sack Highlight",
	category = Category.INVENTORY,
	description = "Shades every slot in an open sack whose item has filled it."
) {
	init {
		on<SlotRenderEvent.Post> { shaded(it) }
	}

	private fun shaded(event: SlotRenderEvent.Post) {
		if (!SackState.inSack || !SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		if (slot.container is Inventory || !SackState.isFull(slot.index)) return
		val shade = DhenPalette.withAlpha(DhenPalette.SLOT_RED, FULL_ALPHA)
		SharpGui.fill(event.graphics, slot.x, slot.y, slot.x + SLOT_BOX, slot.y + SLOT_BOX, shade)
	}

	private const val FULL_ALPHA = 110
}
