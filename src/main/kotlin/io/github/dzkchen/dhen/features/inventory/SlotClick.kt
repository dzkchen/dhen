package io.github.dzkchen.dhen.features.inventory

import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

internal fun clickSlot(menu: AbstractContainerMenu, slot: Int, button: Int, input: ContainerInput) {
	val minecraft = Minecraft.getInstance()
	val player = minecraft.player ?: return
	val gameMode = minecraft.gameMode ?: return
	if (player.containerMenu !== menu) return
	gameMode.handleContainerInput(menu.containerId, slot, button, input, player)
}
