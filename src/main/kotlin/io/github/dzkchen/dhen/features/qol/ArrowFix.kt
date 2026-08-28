package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ArrowFix : Module(
	name = "Arrow Fix",
	category = Category.QOL,
	description = "Removes the bow pullback animation from shortbows."
) {
	private val shortbowIds = HashSet<String>()
	private val otherIds = HashSet<String>()

	@JvmStatic
	fun shouldStop(stack: ItemStack): Boolean = enabled && isShortbow(stack)

	internal fun isShortbow(stack: ItemStack): Boolean {
		if (stack.isEmpty || !stack.`is`(Items.BOW)) return false
		val id = SkyBlockItems.of(stack).id
		if (id.isNotEmpty()) {
			if (id in shortbowIds) return true
			if (id in otherIds) return false
		}

		val lore = SkyBlockItems.lore(stack)
		var shortbow = false
		for (index in lore.size - 3 downTo 0) {
			if (SHORTBOW_MARKER in lore[index].string) {
				shortbow = true
				break
			}
		}
		if (id.isNotEmpty()) {
			if (shortbow) shortbowIds += id else otherIds += id
		}
		return shortbow
	}

	private const val SHORTBOW_MARKER = "Shortbow: Instantly shoots!"
}
