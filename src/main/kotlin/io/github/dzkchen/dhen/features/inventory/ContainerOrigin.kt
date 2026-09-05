package io.github.dzkchen.dhen.features.inventory

import net.minecraft.world.inventory.Slot

internal interface ContainerOrigin {
	fun dhenContainerLeft(): Int

	fun dhenContainerTop(): Int

	fun dhenContainerWidth(): Int

	fun dhenContainerHeight(): Int

	fun dhenHoveredSlot(): Slot?
}
