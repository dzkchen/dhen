package io.github.dzkchen.dhen.features.dev

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.gui.ArcPreviewScreen
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.WorldDepth
import io.github.dzkchen.dhen.render.WorldDraw
import net.minecraft.client.Minecraft
import net.minecraft.util.ARGB
import kotlin.math.ceil
import kotlin.math.sqrt

object RenderTest : Module(
	name = "Render Test",
	category = Category.DEV,
	description = "Draws every world-render shape Dhen owns around you, and can flood the world with boxes."
) {
	private val styleSetting = SelectorSetting(
		"Styled Box Style",
		default = FILL,
		options = listOf(OUTLINE, FILL, FILLED_OUTLINE),
		description = "How the middle box around you is drawn."
	)

	private val boxCountSetting = NumberSetting(
		"Box Count",
		default = 0.0,
		min = 0.0,
		max = 200_000.0,
		step = 1.0,
		description = "How many extra boxes to draw around you, to see where the frame rate breaks."
	)

	private val boxLevelsSetting = NumberSetting(
		"Box Levels",
		default = 4.0,
		min = 1.0,
		max = 12.0,
		step = 1.0,
		description = "How many layers those extra boxes are stacked into."
	)

	init {
		registerSetting(styleSetting)
		registerSetting(boxCountSetting)
		registerSetting(boxLevelsSetting)
		registerSetting(ActionSetting("Open Arc Preview", ::openArcPreview, "Opens the ring-segment preview screen."))

		on<WorldRenderEvent> { draw(it) }
	}

	private fun draw(event: WorldRenderEvent) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(true)
		val ink = DhenPalette.accent
		EntityHighlights.drawEntityBox(
			event, player, partialTick, WIRE_INFLATE, ink, ink, true, false, LINE_WIDTH, WorldDepth.THROUGH_WALLS
		)
		val eye = player.getEyePosition(partialTick)
		WorldDraw.drawLine(event, eye, eye.add(0.0, LINE_RISE, 0.0), ink, LINE_WIDTH, WorldDepth.THROUGH_WALLS)
		val lag = 1f - partialTick
		val standX = player.x + (player.xo - player.x) * lag
		val standY = player.y + (player.yo - player.y) * lag
		val standZ = player.z + (player.zo - player.z) * lag
		WorldDraw.drawWireCircle(
			event, standX, standY, standZ, RING_RADIUS, ink, LINE_WIDTH, WorldDepth.THROUGH_WALLS
		)
		val style = styleSetting.value
		EntityHighlights.drawEntityBox(
			event,
			player,
			partialTick,
			STYLED_INFLATE,
			ink,
			ARGB.color(HALF_ALPHA, ink),
			style != FILL,
			style != OUTLINE,
			LINE_WIDTH,
			WorldDepth.TESTED
		)
		EntityHighlights.drawEntityBox(
			event,
			player,
			partialTick,
			FILLED_INFLATE,
			ink,
			ARGB.color(HALF_ALPHA, ink),
			false,
			true,
			LINE_WIDTH,
			WorldDepth.THROUGH_WALLS
		)
		grid(event, standX, standY, standZ)
	}

	private fun grid(event: WorldRenderEvent, x: Double, y: Double, z: Double) {
		val wanted = boxCountSetting.amount.toInt()
		if (wanted <= 0) return
		val layers = boxLevelsSetting.amount.toInt()
		val perLayer = ceil(wanted.toDouble() / layers).toInt()
		val reach = ceil(sqrt(perLayer.toDouble())).toInt() / 2
		var drawn = 0
		for (layer in 0 until layers) {
			val lift = (layer - layers / 2).toDouble()
			for (stepX in -reach..reach) {
				for (stepZ in -reach..reach) {
					if (drawn >= wanted) return
					val minX = x + stepX - GRID_HALF
					val minY = y + lift - GRID_HALF
					val minZ = z + stepZ - GRID_HALF
					val ink = ARGB.opaque(
						((stepX + reach) and CHANNEL shl 16) or
							((layer + LAYER_TINT) and CHANNEL shl 8) or
							((stepZ + reach) and CHANNEL)
					)
					WorldDraw.drawBox(
						event,
						minX,
						minY,
						minZ,
						minX + GRID_SIDE,
						minY + GRID_SIDE,
						minZ + GRID_SIDE,
						ink,
						ARGB.color(HALF_ALPHA, ink),
						true,
						true,
						LINE_WIDTH,
						WorldDepth.TESTED
					)
					drawn++
				}
			}
		}
	}

	private fun openArcPreview() {
		val client = Minecraft.getInstance()
		client.execute { client.gui.setScreen(ArcPreviewScreen(client.gui.screen())) }
	}

	private const val OUTLINE = "Outline"
	private const val FILL = "Fill"
	private const val FILLED_OUTLINE = "Filled Outline"
	private const val WIRE_INFLATE = 2.0
	private const val STYLED_INFLATE = 1.0
	private const val FILLED_INFLATE = 0.5
	private const val LINE_WIDTH = 3f
	private const val LINE_RISE = 5.0
	private const val RING_RADIUS = 1.0
	private const val HALF_ALPHA = 128
	private const val GRID_HALF = 0.45
	private const val GRID_SIDE = 0.9
	private const val CHANNEL = 0xFF
	private const val LAYER_TINT = 32
}
