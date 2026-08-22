package io.github.dzkchen.dhen.diagnostic

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.render.IrisCompat
import io.github.dzkchen.dhen.render.IrisShaderProgram
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.phys.Vec3
import java.util.Optional
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

internal object WorldRenderProbe {
	private const val REACH = 6.0
	private const val SPREAD = 4.0
	private const val FILL_OFFSET = 2.0
	private const val BEAM_OFFSET = -2.0
	private const val TRACER_DROP = 0.2
	private const val LABEL_HEIGHT = 2.4
	private const val LABEL_SCALE = 0.025f
	private const val LINE_WIDTH = 3f
	private const val BEAM_HEIGHT = 64
	private const val BEAM_PERIOD = 40L
	private const val QUAD_LIFT = 2.0
	private const val QUAD_HALF = 0.5f
	private const val QUAD_TOWARD_CAMERA = 180.0

	private val LINES_THROUGH_WALLS: RenderPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(Dhen.id("pipeline/lines_through_walls"))
		.withDepthStencilState(Optional.empty())
		.build()

	private val FILLED_THROUGH_WALLS: RenderPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(Dhen.id("pipeline/filled_through_walls"))
		.withDepthStencilState(Optional.empty())
		.build()

	init {
		IrisCompat.assignOnce(LINES_THROUGH_WALLS, IrisShaderProgram.LINES)
		IrisCompat.assignOnce(FILLED_THROUGH_WALLS, IrisShaderProgram.BASIC)
	}

	private val THROUGH_WALL_LINES: RenderType = RenderType.create(
		"dhen_lines_through_walls",
		RenderSetup.builder(LINES_THROUGH_WALLS)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup()
	)

	private val THROUGH_WALL_FILL: RenderType = RenderType.create(
		"dhen_filled_through_walls",
		RenderSetup.builder(FILLED_THROUGH_WALLS)
			.sortOnUpload()
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup()
	)

	private val WIRE_EDGES = intArrayOf(
		0, 1, 2, 3, 4, 5, 6, 7,
		0, 2, 1, 3, 4, 6, 5, 7,
		0, 4, 1, 5, 2, 6, 3, 7
	)

	private val FILL_CORNERS = intArrayOf(
		0, 4, 6, 2, 5, 1, 3, 7,
		0, 2, 3, 1, 5, 7, 6, 4,
		0, 1, 5, 4, 6, 7, 3, 2
	)

	private lateinit var bus: EventBus
	private var subscription: Handle? = null

	fun install(bus: EventBus) {
		this.bus = bus
	}

	fun toggle(): Boolean {
		val current = subscription
		subscription = if (current == null) {
			bus.subscribe<WorldRenderEvent> {
				draw(it.collector, it.pose, it.camera, it.gameTime, -SPREAD, "collect submits")
			}
		} else {
			current.unsubscribe()
			null
		}
		return subscription != null
	}

	fun afterTranslucentTerrain(context: LevelRenderContext) {
		if (subscription == null) return
		val level = context.levelState()
		draw(
			context.submitNodeCollector(),
			context.poseStack(),
			level.cameraRenderState,
			level.gameTime,
			SPREAD,
			"after translucent terrain"
		)
	}

	private fun draw(
		collector: SubmitNodeCollector,
		pose: PoseStack,
		camera: CameraRenderState,
		gameTime: Long,
		sideways: Double,
		stage: String
	) {
		val wire = blockCorner(camera, sideways)
		val filled = blockCorner(camera, sideways + FILL_OFFSET)

		wireBox(collector, pose, wire, DhenPalette.accent)
		filledBox(collector, pose, filled, translucentAccent())
		texturedQuad(collector, pose, camera, filled.add(0.5, QUAD_LIFT, 0.5))
		tracer(collector, pose, camera, wire.add(0.5, 0.5, 0.5), DhenPalette.accentMuted)
		beacon(collector, pose, blockCorner(camera, sideways + BEAM_OFFSET), gameTime)
		label(collector, pose, camera, wire.add(0.5, LABEL_HEIGHT, 0.5), stage)
	}

	private fun blockCorner(camera: CameraRenderState, sideways: Double): Vec3 {
		val ahead = Vec3.directionFromRotation(0f, camera.yRot)
		val side = Vec3.directionFromRotation(0f, camera.yRot + 90f)
		return Vec3(
			floor(camera.pos.x + ahead.x * REACH + side.x * sideways) - camera.pos.x,
			floor(camera.pos.y) - 1.0 - camera.pos.y,
			floor(camera.pos.z + ahead.z * REACH + side.z * sideways) - camera.pos.z
		)
	}

	private fun translucentAccent(): Int = DhenPalette.mix(DhenPalette.accent, DhenPalette.GLASS_SHEEN, 0.5f)

