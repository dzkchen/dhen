package io.github.dzkchen.dhen.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal fun cameraRelative(world: Double, camera: Double): Float = (world - camera).toFloat()

internal fun worldLineSpan(dx: Float, dy: Float, dz: Float): Float = sqrt(dx * dx + dy * dy + dz * dz)

internal fun worldTextDisplayMode(depth: WorldDepth): Font.DisplayMode = when (depth) {
	WorldDepth.TESTED -> Font.DisplayMode.NORMAL
	WorldDepth.THROUGH_WALLS -> Font.DisplayMode.SEE_THROUGH
}

internal fun worldBeaconRadiusScale(
	centerX: Double,
	centerZ: Double,
	cameraX: Double,
	cameraZ: Double,
	isScoping: Boolean
): Float {
	if (isScoping) return 1f
	val dx = cameraX - centerX
	val dz = cameraZ - centerZ
	return max(1f, (sqrt(dx * dx + dz * dz) / 96.0).toFloat())
}

internal fun worldQuadRightX(yaw: Float): Float = cos(Math.toRadians(yaw.toDouble())).toFloat()

internal fun worldQuadRightZ(yaw: Float): Float = sin(Math.toRadians(yaw.toDouble())).toFloat()

internal inline fun <T> withRestoredWorldPose(pose: PoseStack, block: PoseStack.() -> T): T {
	val parent = pose.last()
	pose.pushPose()
	return try {
		pose.block()
	} finally {
		while (pose.last() !== parent) pose.popPose()
	}
}

internal object WorldDraw {
	private const val DEFAULT_LINE_WIDTH = 3f
	private const val DEGREES_TO_RADIANS = Math.PI.toFloat() / 180f
	private const val TRACER_DROP = 0.2f
	private const val WORLD_TEXT_SCALE = 0.025f
	private const val BEAM_PERIOD = 40L
	private const val CIRCLE_SEGMENTS = 64
	private const val CIRCLE_STEP = 2.0 * Math.PI / CIRCLE_SEGMENTS

	private val wireEdges = intArrayOf(
		0, 1, 2, 3, 4, 5, 6, 7,
		0, 2, 1, 3, 4, 6, 5, 7,
		0, 4, 1, 5, 2, 6, 3, 7
	)

	private val fillCorners = intArrayOf(
		0, 4, 6, 2, 5, 1, 3, 7,
		0, 2, 3, 1, 5, 7, 6, 4,
		0, 1, 5, 4, 6, 7, 3, 2
	)

	fun drawLine(
		event: WorldRenderEvent,
		from: Vec3,
		to: Vec3,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) {
		val camera = event.camera.pos
		drawLine(
			event,
			cameraRelative(from.x, camera.x),
			cameraRelative(from.y, camera.y),
			cameraRelative(from.z, camera.z),
			cameraRelative(to.x, camera.x),
			cameraRelative(to.y, camera.y),
			cameraRelative(to.z, camera.z),
			color,
			width,
			depth
		)
	}

	fun drawTracer(
		event: WorldRenderEvent,
		to: Vec3,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH
	) = drawTracer(event, to.x, to.y, to.z, color, width)

