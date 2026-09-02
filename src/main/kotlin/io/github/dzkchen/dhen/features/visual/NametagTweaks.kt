package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import java.util.WeakHashMap

object NametagTweaks : Module(
	name = "Nametag Tweaks",
	category = Category.VISUAL,
	description = "Reworks how nametags are drawn: your own, forced, unbacked, shadowed, or gone."
) {
	internal val showOwnSetting = BooleanSetting(
		"Show Own Nametag",
		description = "Draws your own nametag above your head."
	)
	internal val forceSetting = BooleanSetting(
		"Force Nametags",
		description = "Keeps player nametags visible while they sneak or turn invisible. SkyBlock only."
	)
	internal val hideBackgroundSetting = BooleanSetting(
		"Hide Nametag Background",
		description = "Drops the dark plate behind a nametag."
	)
	internal val shadowedSetting = BooleanSetting(
		"Shadowed Nametag",
		description = "Adds a text shadow to the nametag label."
	)
	internal val hideDinnerboneSetting = BooleanSetting(
		"Hide Dinnerbone Nametags",
		description = "Hides the nametag of any entity named Dinnerbone."
	)

	private var showOwn by showOwnSetting
	private var force by forceSetting
	private var hideBackground by hideBackgroundSetting
	private var shadowed by shadowedSetting
	private var hideDinnerbone by hideDinnerboneSetting

	private val dinnerboneVerdicts = WeakHashMap<Entity, Boolean>()

	override fun onDisabled() {
		dinnerboneVerdicts.clear()
	}

	@JvmStatic
	fun forcesNametags(): Boolean = enabled && force && SkyBlockLocation.inSkyBlock

	@JvmStatic
	fun showsOwnNametag(entity: Entity): Boolean =
		enabled && showOwn && entity === Minecraft.getInstance().player

	@JvmStatic
	fun hidesDinnerboneNametag(entity: Entity): Boolean {
		if (!enabled || !hideDinnerbone) return false
		val name = entity.customName ?: return false
		return dinnerboneVerdicts.getOrPut(entity) { name.string.equals(DINNERBONE, ignoreCase = true) }
	}

	@JvmStatic
	fun hidesNametagBackground(): Boolean = enabled && hideBackground

	@JvmStatic
	fun shadowsNametagText(): Boolean = enabled && shadowed

	private const val DINNERBONE = "Dinnerbone"
}