	private fun wireBox(collector: SubmitNodeCollector, pose: PoseStack, min: Vec3, color: Int) {
		collector.submitCustomGeometry(pose, RenderTypes.LINES) { _, buffer ->
			var edge = 0
			while (edge < WIRE_EDGES.size) {
				val from = WIRE_EDGES[edge]
				val to = WIRE_EDGES[edge + 1]
				line(
					buffer,
					axis(min.x, from, 0), axis(min.y, from, 1), axis(min.z, from, 2),
					axis(min.x, to, 0), axis(min.y, to, 1), axis(min.z, to, 2),
					color
				)
				edge += 2
			}
		}
	}

	private fun filledBox(collector: SubmitNodeCollector, pose: PoseStack, min: Vec3, color: Int) {
		collector.submitCustomGeometry(pose, THROUGH_WALL_FILL) { _, buffer ->
			for (index in FILL_CORNERS) {
				buffer.addVertex(axis(min.x, index, 0), axis(min.y, index, 1), axis(min.z, index, 2)).setColor(color)
			}
		}
	}

	private fun axis(base: Double, corner: Int, shift: Int): Float = (base + (corner shr shift and 1)).toFloat()

	private fun line(
		buffer: VertexConsumer,
		x0: Float, y0: Float, z0: Float,
		x1: Float, y1: Float, z1: Float,
		color: Int
	) {
		val span = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0))
		val nx = (x1 - x0) / span
		val ny = (y1 - y0) / span
		val nz = (z1 - z0) / span
		buffer.addVertex(x0, y0, z0).setColor(color).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH)
		buffer.addVertex(x1, y1, z1).setColor(color).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH)
	}

	private fun tracer(collector: SubmitNodeCollector, pose: PoseStack, camera: CameraRenderState, to: Vec3, color: Int) {
		val from = Vec3.directionFromRotation(camera.xRot, camera.yRot).subtract(0.0, TRACER_DROP, 0.0)
		collector.submitCustomGeometry(pose, THROUGH_WALL_LINES) { _, buffer ->
			line(
				buffer,
				from.x.toFloat(), from.y.toFloat(), from.z.toFloat(),
				to.x.toFloat(), to.y.toFloat(), to.z.toFloat(),
				color
			)
		}
	}

	private fun texturedQuad(collector: SubmitNodeCollector, pose: PoseStack, camera: CameraRenderState, at: Vec3) {
		val facing = Math.toRadians(camera.yRot + QUAD_TOWARD_CAMERA)
		val rightX = cos(facing).toFloat()
		val rightZ = sin(facing).toFloat()
		val x = at.x.toFloat()
		val y = at.y.toFloat()
		val z = at.z.toFloat()
		val color = DhenPalette.accent
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BeaconRenderer.BEAM_LOCATION)) { _, buffer ->
			quadVertex(buffer, x - rightX * QUAD_HALF, y - QUAD_HALF, z - rightZ * QUAD_HALF, 0f, 1f, -rightZ, rightX, color)
			quadVertex(buffer, x - rightX * QUAD_HALF, y + QUAD_HALF, z - rightZ * QUAD_HALF, 0f, 0f, -rightZ, rightX, color)
			quadVertex(buffer, x + rightX * QUAD_HALF, y + QUAD_HALF, z + rightZ * QUAD_HALF, 1f, 0f, -rightZ, rightX, color)
			quadVertex(buffer, x + rightX * QUAD_HALF, y - QUAD_HALF, z + rightZ * QUAD_HALF, 1f, 1f, -rightZ, rightX, color)
		}
	}

	private fun quadVertex(
		buffer: VertexConsumer,
		x: Float, y: Float, z: Float,
		u: Float, v: Float,
		normalX: Float, normalZ: Float,
		color: Int
	) {
		buffer.addVertex(x, y, z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(normalX, 0f, normalZ)
	}

	private fun beacon(collector: SubmitNodeCollector, pose: PoseStack, at: Vec3, gameTime: Long) {
		val client = Minecraft.getInstance()
		val spin = Math.floorMod(gameTime, BEAM_PERIOD) + client.deltaTracker.getGameTimeDeltaPartialTick(false)
		pose.pushPose()
		pose.translate(at)
		BeaconRenderer.submitBeaconBeam(
			pose,
			collector,
			BeaconRenderer.BEAM_LOCATION,
			1f,
			spin,
			0,
			BEAM_HEIGHT,
			DhenPalette.accent,
			BeaconRenderer.SOLID_BEAM_RADIUS,
			BeaconRenderer.BEAM_GLOW_RADIUS
		)
		pose.popPose()
	}

	private fun label(collector: SubmitNodeCollector, pose: PoseStack, camera: CameraRenderState, at: Vec3, text: String) {
		val font: Font = Minecraft.getInstance().font
		pose.pushPose()
		pose.translate(at)
		pose.mulPose(camera.orientation)
		pose.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE)
		collector.submitText(
			pose,
			-DhenType.width(font, text) / 2f,
			0f,
			DhenType.styled(text).visualOrderText,
			true,
			Font.DisplayMode.SEE_THROUGH,
			LightCoordsUtil.FULL_BRIGHT,
			DhenPalette.TEXT_ON_WORLD,
			0,
			0
		)
		pose.popPose()
	}
}
