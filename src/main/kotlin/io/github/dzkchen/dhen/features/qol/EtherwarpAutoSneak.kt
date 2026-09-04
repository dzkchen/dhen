package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.delayTicks
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft

object EtherwarpAutoSneak : Module(
	name = "Etherwarp Auto Sneak",
	category = Category.QOL,
	description = "Sneaks for you around a left-click Etherwarp, so the click works while you stand upright."
) {
	private var sneakMillis by NumberSetting(
		"Sneak Time",
		default = 50.0,
		min = 50.0,
		max = 150.0,
		description = "Milliseconds spent sneaking around the click."
	)

	override fun onEnabled() {
		Dhen.automationNotice.announce(name)
	}

	internal fun sneakAround(use: () -> Unit) {
		val sneakKey = Minecraft.getInstance().options.keyShift
		val heldBefore = sneakKey.isDown
		launch {
			val half = (sneakMillis / 2).toLong()
			sneakKey.setDown(true)
			try {
				delayTicks(TICKS_UNTIL_CROUCHED)
				delay(half)
				use()
				delay(half)
			} finally {
				sneakKey.setDown(heldBefore)
			}
		}
	}

	private const val TICKS_UNTIL_CROUCHED = 2
}
