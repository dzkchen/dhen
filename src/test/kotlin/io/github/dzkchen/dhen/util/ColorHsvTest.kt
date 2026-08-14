package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorHsvTest {
	@Test
	fun `every sampled colour survives a trip through hsv and back`() {
		for (red in SWEEP) {
			for (green in SWEEP) {
				for (blue in SWEEP) {
					val original = Color.rgba(red, green, blue)
					assertRoundTrip(original)
				}
			}
		}
	}

	@Test
	fun `every grey survives, keeps hue zero, and reports no saturation`() {
		for (level in 0..0xFF) {
			val grey = Color.rgba(level, level, level)
			assertEquals(0f, grey.hue)
			assertEquals(0f, grey.saturation)
			assertEquals(level / 255f, grey.brightness)
			assertRoundTrip(grey)
		}
	}

	@Test
	fun `every fully saturated hue survives at every brightness`() {
		for (step in 0 until 360) {
			for (level in BRIGHTNESS_LEVELS) {
				assertRoundTrip(Color.hsv(step / 360f, 1f, level))
			}
		}
	}

	@Test
	fun `the six corners of the wheel sit where the sector maths says they do`() {
		assertEquals(0f, Color.rgba(255, 0, 0).hue)
		assertEquals(1f / 6f, Color.rgba(255, 255, 0).hue)
		assertEquals(2f / 6f, Color.rgba(0, 255, 0).hue)
		assertEquals(3f / 6f, Color.rgba(0, 255, 255).hue)
		assertEquals(4f / 6f, Color.rgba(0, 0, 255).hue)
		assertEquals(5f / 6f, Color.rgba(255, 0, 255).hue)
	}

	@Test
	fun `alpha rides through untouched`() {
		for (alpha in intArrayOf(0x00, 0x01, 0x80, 0xFE, 0xFF)) {
			val built = Color.hsv(0.75f, 0.5f, 0.5f, alpha)
			assertEquals(alpha, built.alpha)
			assertEquals(Color.hsv(0.75f, 0.5f, 0.5f).rgb, built.rgb)
		}
	}

	@Test
	fun `a hue outside one turn wraps instead of clamping`() {
		val quarter = Color.hsv(0.25f, 1f, 1f)
		assertEquals(quarter, Color.hsv(1.25f, 1f, 1f))
		assertEquals(quarter, Color.hsv(-2.75f, 1f, 1f))
		assertEquals(Color.hsv(0.75f, 1f, 1f), Color.hsv(-0.25f, 1f, 1f))
	}

	@Test
	fun `saturation and brightness clamp instead of wrapping`() {
		assertEquals(Color.hsv(0.5f, 1f, 1f), Color.hsv(0.5f, 4f, 2f))
		assertEquals(Color.hsv(0.5f, 0f, 0f), Color.hsv(0.5f, -3f, -1f))
	}

	@Test
	fun `black and white are the two ends of brightness at no saturation`() {
		val black = Color.hsv(0.3f, 1f, 0f)
		assertEquals(Color.rgba(0, 0, 0), black)
		assertEquals(0f, black.saturation)
		assertEquals(0f, black.brightness)

		val white = Color.hsv(0.3f, 0f, 1f)
		assertEquals(Color.rgba(255, 255, 255), white)
		assertEquals(0f, white.saturation)
		assertEquals(1f, white.brightness)
	}

	@Test
	fun `a half-saturated mid-brightness colour reads back its own components`() {
		val built = Color.hsv(2f / 6f, 0.5f, 0.8f)
		assertEquals(Color.rgba(102, 204, 102), built)
		assertEquals(2f / 6f, built.hue)
		assertEquals(0.5f, built.saturation)
		assertEquals(0.8f, built.brightness)
	}

	private fun assertRoundTrip(original: Color) {
		val again = Color.hsv(original.hue, original.saturation, original.brightness, original.alpha)
		assertEquals(original, again) {
			"#%08X went through hue=%f saturation=%f brightness=%f and came back #%08X".format(
				original.argb, original.hue, original.saturation, original.brightness, again.argb
			)
		}
	}

	private companion object {
		val SWEEP = intArrayOf(0, 1, 2, 17, 63, 64, 100, 127, 128, 129, 200, 253, 254, 255)
		val BRIGHTNESS_LEVELS = floatArrayOf(0f, 0.01f, 0.25f, 0.5f, 0.75f, 0.99f, 1f)
	}
}
