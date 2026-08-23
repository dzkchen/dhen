package io.github.dzkchen.dhen.event

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState

class WorldRenderEvent internal constructor() : DeepProfiledEvent {
	private var frame: LevelRenderContext? = null

	private val context: LevelRenderContext get() = frame!!

	val collector: SubmitNodeCollector get() = context.submitNodeCollector()

	val pose: PoseStack get() = context.poseStack()

	val camera: CameraRenderState get() = context.levelState().cameraRenderState

	val gameTime: Long get() = context.levelState().gameTime

	internal fun seed(context: LevelRenderContext) {
		frame = context
	}

	internal fun forget() {
		frame = null
	}
}
