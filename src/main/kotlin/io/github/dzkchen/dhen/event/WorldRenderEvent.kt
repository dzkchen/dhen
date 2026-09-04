package io.github.dzkchen.dhen.event

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.VoxelShape

open class WorldRenderEvent internal constructor() : DeepProfiledEvent {
	private var frame: LevelRenderContext? = null

	private val context: LevelRenderContext get() = frame!!

	val collector: SubmitNodeCollector get() = context.submitNodeCollector()

	val pose: PoseStack get() = context.poseStack()

	val camera: CameraRenderState get() = context.levelState().cameraRenderState

	val gameTime: Long get() = context.levelState().gameTime

	internal fun seed(context: LevelRenderContext) {
		frame = context
	}

	internal open fun forget() {
		frame = null
	}
}

class BlockOutlineEvent internal constructor() : WorldRenderEvent() {
	private var outlined: BlockPos? = null
	private var outlineShape: VoxelShape? = null

	val pos: BlockPos get() = outlined!!

	val shape: VoxelShape get() = outlineShape!!

	var drawsVanillaOutline: Boolean = true

	internal fun seed(context: LevelRenderContext, pos: BlockPos, shape: VoxelShape) {
		seed(context)
		outlined = pos
		outlineShape = shape
		drawsVanillaOutline = true
	}

	override fun forget() {
		super.forget()
		outlined = null
		outlineShape = null
	}
}
