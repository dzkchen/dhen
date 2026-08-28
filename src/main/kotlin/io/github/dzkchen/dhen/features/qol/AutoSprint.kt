package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft

object AutoSprint : Module(
	name = "Auto Sprint",
	category = Category.QOL,
	description = "Keeps sprint held while moving."
) {
	init {
		on<ClientTickEvent.Start> {
			val client = Minecraft.getInstance()
			val player = client.player ?: return@on
			if (!shouldHoldSprint(client.gui.screen() != null, player.isSprinting)) return@on
			client.options.keySprint.isDown = true
		}
	}

	internal fun shouldHoldSprint(screenOpen: Boolean, sprinting: Boolean): Boolean =
		!screenOpen && !sprinting
}