	fun drawWireCircle(
		event: WorldRenderEvent,
		center: Vec3,
		radius: Double,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawWireCircle(event, center.x, center.y, center.z, radius, color, width, depth)

	fun drawWireCircle(
		event: WorldRenderEvent,
		centerX: Double,
		centerY: Double,
		centerZ: Double,
		radius: Double,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) {
		if (!radius.isFinite() || radius <= 0.0) return
		val camera = event.camera.pos
		val x = cameraRelative(centerX, camera.x)
		val y = cameraRelative(centerY, camera.y)
		val z = cameraRelative(centerZ, camera.z)
		val span = radius.toFloat()
		val renderType = WorldRenderTypes.line(depth, color)
		event.collector.submitCustomGeometry(event.pose, renderType) { _, buffer ->
			var segment = 0
			while (segment < CIRCLE_SEGMENTS) {
				val from = CIRCLE_STEP * segment
				val to = CIRCLE_STEP * (segment + 1)
				val fromX = x + span * cos(from).toFloat()
				val fromZ = z + span * sin(from).toFloat()
				val toX = x + span * cos(to).toFloat()
				val toZ = z + span * sin(to).toFloat()
				val dx = toX - fromX
				val dz = toZ - fromZ
				val edge = worldLineSpan(dx, 0f, dz)
				if (edge > 0f) line(buffer, fromX, y, fromZ, toX, y, toZ, dx / edge, 0f, dz / edge, color, width)
				segment++
			}
		}
	}

	fun drawTracer(
		event: WorldRenderEvent,
		toX: Double,
		toY: Double,
		toZ: Double,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH
	) {
		val camera = event.camera
		val yaw = -camera.yRot * DEGREES_TO_RADIANS - Math.PI.toFloat()
		val pitch = -camera.xRot * DEGREES_TO_RADIANS
		val horizontal = -Mth.cos(pitch.toDouble())
		val target = camera.pos
		drawLine(
			event,
			Mth.sin(yaw.toDouble()) * horizontal,
			Mth.sin(pitch.toDouble()) - TRACER_DROP,
			Mth.cos(yaw.toDouble()) * horizontal,
			cameraRelative(toX, target.x),
			cameraRelative(toY, target.y),
			cameraRelative(toZ, target.z),
			color,
			width,
			WorldDepth.THROUGH_WALLS
		)
	}

	fun drawBox(
		event: WorldRenderEvent,
		bounds: AABB,
		outlineColor: Int,
		fillColor: Int = outlineColor,
		outline: Boolean = true,
		fill: Boolean = true,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) {
		if (fill) drawFilledBox(event, bounds, fillColor, depth)
		if (outline) drawWireBox(event, bounds, outlineColor, width, depth)
	}

	fun drawBox(
		event: WorldRenderEvent,
		pos: BlockPos,
		outlineColor: Int,
		fillColor: Int = outlineColor,
		outline: Boolean = true,
		fill: Boolean = true,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) {
		if (fill) drawFilledBox(event, pos, fillColor, depth)
		if (outline) drawWireBox(event, pos, outlineColor, width, depth)
	}

	fun drawBox(
		event: WorldRenderEvent,
		minX: Double,
		minY: Double,
		minZ: Double,
		maxX: Double,
		maxY: Double,
		maxZ: Double,
		outlineColor: Int,
		fillColor: Int,
		outline: Boolean,
		fill: Boolean,
		width: Float,
		depth: WorldDepth
	) {
		if (fill) drawFilledBox(event, minX, minY, minZ, maxX, maxY, maxZ, fillColor, depth)
		if (outline) drawWireBox(event, minX, minY, minZ, maxX, maxY, maxZ, outlineColor, width, depth)
	}

	fun drawWireBox(
		event: WorldRenderEvent,
		bounds: AABB,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawWireBox(
		event,
		bounds.minX,
		bounds.minY,
		bounds.minZ,
		bounds.maxX,
		bounds.maxY,
		bounds.maxZ,
		color,
		width,
		depth
	)

	fun drawWireBox(
		event: WorldRenderEvent,
		pos: BlockPos,
		color: Int,
		width: Float = DEFAULT_LINE_WIDTH,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawWireBox(
		event,
		pos.x.toDouble(),
		pos.y.toDouble(),
		pos.z.toDouble(),
		pos.x + 1.0,
		pos.y + 1.0,
		pos.z + 1.0,
		color,
		width,
		depth
	)

	fun drawFilledBox(
		event: WorldRenderEvent,
		bounds: AABB,
		color: Int,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawFilledBox(
		event,
		bounds.minX,
		bounds.minY,
		bounds.minZ,
		bounds.maxX,
		bounds.maxY,
		bounds.maxZ,
		color,
		depth
	)

	fun drawText(
		event: WorldRenderEvent,
		text: String,
		pos: Vec3,
		color: Int,
		scale: Float = 1f,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawTextInternal(event, null, text, pos.x, pos.y, pos.z, color, scale, depth)

	fun drawText(
		event: WorldRenderEvent,
		memo: TextMemo,
		text: String,
		pos: Vec3,
		color: Int,
		scale: Float = 1f,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawTextInternal(event, memo, text, pos.x, pos.y, pos.z, color, scale, depth)

	fun drawText(
		event: WorldRenderEvent,
		memo: TextMemo,
		text: String,
		x: Double,
		y: Double,
		z: Double,
		color: Int,
		scale: Float = 1f,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawTextInternal(event, memo, text, x, y, z, color, scale, depth)

	fun drawBeaconBeam(
		event: WorldRenderEvent,
		pos: BlockPos,
		color: Int,
		height: Int = BeaconRenderer.MAX_RENDER_Y
	) {
		val camera = event.camera.pos
		val client = Minecraft.getInstance()
		val radiusScale = worldBeaconRadiusScale(
			pos.x + 0.5,
			pos.z + 0.5,
			camera.x,
			camera.z,
			client.player?.isScoping == true
		)
		val animationTime = Math.floorMod(event.gameTime, BEAM_PERIOD).toFloat() +
			client.deltaTracker.getGameTimeDeltaPartialTick(false)
		withRestoredWorldPose(event.pose) {
			translate(
				cameraRelative(pos.x.toDouble(), camera.x),
				cameraRelative(pos.y.toDouble(), camera.y),
				cameraRelative(pos.z.toDouble(), camera.z)
			)
			BeaconRenderer.submitBeaconBeam(
				this,
				event.collector,
				BeaconRenderer.BEAM_LOCATION,
				1f,
				animationTime,
				0,
				height,
				color,
				BeaconRenderer.SOLID_BEAM_RADIUS * radiusScale,
				BeaconRenderer.BEAM_GLOW_RADIUS * radiusScale
			)
		}
	}

	fun drawTexturedQuad(
		event: WorldRenderEvent,
		texture: Identifier,
		pos: Vec3,
		width: Float,
		height: Float,
		yaw: Float,
		color: Int
	) {
		if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
		val camera = event.camera.pos
		val x = cameraRelative(pos.x, camera.x)
		val y = cameraRelative(pos.y, camera.y)
		val z = cameraRelative(pos.z, camera.z)
		val halfWidth = width * 0.5f
		val halfHeight = height * 0.5f
		val rightX = worldQuadRightX(yaw)
		val rightZ = worldQuadRightZ(yaw)
		event.collector.submitCustomGeometry(event.pose, RenderTypes.entityCutout(texture)) { _, buffer ->
			quadVertex(buffer, x - rightX * halfWidth, y - halfHeight, z - rightZ * halfWidth, 0f, 1f, -rightZ, rightX, color)
			quadVertex(buffer, x - rightX * halfWidth, y + halfHeight, z - rightZ * halfWidth, 0f, 0f, -rightZ, rightX, color)
			quadVertex(buffer, x + rightX * halfWidth, y + halfHeight, z + rightZ * halfWidth, 1f, 0f, -rightZ, rightX, color)
			quadVertex(buffer, x + rightX * halfWidth, y - halfHeight, z + rightZ * halfWidth, 1f, 1f, -rightZ, rightX, color)
		}
	}

	private fun drawTextInternal(
		event: WorldRenderEvent,
		memo: TextMemo?,
		text: String,
		x: Double,
		y: Double,
		z: Double,
		color: Int,
		scale: Float,
		depth: WorldDepth
	) {
		if (!scale.isFinite() || scale <= 0f) return
		val font = Minecraft.getInstance().font
		val left = -(memo?.width(font, text) ?: DhenType.width(font, text)) / 2f
		val camera = event.camera
		val textScale = WORLD_TEXT_SCALE * scale
		val displayMode = worldTextDisplayMode(depth)
		withRestoredWorldPose(event.pose) {
			translate(
				cameraRelative(x, camera.pos.x),
				cameraRelative(y, camera.pos.y),
				cameraRelative(z, camera.pos.z)
			)
			mulPose(camera.orientation)
			scale(textScale, -textScale, textScale)
			if (memo == null) {
				DhenType.shadowedWorldText(
					event.collector,
					this,
					text,
					left,
					0f,
					color,
					displayMode,
					LightCoordsUtil.FULL_BRIGHT
				)
			} else {
				memo.shadowedWorldText(
					event.collector,
					this,
					text,
					left,
					0f,
					color,
					displayMode,
					LightCoordsUtil.FULL_BRIGHT
				)
			}
		}
	}

	fun drawFilledBox(
		event: WorldRenderEvent,
		pos: BlockPos,
		color: Int,
		depth: WorldDepth = WorldDepth.TESTED
	) = drawFilledBox(
		event,
		pos.x.toDouble(),
		pos.y.toDouble(),
		pos.z.toDouble(),
		pos.x + 1.0,
		pos.y + 1.0,
		pos.z + 1.0,
		color,
		depth
	)

	private fun drawLine(
		event: WorldRenderEvent,
		x0: Float,
		y0: Float,
		z0: Float,
		x1: Float,
		y1: Float,
		z1: Float,
		color: Int,
		width: Float,
		depth: WorldDepth
	) {
		val dx = x1 - x0
		val dy = y1 - y0
		val dz = z1 - z0
		val span = worldLineSpan(dx, dy, dz)
		if (span <= 0f) return
		val normalX = dx / span
		val normalY = dy / span
		val normalZ = dz / span
		val renderType = WorldRenderTypes.line(depth, color)
		event.collector.submitCustomGeometry(event.pose, renderType) { _, buffer ->
			line(buffer, x0, y0, z0, x1, y1, z1, normalX, normalY, normalZ, color, width)
		}
	}

	fun drawWireBox(
		event: WorldRenderEvent,
		minX: Double,
		minY: Double,
		minZ: Double,
		maxX: Double,
		maxY: Double,
		maxZ: Double,
		color: Int,
		width: Float,
		depth: WorldDepth
	) {
		val camera = event.camera.pos
		val x0 = cameraRelative(minX, camera.x)
		val y0 = cameraRelative(minY, camera.y)
		val z0 = cameraRelative(minZ, camera.z)
		val x1 = cameraRelative(maxX, camera.x)
		val y1 = cameraRelative(maxY, camera.y)
		val z1 = cameraRelative(maxZ, camera.z)
		val renderType = WorldRenderTypes.line(depth, color)
		event.collector.submitCustomGeometry(event.pose, renderType) { _, buffer ->
			var edge = 0
			while (edge < wireEdges.size) {
				val from = wireEdges[edge]
				val to = wireEdges[edge + 1]
				val fromX = axis(x0, x1, from, 0)
				val fromY = axis(y0, y1, from, 1)
				val fromZ = axis(z0, z1, from, 2)
				val toX = axis(x0, x1, to, 0)
				val toY = axis(y0, y1, to, 1)
				val toZ = axis(z0, z1, to, 2)
				val dx = toX - fromX
				val dy = toY - fromY
				val dz = toZ - fromZ
				val span = worldLineSpan(dx, dy, dz)
				if (span > 0f) line(buffer, fromX, fromY, fromZ, toX, toY, toZ, dx / span, dy / span, dz / span, color, width)
				edge += 2
			}
		}
	}

	fun drawFilledBox(
		event: WorldRenderEvent,
		minX: Double,
		minY: Double,
		minZ: Double,
		maxX: Double,
		maxY: Double,
		maxZ: Double,
		color: Int,
		depth: WorldDepth
	) {
		val camera = event.camera.pos
		val x0 = cameraRelative(minX, camera.x)
		val y0 = cameraRelative(minY, camera.y)
		val z0 = cameraRelative(minZ, camera.z)
		val x1 = cameraRelative(maxX, camera.x)
		val y1 = cameraRelative(maxY, camera.y)
		val z1 = cameraRelative(maxZ, camera.z)
		val renderType = WorldRenderTypes.fill(depth)
		event.collector.submitCustomGeometry(event.pose, renderType) { _, buffer ->
			for (corner in fillCorners) {
				buffer.addVertex(
					axis(x0, x1, corner, 0),
					axis(y0, y1, corner, 1),
					axis(z0, z1, corner, 2)
				).setColor(color)
			}
		}
	}

	private fun axis(low: Float, high: Float, corner: Int, shift: Int): Float =
		if (corner shr shift and 1 == 0) low else high

	private fun line(
		buffer: VertexConsumer,
		x0: Float,
		y0: Float,
		z0: Float,
		x1: Float,
		y1: Float,
		z1: Float,
		normalX: Float,
		normalY: Float,
		normalZ: Float,
		color: Int,
		width: Float
	) {
		buffer.addVertex(x0, y0, z0).setColor(color).setNormal(normalX, normalY, normalZ).setLineWidth(width)
		buffer.addVertex(x1, y1, z1).setColor(color).setNormal(normalX, normalY, normalZ).setLineWidth(width)
	}

	private fun quadVertex(
		buffer: VertexConsumer,
		x: Float,
		y: Float,
		z: Float,
		u: Float,
		v: Float,
		normalX: Float,
		normalZ: Float,
		color: Int
	) {
		buffer.addVertex(x, y, z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(normalX, 0f, normalZ)
	}
}
