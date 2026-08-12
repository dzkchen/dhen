package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.GpuFormat
import org.joml.Matrix3x2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoundedQuadTest {
	@Test
	fun `a radius or border never exceeds the shape it is drawn on`() {
		assertEquals(6f, RoundedQuad.clamped(20f, 8f, 6f))
		assertEquals(8f, RoundedQuad.clamped(20f, 8f, 40f))
		assertEquals(8f, RoundedQuad.clamped(20f, 8f, RoundedQuad.FULL))
		assertEquals(0f, RoundedQuad.clamped(20f, 8f, -3f))
	}

	@Test
	fun `a degenerate shape clamps to nothing instead of throwing`() {
		assertEquals(0f, RoundedQuad.clamped(-4f, 8f, 2f))
		assertEquals(0f, RoundedQuad.clamped(0f, 0f, RoundedQuad.FULL))
	}

	@Test
	fun `fixed point keeps its sub-pixel step and saturates instead of wrapping`() {
		assertEquals(8, RoundedQuad.fixed(1f))
		assertEquals(1, RoundedQuad.fixed(1f / RoundedQuad.SUBPIXEL))
		assertEquals(4, RoundedQuad.fixed(0.5f))
		assertEquals(32767, RoundedQuad.fixed(RoundedQuad.LARGEST_EXTENT * 2f))
		assertEquals(-32767, RoundedQuad.fixed(-RoundedQuad.LARGEST_EXTENT * 2f))
	}

	@Test
	fun `the packed extent covers any shape a real display can show`() {
		assertTrue(RoundedQuad.LARGEST_EXTENT * 2f >= 7680f, "a shape must span an 8K screen at GUI scale 1")
	}

	@Test
	fun `a track edge runs from empty to full and survives a degenerate fraction`() {
		assertEquals(10, RoundedQuad.between(10, 50, 0f))
		assertEquals(50, RoundedQuad.between(10, 50, 1f))
		assertEquals(30, RoundedQuad.between(10, 50, 0.5f))
		assertEquals(10, RoundedQuad.between(10, 50, -2f))
		assertEquals(50, RoundedQuad.between(10, 50, 9f))
		assertEquals(10, RoundedQuad.between(10, 50, Float.NaN))
	}

	@Test
	fun `only an untransformed pose may share the one cached matrix`() {
		assertTrue(RoundedQuad.isUntransformed(Matrix3x2f()))
		assertFalse(RoundedQuad.isUntransformed(Matrix3x2f().translate(0f, 3f)))
		assertFalse(RoundedQuad.isUntransformed(Matrix3x2f().scale(2f, 2f)))
	}

	@Test
	fun `the quad layout carries every value the shader reads`() {
		val layout = RoundedQuad.FORMAT.elements.map { it.name() to it.format() }

		assertEquals(ATTRIBUTES.map { it.name to it.format }, layout)
		assertEquals(32, RoundedQuad.FORMAT.vertexSize)
	}

	@Test
	fun `the vertex shader declares the attributes the quad supplies`() {
		assertEquals(ATTRIBUTES.associate { it.name to it.glsl }, declarations(source("vsh"), "in"))
	}

	@Test
	fun `the fragment stage reads exactly what the vertex stage passes it`() {
		assertEquals(declarations(source("vsh"), "out"), declarations(source("fsh"), "in"))
	}

	@Test
	fun `the shader agrees with Kotlin on the constants they both encode`() {
		assertEquals(RoundedQuad.SUBPIXEL, constant(source("vsh"), "SUBPIXEL"))
		assertEquals(RoundedQuad.PADDING.toFloat(), constant(source("fsh"), "PADDING"))
	}

	@Test
	fun `both shader stages target the vanilla GLSL version`() {
		for (stage in listOf("vsh", "fsh")) {
			assertTrue(source(stage).startsWith("#version 330"), "$stage must match the vanilla core shaders")
		}
	}

	private fun declarations(source: String, direction: String): Map<String, String> =
		Regex("""^(?:flat )?$direction (\w+) (\w+);""", RegexOption.MULTILINE)
			.findAll(source)
			.associate { it.groupValues[2] to it.groupValues[1] }

	private fun constant(source: String, name: String): Float {
		val declared = Regex("""^const float $name = ([\d.]+);""", RegexOption.MULTILINE).find(source)
		assertNotNull(declared, "the shader must declare $name")
		return declared!!.groupValues[1].toFloat()
	}

	private fun source(stage: String): String {
		val path = "assets/dhen/shaders/core/${RoundedQuad.SHADER}.$stage"
		val stream = javaClass.classLoader.getResourceAsStream(path)
		assertNotNull(stream, "missing bundled shader: $path")
		return stream!!.use { String(it.readBytes(), Charsets.UTF_8) }
	}

	private data class Attribute(val name: String, val format: GpuFormat, val glsl: String)

	private companion object {
		val ATTRIBUTES = listOf(
			Attribute("Position", GpuFormat.RGB32_FLOAT, "vec3"),
			Attribute("UV0", GpuFormat.RG32_FLOAT, "vec2"),
			Attribute("UV1", GpuFormat.RG16_SINT, "ivec2"),
			Attribute("UV2", GpuFormat.RG16_SINT, "ivec2"),
			Attribute("Color", GpuFormat.RGBA8_UNORM, "vec4")
		)
	}
}
