package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.GpuFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class ArcGuiTest {
	@Test
	fun `the arc rejects degenerate and unrepresentable inputs`() {
		assertTrue(ArcGui.valid(12f, 12f, 0f, 8f, 0f, 1f))
		assertFalse(ArcGui.valid(Float.NaN, 12f, 4f, 8f, 0f, 1f))
		assertFalse(ArcGui.valid(12f, Float.POSITIVE_INFINITY, 4f, 8f, 0f, 1f))
		assertFalse(ArcGui.valid(12f, 12f, -1f, 8f, 0f, 1f))
		assertFalse(ArcGui.valid(12f, 12f, 8f, 8f, 0f, 1f))
		assertFalse(ArcGui.valid(12f, 12f, 4f, RoundedQuad.LARGEST_EXTENT + 1f, 0f, 1f))
		assertFalse(ArcGui.valid(12f, 12f, 4f, 8f, Float.NaN, 1f))
		assertFalse(ArcGui.valid(12f, 12f, 4f, 8f, 0f, 0f))
	}

	@Test
	fun `angles wrap without changing their direction`() {
		assertEquals(-PI.toFloat(), ArcGui.normalized(PI.toFloat()))
		assertEquals(-PI.toFloat() / 2f, ArcGui.normalized(PI.toFloat() * 1.5f), 0.0001f)
		assertEquals(PI.toFloat() / 2f, ArcGui.normalized(-PI.toFloat() * 1.5f), 0.0001f)
		assertEquals(4096, ArcGui.angleFixed(1f))
		assertEquals(25736, ArcGui.angleFixed(ArcGui.TAU))
	}

	@Test
	fun `the shader contract matches the packed quad`() {
		val vertex = source("vsh")
		val fragment = source("fsh")
		val attributes = RoundedQuad.FORMAT.elements.associate { it.name() to it.format() }

		assertEquals(ATTRIBUTES, attributes)
		assertEquals(declarations(vertex, "out"), declarations(fragment, "in"))
		assertEquals(ArcGui.ANGLE_SUBPIXEL, constant(vertex, "ANGLE_SUBPIXEL"))
		assertEquals(ArcGui.TAU, constant(fragment, "TAU"), 0.000001f)
		assertTrue(vertex.startsWith("#version 330"))
		assertTrue(fragment.startsWith("#version 330"))
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
		val path = "assets/dhen/shaders/core/${ArcGui.SHADER}.$stage"
		val stream = javaClass.classLoader.getResourceAsStream(path)
		assertNotNull(stream, "missing bundled shader: $path")
		return stream!!.use { String(it.readBytes(), Charsets.UTF_8) }
	}

	private companion object {
		val ATTRIBUTES = mapOf(
			"Position" to GpuFormat.RGB32_FLOAT,
			"UV0" to GpuFormat.RG32_FLOAT,
			"UV1" to GpuFormat.RG16_SINT,
			"UV2" to GpuFormat.RG16_SINT,
			"Color" to GpuFormat.RGBA8_UNORM
		)
	}
}
