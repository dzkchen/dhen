package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.VertexFormat
import org.joml.Matrix3x2fc
import kotlin.math.min
import kotlin.math.roundToInt

internal object RoundedQuad {
	const val SHADER = "rounded_rect"
	const val SUBPIXEL = 8f
	const val PADDING = 1
	const val FULL = Float.MAX_VALUE

	private const val FIXED_LIMIT = 32767

	val LARGEST_EXTENT = FIXED_LIMIT / SUBPIXEL

	val FORMAT: VertexFormat = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("UV2", GpuFormat.RG16_SINT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.build()

	fun clamped(halfWidth: Float, halfHeight: Float, pixels: Float): Float =
		pixels.coerceIn(0f, min(halfWidth, halfHeight).coerceAtLeast(0f))

	fun fixed(pixels: Float): Int = (pixels * SUBPIXEL).roundToInt().coerceIn(-FIXED_LIMIT, FIXED_LIMIT)

	fun trackEdge(left: Int, right: Int, progress: Float): Int {
		val settled = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
		return left + ((right - left) * settled).roundToInt()
	}

	fun isUntransformed(pose: Matrix3x2fc): Boolean =
		pose.m00() == 1f && pose.m01() == 0f &&
			pose.m10() == 0f && pose.m11() == 1f &&
			pose.m20() == 0f && pose.m21() == 0f
}
