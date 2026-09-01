package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.util.NanoClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class QuiverWarningTest {
	private var now = 0L
	private val amounts = IntArray(QuiverArrow.entries.size)
	private val warning = QuiverWarning(NanoClock { now }) { amounts[it.ordinal] }

	@Test
	fun `threshold is inclusive and disabled or missing arrows do not alert`() {
		assertFalse(warning.updated(null, 0, false, true, 100))
		assertFalse(warning.updated(QuiverArrow.NONE, 0, false, true, 100))
		assertFalse(warning.updated(QuiverArrow.FLINT, 101, false, true, 100))
		assertFalse(warning.updated(QuiverArrow.FLINT, 100, false, false, 100))
		assertTrue(warning.updated(QuiverArrow.FLINT, 100, false, true, 100))
	}

	@Test
	fun `same-state low updates alert once inside thirty seconds and again at the boundary`() {
		assertTrue(warning.updated(QuiverArrow.FLINT, 100, false, true, 100))
		now += 29.seconds.inWholeNanoseconds
		assertFalse(warning.updated(QuiverArrow.FLINT, 100, false, true, 100))
		now += 1.seconds.inWholeNanoseconds
		assertTrue(warning.updated(QuiverArrow.FLINT, 100, false, true, 100))
	}

	@Test
	fun `instance completion retains only used arrow types that are still low`() {
		amounts[QuiverArrow.FLINT.ordinal] = 100
		amounts[QuiverArrow.ICY.ordinal] = 101
		amounts[QuiverArrow.MAGMA.ordinal] = 50
		warning.updated(QuiverArrow.FLINT, 100, true, false, 100)
		warning.updated(QuiverArrow.ICY, 101, true, false, 100)
		warning.updated(QuiverArrow.MAGMA, 50, false, false, 100)

		assertTrue(warning.completed(true, 100))
		assertEquals("Flint Arrow", warning.takeReminder())
		assertNull(warning.takeReminder())
		assertFalse(warning.used(QuiverArrow.FLINT))
		assertFalse(warning.used(QuiverArrow.ICY))
	}

	@Test
	fun `instance reminder names multiple low types naturally and clears the completed set`() {
		amounts[QuiverArrow.FLINT.ordinal] = 50
		amounts[QuiverArrow.ICY.ordinal] = 50
		amounts[QuiverArrow.MAGMA.ordinal] = 50
		warning.updated(QuiverArrow.FLINT, 50, true, false, 100)
		warning.updated(QuiverArrow.ICY, 50, true, false, 100)
		warning.updated(QuiverArrow.MAGMA, 50, true, false, 100)

		assertTrue(warning.completed(true, 100))
		assertEquals("Flint Arrow, Icy Arrow, and Magma Arrow", warning.takeReminder())
		assertFalse(warning.completed(true, 100))
		assertNull(warning.takeReminder())
	}

	@Test
	fun `disabled completion and reset clear retained instance and cooldown state`() {
		amounts[QuiverArrow.FLINT.ordinal] = 50
		warning.updated(QuiverArrow.FLINT, 50, true, true, 100)
		assertTrue(warning.used(QuiverArrow.FLINT))
		assertFalse(warning.completed(false, 100))
		assertNull(warning.takeReminder())
		assertFalse(warning.updated(QuiverArrow.FLINT, 50, false, true, 100))

		warning.updated(QuiverArrow.ICY, 50, true, false, 100)
		warning.reset()
		assertFalse(warning.used(QuiverArrow.ICY))
		assertTrue(warning.updated(QuiverArrow.FLINT, 50, false, true, 100))
	}
}
