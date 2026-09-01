package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DurationsTest {
	@Test
	fun `a countdown shows the two largest units that are not zero`() {
		assertEquals("4d 12h", countdown(4 * DAY + 12 * HOUR + 30 * MINUTE))
		assertEquals("1h 30m", countdown(HOUR + 30 * MINUTE))
		assertEquals("45s", countdown(45 * SECOND))
	}

	@Test
	fun `a zero unit is skipped rather than printed`() {
		assertEquals("2d 5m", countdown(2 * DAY + 5 * MINUTE))
	}

	@Test
	fun `a year is a unit of its own and only it can grow past a thousand`() {
		assertEquals("1y 40d", countdown(405 * DAY))
		assertEquals("1,000y", countdown(365_000 * DAY))
	}

	@Test
	fun `a duration string parses back into the milliseconds it names`() {
		assertEquals(3 * DAY + 17 * HOUR + 5 * MINUTE + 36 * SECOND, spanMillis("3d 17h 5m 36s"))
		assertEquals(HOUR, spanMillis("1h"))
		assertEquals(0L, spanMillis("no units here"))
	}

	@Test
	fun `anything already past reads as soon, and a sliver of a second as zero`() {
		assertEquals("Soon", countdown(0L))
		assertEquals("Soon", countdown(-1L))
		assertEquals("0s", countdown(500L))
	}

	private companion object {
		const val SECOND = 1_000L
		const val MINUTE = 60 * SECOND
		const val HOUR = 60 * MINUTE
		const val DAY = 24 * HOUR
	}
}
