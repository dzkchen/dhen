package io.github.dzkchen.dhen.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionRangeTest {
	@Test
	fun inclusiveAndExclusiveBoundsAreRespected() {
		val range = VersionRange.parse("[1.0,2.0)")

		assertTrue(range.contains("1.0"))
		assertTrue(range.contains("1.5"))
		assertFalse(range.contains("2.0"))
	}

	@Test
	fun exclusiveLowerAndInclusiveUpperBoundsAreRespected() {
		val range = VersionRange.parse("(1.0,2.0]")

		assertFalse(range.contains("1.0"))
		assertTrue(range.contains("1.0.1"))
		assertTrue(range.contains("2.0"))
	}

	@Test
	fun wildcardRangeContainsAnyParsedVersion() {
		val range = VersionRange.parse("*")

		assertTrue(range.contains("0.19"))
		assertTrue(range.contains("26.2"))
	}

	@Test
	fun invalidRangesAreRejected() {
		assertThrows(IllegalArgumentException::class.java) { VersionRange.parse("1.0,2.0") }
		assertThrows(IllegalArgumentException::class.java) { VersionRange.parse("[2.0,1.0)") }
		assertThrows(IllegalArgumentException::class.java) { VersionRange.parse("[,]") }
	}
}
