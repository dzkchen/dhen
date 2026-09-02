package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object HidePlayers : Module(
	name = "Hide Players",
	category = Category.VISUAL,
	description = "Hides other real players standing near you."
) {
	internal val onlyInDungeonsSetting = BooleanSetting(
		"Only in Dungeons",
		description = "Hides players only while you are in the Catacombs."
	)
	internal val hideAllSetting = BooleanSetting(
		"Hide all",
		description = "Hides every player, however far away."
	)
	internal val distanceSetting = NumberSetting(
		"Distance",
		default = 3.0,
		min = 0.0,
		max = 32.0,
		step = 0.5,
		description = "How many blocks away a player has to be before you can see them again."
	).withDependency { !hideAllSetting.on }

	private var onlyInDungeons by onlyInDungeonsSetting
	private var hideAll by hideAllSetting
	private var distance by distanceSetting

	init {
		on<EntityRenderEvent> { if (hides(it.entity)) it.cancelled = true }
	}

	private fun hides(entity: Entity): Boolean {
		if (entity !is Player || entity.uuid.version() != REAL_PLAYER_UUID_VERSION) return false
		val self = Minecraft.getInstance().player ?: return false
		if (entity === self) return false
		if (onlyInDungeons && SkyBlockLocation.island != Island.CATACOMBS) return false
		if (hideAll) return true
		return entity.distanceToSqr(self) <= distance * distance
	}

	private const val REAL_PLAYER_UUID_VERSION = 4
}
