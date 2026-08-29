package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft

object AutoSprint : Module(
	name = "Auto Sprint",
	category = Category.QOL,
	description = "Keeps sprint held while moving."
) {
	internal val disableInWaterSetting = BooleanSetting(
		"Disable In Water",
		description = "Stops sprinting while you are in water."
	)
	private var disableInWater by disableInWaterSetting

	init {
		on<ClientTickEvent.Start> {
			val client = Minecraft.getInstance()
			val player = client.player ?: return@on
			if (!ownsSprintKey(client.gui.screen() != null, player.isSprinting)) return@on
			client.options.keySprint.isDown = shouldHoldSprint(disableInWaterSetting.on, player.isInWater)
		}
	}

	internal fun ownsSprintKey(screenOpen: Boolean, sprinting: Boolean): Boolean =
		!screenOpen && !sprinting

	internal fun shouldHoldSprint(disableInWater: Boolean, inWater: Boolean): Boolean =
		!(disableInWater && inWater)
}
