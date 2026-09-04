package io.github.dzkchen.dhen.features.dev

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.mayor.Mayor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module

object Simulation : Module(
	name = "Simulation",
	category = Category.DEV,
	description = "Makes Dhen believe you are on SkyBlock, or under Diana, when you are not."
) {
	private val alwaysSkyBlockSetting = BooleanSetting(
		"Always On SkyBlock",
		description = "Every SkyBlock-only feature runs as if you were on a SkyBlock island."
	)

	private val alwaysDianaSetting = BooleanSetting(
		"Always Diana Mayor",
		description = "Every mayor and perk check answers as if Diana were in office."
	)

	internal val onSkyBlock: Boolean
		get() = enabled && alwaysSkyBlockSetting.on

	internal val seatedMayor: Mayor?
		get() = if (enabled && alwaysDianaSetting.on) DIANA else null

	init {
		registerSetting(alwaysSkyBlockSetting)
		registerSetting(alwaysDianaSetting)
	}

	private val DIANA = Mayor("Diana", setOf("Mythological Ritual", "Sharing is Caring", "Pet XP Buff"))
}
