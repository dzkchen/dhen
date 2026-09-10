package io.github.dzkchen.dhen.features.inventory

import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu

internal class WardrobeEditScreen(menu: ChestMenu, inventory: Inventory, title: Component) : ContainerScreen(menu, inventory, title) {
	override fun removed() {
		if (!CustomWardrobe.swapping) super.removed()
	}
}
