package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module

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

	@Volatile
	private var startedRunningSprint = false

	override fun onDisabled() {
		startedRunningSprint = false
	}

	@JvmStatic
	fun forcesSprint(inWater: Boolean): Boolean {
		val forced = enabled && shouldHoldSprint(disableInWaterSetting.on, inWater)
		startedRunningSprint = forced
		return forced
	}

	@JvmStatic
	fun stopsForcedSwimSprint(playerHoldingSprint: Boolean): Boolean =
		enabled && disableInWaterSetting.on && startedRunningSprint && !playerHoldingSprint

	@JvmStatic
	fun suppressesDoubleTapSprint(inWater: Boolean): Boolean =
		enabled && disableInWaterSetting.on && inWater

	internal fun shouldHoldSprint(disableInWater: Boolean, inWater: Boolean): Boolean =
		!(disableInWater && inWater)
}
