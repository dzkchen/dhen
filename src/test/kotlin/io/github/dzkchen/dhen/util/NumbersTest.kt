package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
	@Test
	fun `a bare number keeps every digit it was given`() {
		assertEquals(1000L, compactNumber("1000"))
		assertEquals(0L, compactNumber("0"))
		assertEquals(1000L, compactNumber("1,000"))
		assertEquals(-100L, compactNumber("-100"))
	}

	@Test
	fun `a suffix multiplies and reads in either case`() {
		assertEquals(10_000L, compactNumber("10k"))
		assertEquals(10_000L, compactNumber("10K"))
		assertEquals(10_000_000L, compactNumber("10m"))
		assertEquals(1_000_000_000_000L, compactNumber("1t"))
		assertEquals(1_000_000_000_000_000L, compactNumber("1p"))
		assertEquals(1_000_000_000_000_000_000L, compactNumber("1e"))
	}

	@Test
	fun `a decimal part survives the suffix and truncates toward zero`() {
		assertEquals(1_500_000L, compactNumber("1.5m"))
		assertEquals(2_500_000_000L, compactNumber("2.5b"))
		assertEquals(1500L, compactNumber("1.5k"))
		assertEquals(-3000L, compactNumber("-3k"))
	}

	@Test
	fun `surrounding space is trimmed before anything else is read`() {
		assertEquals(5_000_000L, compactNumber("  5M  "))
		assertEquals(10_000_000L, compactNumber(" 10m"))
	}

	@Test
	fun `nothing readable comes back as no number at all`() {
		assertNull(compactNumber(""))
		assertNull(compactNumber("   "))
		assertNull(compactNumber("abc"))
		assertNull(compactNumber("k"))
		assertNull(compactNumber("1.5"))
	}
}
