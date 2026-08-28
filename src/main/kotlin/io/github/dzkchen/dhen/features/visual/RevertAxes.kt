package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object RevertAxes : Module(
	name = "Revert Axes",
	category = Category.VISUAL,
	description = "Restores vanilla axe models for selected SkyBlock weapons."
) {
	@JvmStatic
	fun model(stack: ItemStack, current: Identifier?): Identifier? {
		if (!enabled || stack.isEmpty) return current
		return replacementItem(SkyBlockItems.of(stack).id)?.components()?.get(DataComponents.ITEM_MODEL) ?: current
	}

	internal fun replacementItem(id: String): Item? = when (id) {
		RAGNAROCK_AXE, DAEDALUS_AXE, STARRED_DAEDALUS_AXE -> Items.GOLDEN_AXE
		AXE_OF_THE_SHREDDED -> Items.DIAMOND_AXE
		else -> null
	}

	private const val RAGNAROCK_AXE = "RAGNAROCK_AXE"
	private const val DAEDALUS_AXE = "DAEDALUS_AXE"
	private const val STARRED_DAEDALUS_AXE = "STARRED_DAEDALUS_AXE"
	private const val AXE_OF_THE_SHREDDED = "AXE_OF_THE_SHREDDED"
}
