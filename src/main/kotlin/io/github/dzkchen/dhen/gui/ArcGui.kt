package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.dzkchen.dhen.Dhen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal object ArcGui {
	const val SHADER = "annular_segment"
	const val ANGLE_SUBPIXEL = 4096f
	const val TAU = (2.0 * PI).toFloat()

	private val PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(Dhen.id("pipeline/$SHADER"))
		.withVertexShader(Dhen.id("core/$SHADER"))
		.withFragmentShader(Dhen.id("core/$SHADER"))
		.withVertexBinding(0, RoundedQuad.FORMAT)
		.build()

	fun annularSegment(
		graphics: GuiGraphicsExtractor,
		centerX: Float,
		centerY: Float,
		innerRadius: Float,
		outerRadius: Float,
		startAngle: Float,
		sweep: Float,
		color: Int
	) {
		if (!valid(centerX, centerY, innerRadius, outerRadius, startAngle, sweep) || color ushr 24 == 0) return
		val amount = abs(sweep).coerceAtMost(TAU)
		val beginning = normalized(if (sweep < 0f) startAngle + sweep else startAngle)
		graphics.guiRenderState.addGuiElement(
			Element(
				GuiPose.of(graphics.pose()),
				centerX,
				centerY,
				innerRadius,
				outerRadius,
				angleFixed(beginning),
				angleFixed(amount),
				color,
				graphics.scissorStack.peek()
			)
		)
	}

	internal fun valid(
		centerX: Float,
		centerY: Float,
		innerRadius: Float,
		outerRadius: Float,
		startAngle: Float,
		sweep: Float
	): Boolean =
		centerX.isFinite() && centerY.isFinite() && innerRadius.isFinite() && outerRadius.isFinite() &&
			startAngle.isFinite() && sweep.isFinite() &&
			innerRadius >= 0f && outerRadius > innerRadius && outerRadius <= RoundedQuad.LARGEST_EXTENT && sweep != 0f

	internal fun normalized(angle: Float): Float {
		val wrapped = angle % TAU
		return when {
			wrapped < -PI.toFloat() -> wrapped + TAU
			wrapped >= PI.toFloat() -> wrapped - TAU
			else -> wrapped
		}
	}

	internal fun angleFixed(angle: Float): Int =
		(angle * ANGLE_SUBPIXEL).roundToInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())

	private class Element(
		private val pose: Matrix3x2fc,
		centerX: Float,
		centerY: Float,
		innerRadius: Float,
		outerRadius: Float,
		private val startAngle: Int,
		private val sweep: Int,
		private val color: Int,
		private val scissor: ScreenRectangle?
	) : GuiElementRenderState {
		private val quadLeft = centerX - outerRadius - RoundedQuad.PADDING
		private val quadTop = centerY - outerRadius - RoundedQuad.PADDING
		private val quadRight = centerX + outerRadius + RoundedQuad.PADDING
		private val quadBottom = centerY + outerRadius + RoundedQuad.PADDING
		private val localRadius = outerRadius + RoundedQuad.PADDING
		private val inner = RoundedQuad.fixed(innerRadius)
		private val outer = RoundedQuad.fixed(outerRadius)
		private val area = bounds(centerX, centerY, outerRadius)

		override fun buildVertices(consumer: VertexConsumer) {
			vertex(consumer, quadLeft, quadTop, -localRadius, -localRadius)
			vertex(consumer, quadLeft, quadBottom, -localRadius, localRadius)
			vertex(consumer, quadRight, quadBottom, localRadius, localRadius)
			vertex(consumer, quadRight, quadTop, localRadius, -localRadius)
		}

		override fun pipeline(): RenderPipeline = PIPELINE

		override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

		override fun scissorArea(): ScreenRectangle? = scissor

		override fun bounds(): ScreenRectangle? = area

		private fun vertex(consumer: VertexConsumer, x: Float, y: Float, localX: Float, localY: Float) {
			consumer.addVertexWith2DPose(pose, x, y)
				.setUv(localX, localY)
				.setUv1(inner, outer)
				.setUv2(startAngle, sweep)
				.setColor(color)
		}

		private fun bounds(centerX: Float, centerY: Float, radius: Float): ScreenRectangle? {
			val left = floor(centerX - radius).toInt() - RoundedQuad.PADDING
			val top = floor(centerY - radius).toInt() - RoundedQuad.PADDING
			val right = ceil(centerX + radius).toInt() + RoundedQuad.PADDING
			val bottom = ceil(centerY + radius).toInt() + RoundedQuad.PADDING
			return GuiPose.clip(ScreenRectangle(left, top, right - left, bottom - top), pose, scissor)
		}
	}
}
