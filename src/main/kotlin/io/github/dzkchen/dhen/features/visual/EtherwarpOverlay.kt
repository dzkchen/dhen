package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.BoxStyle
import io.github.dzkchen.dhen.render.WorldDepth
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.EtherwarpGuess
import io.github.dzkchen.dhen.util.EtherwarpTarget
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction

object EtherwarpOverlay : Module(
	name = "Etherwarp Overlay",
	category = Category.VISUAL,
	description = "Marks the block Etherwarp would take you to while you sneak with an Aspect of the Void."
) {
	private val modeSetting = SelectorSetting(
		"Mode",
		BoxStyle.OUTLINE,
		BoxStyle.options,
		description = "Whether the box is drawn as edges, as a solid, or both."
	)
	private var mode by modeSetting

	private var phase by BooleanSetting("Phase", description = "Draws the box through walls.")

	private var lineWidth by NumberSetting("Line Width", 1.0, 1.0, 10.0, 0.1)
		.withDependency { BoxStyle.outlines(modeSetting.value) }

	private val showFailSetting = BooleanSetting(
		"Show Fail",
		default = true,
		description = "Also marks the block when the teleport would not go through."
	)
	private var showFail by showFailSetting

	private var fullBlock by BooleanSetting(
		"Full Block",
		description = "Draws a whole block instead of the target block's own shape."
	)

	internal val previousTickOriginSetting = BooleanSetting(
		"Use Server Position",
		description = "Aims from where you stood last tick instead of where you stand now."
	)

	private var previousTickOrigin by previousTickOriginSetting

	private var fillColor by ColorSetting("Fill Color", Color.rgba(0, 134, 255, 50), allowAlpha = true)
		.withDependency { BoxStyle.fills(modeSetting.value) }

	private var outlineColor by ColorSetting("Outline Color", Color.rgba(0, 134, 255))
		.withDependency { BoxStyle.outlines(modeSetting.value) }

	private var invalidFillColor by ColorSetting("Invalid Fill Color", Color.rgba(255, 0, 0, 50), allowAlpha = true)
		.withDependency { BoxStyle.fills(modeSetting.value) && showFailSetting.on }

	private var invalidOutlineColor by ColorSetting("Invalid Outline Color", Color.rgba(255, 0, 0))
		.withDependency { BoxStyle.outlines(modeSetting.value) && showFailSetting.on }

	private val target = EtherwarpTarget()

	init {
		on<WorldRenderEvent> { render(it) }
	}

	private fun render(event: WorldRenderEvent) {
		val client = Minecraft.getInstance()
		if (client.gui.screen() != null) return
		val player = client.player ?: return
		val item = EtherwarpGuess.etherwarpItem(player.mainHandItem) ?: return
		if (EtherwarpGuess.requiresSneak(item) && !client.options.keyShift.isDown) return
		EtherwarpGuess.aimedAtTarget(previousTickOrigin, EtherwarpGuess.distanceOf(item), target)
		if (!target.found) return
		if (!target.succeeded && !showFail) return

		val outlineArgb = (if (target.succeeded) outlineColor else invalidOutlineColor).argb
		val fillArgb = (if (target.succeeded) fillColor else invalidFillColor).argb
		val depth = if (phase) WorldDepth.THROUGH_WALLS else WorldDepth.TESTED
		if (fullBlock) {
			WorldDraw.drawBox(
				event,
				target.x - FULL_BLOCK_INFLATION,
				target.y.toDouble(),
				target.z - FULL_BLOCK_INFLATION,
				target.x + 1.0 + FULL_BLOCK_INFLATION,
				target.y + 1.0 + FULL_BLOCK_INFLATION * 2.0,
				target.z + 1.0 + FULL_BLOCK_INFLATION,
				outlineArgb,
				fillArgb,
				BoxStyle.outlines(mode),
				BoxStyle.fills(mode),
				lineWidth.toFloat(),
				depth
			)
			return
		}
		val shape = EtherwarpGuess.shapeAt(target.x, target.y, target.z)
		WorldDraw.drawBox(
			event,
			target.x + shape.min(Direction.Axis.X),
			target.y + shape.min(Direction.Axis.Y),
			target.z + shape.min(Direction.Axis.Z),
			target.x + shape.max(Direction.Axis.X),
			target.y + shape.max(Direction.Axis.Y),
			target.z + shape.max(Direction.Axis.Z),
			outlineArgb,
			fillArgb,
			BoxStyle.outlines(mode),
			BoxStyle.fills(mode),
			lineWidth.toFloat(),
			depth
		)
	}

	private const val FULL_BLOCK_INFLATION = 0.00005
}
