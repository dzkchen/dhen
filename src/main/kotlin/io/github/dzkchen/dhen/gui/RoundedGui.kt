package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.dzkchen.dhen.Dhen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc

internal object RoundedGui {
	private val PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(Dhen.id("pipeline/${RoundedQuad.SHADER}"))
		.withVertexShader(Dhen.id("core/${RoundedQuad.SHADER}"))
		.withFragmentShader(Dhen.id("core/${RoundedQuad.SHADER}"))
		.withVertexBinding(0, RoundedQuad.FORMAT)
		.build()

	private val UNTRANSFORMED_POSE: Matrix3x2fc = Matrix3x2f()

	const val HAIRLINE = 1f
	private const val OPAQUE = 0xFF

	fun fill(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		color: Int
	) = draw(graphics, left, top, right, bottom, radius, 0f, color)

	fun border(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		thickness: Float,
		color: Int
	) {
		if (thickness <= 0f) return
		draw(graphics, left, top, right, bottom, radius, thickness, color)
	}

	fun frame(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		fillColor: Int,
		borderColor: Int
	) {
		fill(graphics, left, top, right, bottom, radius, fillColor)
		border(graphics, left, top, right, bottom, radius, HAIRLINE, borderColor)
	}

	fun pill(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int, color: Int) =
		fill(graphics, left, top, right, bottom, RoundedQuad.FULL, color)

	fun pillFrame(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		fillColor: Int,
		borderColor: Int
	) = frame(graphics, left, top, right, bottom, RoundedQuad.FULL, fillColor, borderColor)

	fun pillBorder(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		thickness: Float,
		color: Int
	) = border(graphics, left, top, right, bottom, RoundedQuad.FULL, thickness, color)

	fun circle(graphics: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Int, color: Int) =
		fill(graphics, centerX - radius, centerY - radius, centerX + radius, centerY + radius, RoundedQuad.FULL, color)

	fun capsuleTrack(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		progress: Float,
		trackColor: Int,
		fillColor: Int
	): Int {
		val edge = RoundedQuad.trackEdge(left, right, progress)
		if (edge < right || fillColor ushr 24 != OPAQUE) pill(graphics, left, top, right, bottom, trackColor)
		pill(graphics, left, top, edge, bottom, fillColor)
		return edge
	}

	private fun draw(
		graphics: GuiGraphicsExtractor,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		border: Float,
		color: Int
	) {
		if (left >= right || top >= bottom || color ushr 24 == 0) return
		graphics.guiRenderState.addGuiElement(
			Element(pose(graphics.pose()), left, top, right, bottom, radius, border, color, graphics.scissorStack.peek())
		)
	}

	private fun pose(stack: Matrix3x2fc): Matrix3x2fc =
		if (RoundedQuad.isUntransformed(stack)) UNTRANSFORMED_POSE else Matrix3x2f(stack)

	private class Element(
		private val pose: Matrix3x2fc,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		radius: Float,
		border: Float,
		private val color: Int,
		private val scissor: ScreenRectangle?
	) : GuiElementRenderState {
		private val halfWidth = (right - left) * 0.5f
		private val halfHeight = (bottom - top) * 0.5f
		private val quadLeft = (left - RoundedQuad.PADDING).toFloat()
		private val quadTop = (top - RoundedQuad.PADDING).toFloat()
		private val quadRight = (right + RoundedQuad.PADDING).toFloat()
		private val quadBottom = (bottom + RoundedQuad.PADDING).toFloat()
		private val localX = halfWidth + RoundedQuad.PADDING
		private val localY = halfHeight + RoundedQuad.PADDING
		private val extentX = RoundedQuad.fixed(halfWidth)
		private val extentY = RoundedQuad.fixed(halfHeight)
		private val cornerRadius = RoundedQuad.fixed(RoundedQuad.clamped(halfWidth, halfHeight, radius))
		private val borderWidth = RoundedQuad.fixed(RoundedQuad.clamped(halfWidth, halfHeight, border))
		private val area = paddedBounds(left, top, right, bottom)

		override fun buildVertices(consumer: VertexConsumer) {
			vertex(consumer, quadLeft, quadTop, -localX, -localY)
			vertex(consumer, quadLeft, quadBottom, -localX, localY)
			vertex(consumer, quadRight, quadBottom, localX, localY)
			vertex(consumer, quadRight, quadTop, localX, -localY)
		}

		override fun pipeline(): RenderPipeline = PIPELINE

		override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

		override fun scissorArea(): ScreenRectangle? = scissor

		override fun bounds(): ScreenRectangle? = area

		private fun vertex(consumer: VertexConsumer, x: Float, y: Float, offsetX: Float, offsetY: Float) {
			consumer.addVertexWith2DPose(pose, x, y)
				.setUv(offsetX, offsetY)
				.setUv1(extentX, extentY)
				.setUv2(cornerRadius, borderWidth)
				.setColor(color)
		}

		private fun paddedBounds(left: Int, top: Int, right: Int, bottom: Int): ScreenRectangle? {
			val padded = ScreenRectangle(
				left - RoundedQuad.PADDING,
				top - RoundedQuad.PADDING,
				right - left + 2 * RoundedQuad.PADDING,
				bottom - top + 2 * RoundedQuad.PADDING
			).transformMaxBounds(pose)
			return scissor?.intersection(padded) ?: padded
		}
	}
}
