package io.github.dzkchen.dhen.render

import io.github.dzkchen.dhen.features.visual.RevertAxes
import io.github.dzkchen.dhen.privacy.PackOverrides
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile

object ItemModels {
	@JvmStatic
	fun model(stack: ItemStack, current: Identifier?): Identifier? =
		PackOverrides.model(stack, RevertAxes.model(stack, current))

	@JvmStatic
	fun foil(stack: ItemStack, current: Boolean): Boolean = PackOverrides.foil(stack, current)

	@JvmStatic
	fun headProfile(stack: ItemStack, current: ResolvableProfile?): ResolvableProfile? =
		PackOverrides.headProfile(stack, current)
}
