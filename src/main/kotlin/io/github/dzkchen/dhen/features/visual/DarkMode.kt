package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

object DarkMode : Module(
	name = "Dark Mode",
	category = Category.VISUAL,
	description = "Darkens the world behind screens with an optional HUD tint."
) {
	internal val opacitySetting = NumberSetting("Opacity", 25.0, 1.0, 80.0, 1.0, "Strength of the dark tint.")
	private var opacity by opacitySetting

	internal val tintHudSetting = BooleanSetting("Tint HUD", false, "Draws the tint over HUD elements.")
	private var tintHud by tintHudSetting

	@JvmStatic
	fun drawBehindHud(graphics: GuiGraphicsExtractor) {
		if (!drawsBehindHud()) return
		draw(graphics)
	}

	@JvmStatic
	fun drawOverHud(graphics: GuiGraphicsExtractor) {
		if (!drawsOverHud()) return
		draw(graphics)
	}

	internal fun drawsBehindHud(): Boolean = enabled && !tintHudSetting.on

	internal fun drawsOverHud(): Boolean = enabled && tintHudSetting.on

	internal fun overlayColor(percent: Double = opacitySetting.amount): Int =
		ARGB.black((percent.coerceIn(1.0, 80.0) * CHANNEL_MAX / 100.0).roundToInt())

	private fun draw(graphics: GuiGraphicsExtractor) {
		SharpGui.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), overlayColor())
	}

	private const val CHANNEL_MAX = 255.0
}
