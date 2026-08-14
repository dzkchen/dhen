package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc

internal object GradientGui {
	fun horizontal(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		leftColor: Int,
		rightColor: Int
	) = quad(graphics, left, top, right, bottom, leftColor, rightColor, rightColor, leftColor)

	fun vertical(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		topColor: Int,
		bottomColor: Int
	) = quad(graphics, left, top, right, bottom, topColor, topColor, bottomColor, bottomColor)

	fun quad(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		topLeft: Int,
		topRight: Int,
		bottomRight: Int,
		bottomLeft: Int
	) {
		if (left >= right || top >= bottom) return
		if ((topLeft or topRight or bottomRight or bottomLeft) ushr 24 == 0) return
		graphics.guiRenderState.addGuiElement(
			Element(
				GuiPose.of(graphics.pose()),
				left, top, right, bottom,
				topLeft, topRight, bottomRight, bottomLeft,
				graphics.scissorStack.peek()
			)
		)
	}

	private class Element(
		private val pose: Matrix3x2fc,
		private val left: Int,
		private val top: Int,
		private val right: Int,
		private val bottom: Int,
		private val topLeft: Int,
		private val topRight: Int,
		private val bottomRight: Int,
		private val bottomLeft: Int,
		private val scissor: ScreenRectangle?
	) : GuiElementRenderState {
		private val area = GuiPose.clip(ScreenRectangle(left, top, right - left, bottom - top), pose, scissor)

		override fun buildVertices(consumer: VertexConsumer) {
			vertex(consumer, left, top, topLeft)
			vertex(consumer, left, bottom, bottomLeft)
			vertex(consumer, right, bottom, bottomRight)
			vertex(consumer, right, top, topRight)
		}

		override fun pipeline(): RenderPipeline = RenderPipelines.GUI

		override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

		override fun scissorArea(): ScreenRectangle? = scissor

		override fun bounds(): ScreenRectangle? = area

		private fun vertex(consumer: VertexConsumer, x: Int, y: Int, color: Int) {
			consumer.addVertexWith2DPose(pose, x.toFloat(), y.toFloat()).setColor(color)
		}
	}
}
