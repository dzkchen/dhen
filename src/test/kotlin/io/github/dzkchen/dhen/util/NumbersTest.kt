package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NumbersTest {
	@Test
	fun `anything under a thousand keeps every digit`() {
		assertEquals("0", shortNumber(0L))
		assertEquals("7", shortNumber(7L))
		assertEquals("999", shortNumber(999L))
	}

	@Test
	fun `a tenth is shown only while the whole part is small enough to read`() {
		assertEquals("1k", shortNumber(1_000L))
		assertEquals("1.2k", shortNumber(1_234L))
		assertEquals("12k", shortNumber(12_345L))
		assertEquals("1.2M", shortNumber(1_234_567L))
		assertEquals("123M", shortNumber(123_456_789L))
		assertEquals("1.2B", shortNumber(1_234_567_890L))
	}

	@Test
	fun `a trailing zero tenth is dropped rather than printed`() {
		assertEquals("2M", shortNumber(2_000_000L))
		assertEquals("5B", shortNumber(5_000_000_000L))
	}

	@Test
	fun `the largest suffix carries everything above it`() {
		assertEquals("1.5T", shortNumber(1_500_000_000_000L))
		assertEquals("1500T", shortNumber(1_500_000_000_000_000L))
	}

	@Test
	fun `a negative reads as its own magnitude and the smallest long cannot loop`() {
		assertEquals("-1.2k", shortNumber(-1_234L))
		assertEquals("-9223372T", shortNumber(Long.MIN_VALUE))
	}
}
