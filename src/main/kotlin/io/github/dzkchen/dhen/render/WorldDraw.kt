package io.github.dzkchen.dhen.render

import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.dzkchen.dhen.event.WorldRenderEvent
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal fun cameraRelative(world: Double, camera: Double): Float = (world - camera).toFloat()

internal fun worldLineSpan(dx: Float, dy: Float, dz: Float): Float = sqrt(dx * dx + dy * dy + dz * dz)

internal object WorldDraw {
	private const val DEFAULT_LINE_WIDTH = 3f
	private const val DEGREES_TO_RADIANS = Math.PI.toFloat() / 180f
	private const val TRACER_DROP = 0.2f

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
			cameraRelative(to.x, target.x),
			cameraRelative(to.y, target.y),
			cameraRelative(to.z, target.z),
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

	private fun drawWireBox(
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

	private fun drawFilledBox(
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
}
