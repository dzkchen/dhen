package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FailsafeTest {
	@Test
	fun `the success path returns the block result and leaves the latch open`() {
		val failsafe = Failsafe()

		assertEquals("value", failsafe.guard("label") { "value" })
		assertFalse(failsafe.failed)
	}

	@Test
	fun `a throwing block does not propagate and closes the latch`() {
		val failsafe = Failsafe()

		assertNull(failsafe.guard<Unit>("label") { throw RuntimeException("boom") })
		assertTrue(failsafe.failed)
	}

	@Test
	fun `an error is contained the same way an exception is`() {
		val failsafe = Failsafe()

		assertNull(failsafe.guard<Unit>("label") { throw StackOverflowError() })
		assertTrue(failsafe.failed)
	}

	@Test
	fun `a latched guard never enters its block again`() {
		val failsafe = Failsafe()
		var runs = 0

		failsafe.guard<Unit>("label") { throw RuntimeException("boom") }
		repeat(3) { assertNull(failsafe.guard("label") { runs++ }) }

		assertEquals(0, runs)
		assertTrue(failsafe.failed)
	}
}
