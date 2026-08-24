package io.github.dzkchen.dhen.data.item

import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

class HeldItem private constructor(
	private val icon: Holder<Item>?,
	private val presentation: DataComponentPatch,
	val count: Int,
	val item: SkyBlockItem,
	val rarity: ItemRarity,
	val name: String
) {
	fun stack(): ItemStack {
		if (icon == null) return ItemStack.EMPTY
		val stack = ItemStack(icon, count, presentation)
		if (item !== SkyBlockItem.NONE) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(item.tag))
		return stack
	}

	companion object {
		fun of(stack: ItemStack): HeldItem {
			if (stack.isEmpty) return EMPTY
			val data = SkyBlockItems.customData(stack)
			val item = if (data == null) SkyBlockItem.NONE else SkyBlockItem.parse(data)
			return HeldItem(
				icon = stack.typeHolder(),
				presentation = stack.componentsPatch.forget { it === DataComponents.CUSTOM_DATA },
				count = stack.count,
				item = item,
				rarity = item.rarity(stack),
				name = withoutCodes(stack.hoverName.string)
			)
		}

		private val EMPTY = HeldItem(null, DataComponentPatch.EMPTY, 0, SkyBlockItem.NONE, ItemRarity.NONE, "")
	}
}
