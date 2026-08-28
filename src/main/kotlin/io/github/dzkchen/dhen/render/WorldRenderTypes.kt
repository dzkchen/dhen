package io.github.dzkchen.dhen.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import io.github.dzkchen.dhen.Dhen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import java.util.Optional

internal enum class WorldDepth { TESTED, THROUGH_WALLS }

internal enum class WorldLineLayer { OPAQUE, TRANSLUCENT, THROUGH_WALLS }

internal enum class WorldFillLayer { TESTED, THROUGH_WALLS }

internal fun worldLineLayer(depth: WorldDepth, color: Int): WorldLineLayer = when {
	depth == WorldDepth.THROUGH_WALLS -> WorldLineLayer.THROUGH_WALLS
	color ushr 24 == 255 -> WorldLineLayer.OPAQUE
	else -> WorldLineLayer.TRANSLUCENT
}

internal fun worldFillLayer(depth: WorldDepth): WorldFillLayer = when (depth) {
	WorldDepth.TESTED -> WorldFillLayer.TESTED
	WorldDepth.THROUGH_WALLS -> WorldFillLayer.THROUGH_WALLS
}

internal object WorldRenderTypes {
	private val linesThroughWallsPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(Dhen.id("pipeline/lines_through_walls"))
		.withDepthStencilState(Optional.empty())
		.build()

	private val filledThroughWallsPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(Dhen.id("pipeline/filled_through_walls"))
		.withDepthStencilState(Optional.empty())
		.build()

	val linesThroughWalls: RenderType = RenderType.create(
		"dhen_lines_through_walls",
		RenderSetup.builder(linesThroughWallsPipeline)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup()
	)

	val filledThroughWalls: RenderType = RenderType.create(
		"dhen_filled_through_walls",
		RenderSetup.builder(filledThroughWallsPipeline)
			.sortOnUpload()
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup()
	)

	init {
		IrisCompat.assignOnce(linesThroughWallsPipeline, IrisShaderProgram.LINES)
		IrisCompat.assignOnce(filledThroughWallsPipeline, IrisShaderProgram.BASIC)
	}

	fun initialize() = Unit

	fun line(depth: WorldDepth, color: Int): RenderType = when (worldLineLayer(depth, color)) {
		WorldLineLayer.OPAQUE -> RenderTypes.LINES
		WorldLineLayer.TRANSLUCENT -> RenderTypes.LINES_TRANSLUCENT
		WorldLineLayer.THROUGH_WALLS -> linesThroughWalls
	}

	fun fill(depth: WorldDepth): RenderType = when (worldFillLayer(depth)) {
		WorldFillLayer.TESTED -> RenderTypes.debugFilledBox()
		WorldFillLayer.THROUGH_WALLS -> filledThroughWalls
	}
}
