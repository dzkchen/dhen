package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlassGuiTest {
	@BeforeEach
	@AfterEach
	fun restoreDefault() {
		Effects.reduced = false
	}

	@Test
	@Suppress("KotlinMisorderedAssertEqualsArguments")
	fun `reduced effects resolve every surface to its opaque flat color`() {
		Effects.reduced = true

		assertEquals(DhenPalette.CANVAS, GlassGui.canvas())
		assertEquals(DhenPalette.SURFACE, GlassGui.surface())
		assertEquals(DhenPalette.SURFACE_RAISED, GlassGui.raised())
		assertEquals(DhenPalette.SURFACE_INTERACTIVE, GlassGui.interactive())
	}

	@Test
	fun `the glass tier resolves the same surfaces to translucent colors`() {
		val glass = intArrayOf(GlassGui.canvas(), GlassGui.surface(), GlassGui.raised(), GlassGui.interactive())

		assertTrue(glass.all { it ushr 24 in 1..0xFE }, "glass surfaces must be translucent, not opaque or invisible")
		assertEquals(glass.size, glass.distinct().size)
	}

	@Test
	fun `entry progress settles immediately when effects are reduced`() {
		Effects.reduced = true

		assertEquals(GlassGui.SETTLED, GlassGui.entryProgress(Long.MIN_VALUE))
	}

	@Test
	fun `progress runs from zero to settled over the entry window`() {
		assertEquals(0f, entryTween(0L))
		assertEquals(0f, entryTween(-50L))
		assertEquals(GlassGui.SETTLED, entryTween(GlassGui.ENTRY_MILLIS))
		assertEquals(GlassGui.SETTLED, entryTween(GlassGui.ENTRY_MILLIS * 4))

		var previous = 0f
		for (elapsed in 0..GlassGui.ENTRY_MILLIS) {
			val current = entryTween(elapsed)
			assertTrue(current >= previous, "progress must not move backwards at $elapsed ms")
			previous = current
		}
	}

	private fun entryTween(elapsed: Long): Float =
		GlassGui.tween(0f, GlassGui.SETTLED, elapsed, GlassGui.ENTRY_MILLIS)

	@Test
	fun `a tween eases from where it was to where it is going and then holds`() {
		assertEquals(0.25f, GlassGui.tween(0.25f, 1f, 0L, TWEEN_MILLIS))
		assertEquals(0.875f, GlassGui.tween(0f, 1f, TWEEN_MILLIS / 2, TWEEN_MILLIS))
		assertEquals(1f, GlassGui.tween(0f, 1f, TWEEN_MILLIS, TWEEN_MILLIS))
		assertEquals(1f, GlassGui.tween(0f, 1f, TWEEN_MILLIS * 10, TWEEN_MILLIS))
		assertEquals(0f, GlassGui.tween(1f, 0f, TWEEN_MILLIS, TWEEN_MILLIS))
	}

	@Test
	fun `easing decelerates so the entry settles rather than snapping`() {
		assertEquals(0f, GlassGui.ease(0f))
		assertEquals(GlassGui.SETTLED, GlassGui.ease(1f))
		assertTrue(GlassGui.ease(0.5f) > 0.5f)
	}

	@Test
	fun `an offset collapses to nothing once settled, whatever distance it covers`() {
		assertEquals(GlassGui.ENTRY_RISE, GlassGui.offset(0f, GlassGui.ENTRY_RISE))
		assertEquals(GlassGui.TAB_SLIDE, GlassGui.offset(0f, GlassGui.TAB_SLIDE))
		assertEquals(-GlassGui.TAB_SLIDE, GlassGui.offset(0f, -GlassGui.TAB_SLIDE))
		assertEquals(0f, GlassGui.offset(GlassGui.SETTLED, GlassGui.ENTRY_RISE), EXACT)
		assertEquals(0f, GlassGui.offset(GlassGui.SETTLED, GlassGui.TAB_SLIDE), EXACT)
		assertEquals(0f, GlassGui.offset(GlassGui.SETTLED, -GlassGui.TAB_SLIDE), EXACT)
	}

	@Test
	@Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
	fun `the tab transition is quicker than the entry`() {
		assertTrue(GlassGui.TAB_MILLIS < GlassGui.ENTRY_MILLIS)
	}

	@Test
	fun `both transitions settle instantly and without motion when effects are reduced`() {
		Effects.reduced = true

		assertEquals(GlassGui.SETTLED, GlassGui.tabProgress(Long.MAX_VALUE))
		assertEquals(GlassGui.SETTLED, GlassGui.entryProgress(Long.MAX_VALUE))
		assertEquals(0f, GlassGui.offset(GlassGui.tabProgress(Long.MAX_VALUE), GlassGui.TAB_SLIDE), EXACT)
		assertEquals(0f, GlassGui.offset(GlassGui.entryProgress(Long.MAX_VALUE), GlassGui.ENTRY_RISE), EXACT)
	}

	@Test
	fun `the sheen clears the corners it is given and survives the pill sentinel`() {
		assertEquals(0, GlassGui.sheenInset(width = 240, height = 22, radius = 0f))
		assertEquals(3, GlassGui.sheenInset(width = 240, height = 22, radius = 6f))
		assertEquals(5, GlassGui.sheenInset(width = 240, height = 22, radius = RoundedQuad.FULL))
		assertEquals(0, GlassGui.sheenInset(width = 0, height = 0, radius = RoundedQuad.FULL))
		assertEquals(0, GlassGui.sheenInset(width = -20, height = -20, radius = RoundedQuad.FULL))
	}

	@Test
	fun `alpha scaling keeps the color channels and clamps the alpha`() {
		val color = 0x80336699u.toInt()

		assertEquals(0x40336699u.toInt(), GlassGui.scaleAlpha(color, 0.5f))
		assertEquals(0x00336699, GlassGui.scaleAlpha(color, 0f))
		assertEquals(0xFF336699u.toInt(), GlassGui.scaleAlpha(color, 4f))
		assertEquals(0x00336699, GlassGui.scaleAlpha(color, -1f))
	}

	private companion object {
		const val TWEEN_MILLIS = 100L
		const val EXACT = 0f
	}
}
