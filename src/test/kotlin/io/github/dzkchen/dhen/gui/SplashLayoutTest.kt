package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplashLayoutTest {
	@Test
	fun `the mark never outgrows the window and never shrinks as it widens`() {
		var previous = 0
		for (guiWidth in 1..1920) {
			val scale = SplashLayout.markScale(guiWidth)
			assertTrue(scale >= previous, "scale shrank from $previous at $guiWidth")
			assertTrue(
				SplashLayout.MARK_WIDTH * scale <= guiWidth || guiWidth < SplashLayout.MARK_WIDTH,
				"mark overflowed at $guiWidth"
			)
			previous = scale
		}
	}

	@Test
	fun `the mark keeps roughly a third of the width across gui scales`() {
		for (guiWidth in intArrayOf(320, 427, 480, 640, 960)) {
			val drawn = SplashLayout.MARK_WIDTH * SplashLayout.markScale(guiWidth)
			assertTrue(drawn * 100 / guiWidth in 20..40, "mark took ${drawn * 100 / guiWidth}% at $guiWidth")
			assertTrue(drawn <= guiWidth, "mark overflowed at $guiWidth")
		}
	}

	@Test
	fun `a narrow window still gets a whole mark`() {
		assertEquals(1, SplashLayout.markScale(1))
		assertEquals(1, SplashLayout.markScale(120))
	}

	@Test
	fun `the bar is never thinner than two pixels and grows with the mark`() {
		assertEquals(2, SplashLayout.barHeight(1))
		assertEquals(2, SplashLayout.barHeight(2))
		assertEquals(4, SplashLayout.barHeight(4))
	}

	@Test
	fun `the bar sits below the centred mark`() {
		val height = 480
		val markTop = SplashLayout.centered(height, SplashLayout.MARK_HEIGHT * 2)

		assertTrue(SplashLayout.barTop(height) > markTop + SplashLayout.MARK_HEIGHT * 2)
	}

	@Test
	fun `an unstarted timer reads as no animation at all`() {
		assertEquals(-1f, SplashLayout.animation(-1L, 5_000L, SplashLayout.FADE_OUT_MILLIS))
		assertEquals(0.5f, SplashLayout.animation(4_500L, 5_000L, SplashLayout.FADE_OUT_MILLIS))
	}

	@Test
	fun `the boot screen is opaque until vanilla starts the fade out`() {
		assertEquals(0xFF, SplashLayout.canvasAlpha(-1f, -1f, false, false))
		assertEquals(0xFF, SplashLayout.canvasAlpha(0.5f, -1f, false, false))
		assertEquals(1f, SplashLayout.contentOpacity(0.5f, -1f, false, false))
	}

	@Test
	fun `the screen fades to nothing over the second half of the fade out`() {
		assertEquals(0xFF, SplashLayout.canvasAlpha(1f, -1f, false, false))
		assertEquals(0, SplashLayout.canvasAlpha(2f, -1f, false, false))
		assertTrue(SplashLayout.canvasAlpha(1.5f, -1f, false, false) in 1..0xFE)
		assertEquals(0f, SplashLayout.contentOpacity(2f, -1f, false, false))
	}

	@Test
	fun `a reload after startup fades in from vanilla's floor`() {
		assertEquals(0x27, SplashLayout.canvasAlpha(-1f, 0f, true, false))
		assertEquals(0xFF, SplashLayout.canvasAlpha(-1f, 1f, true, false))
		assertEquals(0f, SplashLayout.contentOpacity(-1f, 0f, true, false))
	}

	@Test
	fun `the underlying screen is only extracted while the splash is see-through`() {
		assertFalse(SplashLayout.revealsUnderlay(-1f, -1f, false, false))
		assertFalse(SplashLayout.revealsUnderlay(0.5f, -1f, false, false))
		assertTrue(SplashLayout.revealsUnderlay(1f, -1f, false, false))
		assertTrue(SplashLayout.revealsUnderlay(-1f, 0.5f, true, false))
		assertFalse(SplashLayout.revealsUnderlay(-1f, 1f, true, false))
	}

	@Test
	fun `the reduced tier never pays for an underlay its opaque canvas would cover`() {
		assertFalse(SplashLayout.revealsUnderlay(1f, -1f, false, true))
		assertFalse(SplashLayout.revealsUnderlay(-1f, 0.5f, true, true))
	}

	@Test
	fun `the reduced tier animates none of Dhen's own pixels`() {
		for (fadeOut in listOf(-1f, 0f, 0.5f, 1f, 1.5f, 1.99f)) {
			assertEquals(0xFF, SplashLayout.canvasAlpha(fadeOut, -1f, false, true), "canvas moved at $fadeOut")
			assertEquals(1f, SplashLayout.contentOpacity(fadeOut, -1f, false, true), "mark moved at $fadeOut")
		}
		for (fadeIn in listOf(0f, 0.5f, 1f)) {
			assertEquals(0xFF, SplashLayout.canvasAlpha(-1f, fadeIn, true, true))
			assertEquals(1f, SplashLayout.contentOpacity(-1f, fadeIn, true, true))
		}
	}

	@Test
	fun `the reduced tier still tracks the reload instead of easing towards it`() {
		assertEquals(0.4f, SplashLayout.advanceProgress(0f, 0.4f, true))

		val eased = SplashLayout.advanceProgress(0f, 0.4f, false)

		assertTrue(eased > 0f && eased < 0.4f, "smoothing did not ease: $eased")
	}

	@Test
	fun `smoothing converges on the real progress and never overshoots`() {
		var value = 0f
		repeat(500) { value = SplashLayout.advanceProgress(value, 1f, false) }

		assertTrue(value > 0.99f && value <= 1f, "progress settled at $value")
	}

	@Test
	fun `a second reload restarts the bar instead of running it backwards slowly`() {
		assertEquals(0f, SplashLayout.advanceProgress(0.9f, 0f, false))
	}

	@Test
	fun `progress outside zero to one is clamped before it reaches the bar`() {
		assertEquals(1f, SplashLayout.advanceProgress(1f, 4f, true))
		assertEquals(0f, SplashLayout.advanceProgress(0f, -2f, true))
		assertEquals(0, SplashLayout.filledWidth(150, -0.5f))
		assertEquals(150, SplashLayout.filledWidth(150, 1.5f))
		assertEquals(75, SplashLayout.filledWidth(150, 0.5f))
	}

	@Test
	fun `the bar disappears once the screen starts handing over`() {
		assertEquals(1f, SplashLayout.barOpacity(-1f, false))
		assertEquals(0.25f, SplashLayout.barOpacity(0.75f, false))
		assertEquals(0f, SplashLayout.barOpacity(1f, false))
	}

	@Test
	fun `the reduced tier holds the whole composition still until the hard cut`() {
		for (fadeOut in listOf(-1f, 0f, 0.75f, 1f, 1.99f)) {
			assertEquals(1f, SplashLayout.barOpacity(fadeOut, true), "bar moved at $fadeOut")
		}
	}

	@Test
	fun `the overlay is handed back to vanilla only after the whole fade out`() {
		assertFalse(SplashLayout.handedOver(-1f))
		assertFalse(SplashLayout.handedOver(1.99f))
		assertTrue(SplashLayout.handedOver(2f))
	}

	@Test
	fun `a degenerate window never produces a negative extent or a crash`() {
		for (gui in intArrayOf(0, 1, 2)) {
			assertTrue(SplashLayout.markScale(gui) >= 1)
			assertTrue(SplashLayout.barTop(gui) >= 0)
			assertTrue(SplashLayout.barHeight(SplashLayout.markScale(gui)) >= 2)
		}
		assertEquals(0, SplashLayout.filledWidth(0, 1f))
	}

	@Test
	fun `a stalled overlay clamps instead of overflowing`() {
		val forever = SplashLayout.animation(0L, 1_000_000_000L, SplashLayout.FADE_OUT_MILLIS)

		assertTrue(SplashLayout.handedOver(forever))
		assertEquals(0, SplashLayout.canvasAlpha(forever, -1f, false, false))
		assertEquals(0f, SplashLayout.contentOpacity(forever, -1f, false, false))
		assertEquals(0f, SplashLayout.barOpacity(forever, false))
	}

	@Test
	fun `an explicit alpha replaces whatever the palette carried`() {
		assertEquals(0x20336699, SplashLayout.withAlpha(0xFF336699u.toInt(), 0x20))
		assertEquals(0xFF336699u.toInt(), SplashLayout.withAlpha(0x00336699, 0x1FF))
		assertEquals(0x00336699, SplashLayout.withAlpha(0xFF336699u.toInt(), -5))
	}
}
