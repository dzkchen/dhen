package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.BlockOutlineEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.BoxStyle
import io.github.dzkchen.dhen.render.WorldDepth
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.EtherwarpGuess
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction

object BlockOverlay : Module(
	name = "Block Overlay",
	category = Category.VISUAL,
	description = "Replaces the vanilla outline around the block you are looking at."
) {
	private val modeSetting = SelectorSetting(
		"Mode",
		BoxStyle.FILLED_OUTLINE,
		BoxStyle.options,
		description = "Whether the block is drawn as edges, as a solid, or both."
	)
	private var mode by modeSetting

	private var fillColor by ColorSetting("Fill Color", Color.rgba(0, 134, 255, 50), allowAlpha = true)
		.withDependency { BoxStyle.fills(modeSetting.value) }

	private var outlineColor by ColorSetting("Outline Color", Color.rgba(0, 134, 255))
		.withDependency { BoxStyle.outlines(modeSetting.value) }

	private var lineWidth by NumberSetting("Line Width", 2.5, 1.0, 10.0, 0.1)
		.withDependency { BoxStyle.outlines(modeSetting.value) }

	private var phase by BooleanSetting("Phase", description = "Draws the outline through walls.")

	private var hideWithEtherwarp by BooleanSetting(
		"Hide with Etherwarp",
		description = "Draws nothing while you sneak with an Etherwarp item, leaving the guess box on its own."
	)

	init {
		on<BlockOutlineEvent> { render(it) }
	}

	private fun render(event: BlockOutlineEvent) {
		if (Minecraft.getInstance().gui.hud.isHidden) return
		if (hideWithEtherwarp && etherwarping()) {
			event.drawsVanillaOutline = false
			return
		}

		val pos = event.pos
		val shape = event.shape
		if (shape.isEmpty) return
		WorldDraw.drawBox(
			event,
			pos.x + shape.min(Direction.Axis.X),
			pos.y + shape.min(Direction.Axis.Y),
			pos.z + shape.min(Direction.Axis.Z),
			pos.x + shape.max(Direction.Axis.X),
			pos.y + shape.max(Direction.Axis.Y),
			pos.z + shape.max(Direction.Axis.Z),
			outlineColor.argb,
			fillColor.argb,
			BoxStyle.outlines(mode),
			BoxStyle.fills(mode),
			lineWidth.toFloat(),
			if (phase) WorldDepth.THROUGH_WALLS else WorldDepth.TESTED
		)
		event.drawsVanillaOutline = false
	}

	private fun etherwarping(): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		return client.options.keyShift.isDown && EtherwarpGuess.etherwarpItem(player.mainHandItem) != null
	}

}
