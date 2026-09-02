package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EntityGlowEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.WorldDepth
import net.minecraft.client.Minecraft
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity

object Box3D : Module(
	name = "Box ESP",
	category = Category.VISUAL,
	description = "Replaces every glowing outline with a 3D box."
) {
	internal val modeSetting = SelectorSetting(
		"Mode",
		FILLED_OUTLINE,
		listOf(OUTLINE, FILL, FILLED_OUTLINE),
		description = "Whether the box is drawn as edges, as a solid, or both."
	)
	internal val lineWidthSetting = NumberSetting("Line Width", 2.5, 1.0, 10.0, 0.1)
		.withDependency { modeSetting.value != FILL }
	internal val outlineOpacitySetting = NumberSetting("Outline Opacity", 35.0, 0.0, 100.0, 1.0)
		.withDependency { modeSetting.value != FILL }
	internal val fillOpacitySetting = NumberSetting("Fill Opacity", 35.0, 0.0, 100.0, 1.0)
		.withDependency { modeSetting.value != OUTLINE }
	internal val phaseSetting = BooleanSetting(
		"Phase",
		true,
		description = "Draws the box through walls."
	)

	private var mode by modeSetting
	private var lineWidth by lineWidthSetting
	private var outlineOpacity by outlineOpacitySetting
	private var fillOpacity by fillOpacitySetting
	private var phase by phaseSetting

	private var glowing = arrayOfNulls<Entity>(CAPACITY)
	private var glowingColors = IntArray(CAPACITY)
	private var glowingCount = 0

	init {
		on<ClientTickEvent.End> { forget() }
		on<EntityGlowEvent>(priority = AFTER_PRODUCERS) { consume(it) }
		on<WorldRenderEvent> { render(it) }
	}

	override fun onDisabled() = forget()

	private fun consume(event: EntityGlowEvent) {
		if (!event.glowing) return
		event.glowing = false
		if (event.entity === Minecraft.getInstance().player) return
		remember(event.entity, event.color)
	}

	private fun remember(entity: Entity, color: Int) {
		if (glowingCount == glowing.size) {
			glowing = glowing.copyOf(glowingCount * 2)
			glowingColors = glowingColors.copyOf(glowingCount * 2)
		}
		glowing[glowingCount] = entity
		glowingColors[glowingCount] = color
		glowingCount++
	}

	private fun forget() {
		glowing.fill(null)
		glowingCount = 0
	}

	private fun render(event: WorldRenderEvent) {
		if (glowingCount == 0) return
		val outline = mode != FILL
		val fill = mode != OUTLINE
		val depth = if (phase) WorldDepth.THROUGH_WALLS else WorldDepth.TESTED
		val width = lineWidth.toFloat()
		val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
		var index = 0
		while (index < glowingCount) {
			val entity = glowing[index]
			if (entity != null && entity.isAlive) {
				val color = glowingColors[index]
				EntityHighlights.drawEntityBox(
					event,
					entity,
					partialTick,
					INFLATE,
					ARGB.color(opacity(outlineOpacity), color),
					ARGB.color(opacity(fillOpacity), color),
					outline,
					fill,
					width,
					depth
				)
			}
			index++
		}
		forget()
	}

	private fun opacity(percent: Double): Int = (percent * FULL_ALPHA / PERCENT).toInt()

	private const val OUTLINE = "Outline"
	private const val FILL = "Fill"
	private const val FILLED_OUTLINE = "Filled Outline"
	private const val CAPACITY = 64
	private const val INFLATE = 0.1
	private const val FULL_ALPHA = 255.0
	private const val PERCENT = 100.0
}
