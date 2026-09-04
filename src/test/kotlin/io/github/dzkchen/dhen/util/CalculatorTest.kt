package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.math.BigDecimal

class CalculatorTest {
	@AfterEach
	fun clearAnswer() = Calculator.forget()

	private fun value(source: String): BigDecimal {
		val result = Calculator.evaluate(source)
		assertInstanceOf(Calculated.Value::class.java, result, source)
		return (result as Calculated.Value).amount
	}

	private fun assertValue(expected: String, source: String) {
		assertEquals(0, value(source).compareTo(BigDecimal(expected)), "$source should be $expected")
	}

	private fun assertInvalid(source: String) {
		assertInstanceOf(Calculated.Invalid::class.java, Calculator.evaluate(source), source)
	}

	@Test
	fun `suffixes multiply by their SkyBlock amounts`() {
		assertValue("1000", "1k")
		assertValue("2500000", "2.5m")
		assertValue("3000000000", "3b")
		assertValue("4000000000000", "4t")
		assertValue("1728", "27s")
		assertValue("1600", "10e")
		assertValue("5", "50 * 10%")
	}

	@Test
	fun `a trailing e is an enchanted group while a following digit is an exponent`() {
		assertValue("1500000", "1.5e6")
		assertValue("240", "1.5e")
		assertValue("15000", "1.5e4")
	}

	@Test
	fun `suffixes bind tighter than a power`() {
		assertValue("4000000", "2k^2")
	}

	@Test
	fun `arithmetic follows precedence and keeps full division precision`() {
		assertValue("3000", "(1000+500)*2")
		assertEquals(
			BigDecimal("50000000").divide(BigDecimal("1200"), Calculator.precision),
			value("50m / 1.2k")
		)
		assertValue("12", "3x4")
		assertValue("8", "2**3")
	}

	@Test
	fun `powers are right associative and unary minus applies after the power`() {
		assertValue("512", "2^3^2")
		assertValue("-4", "-2^2")
		assertValue("0.25", "2^-2")
	}

	@Test
	fun `functions round and compare`() {
		assertValue("12", "sqrt(144)")
		assertValue("3.33", "round(10/3, 2)")
		assertValue("3", "round(10/3)")
		assertValue("-7", "floor(-6.2)")
		assertValue("-6", "ceil(-6.2)")
		assertValue("6.2", "abs(-6.2)")
		assertValue("2", "min(5, 2, 9)")
		assertValue("9", "max(5, 2, 9)")
	}

	@Test
	fun `ans reads the last remembered answer and fails without one`() {
		assertInvalid("ans + 1")
		Calculator.remember(BigDecimal("40"))
		assertValue("42", "ans + 2")
	}

	@Test
	fun `half typed input reads as incomplete rather than wrong`() {
		assertInstanceOf(Calculated.Incomplete::class.java, Calculator.evaluate("10 +"))
		assertInstanceOf(Calculated.Incomplete::class.java, Calculator.evaluate("(1+2"))
		assertInstanceOf(Calculated.Incomplete::class.java, Calculator.evaluate(""))
	}

	@Test
	fun `division by zero is refused`() {
		assertInvalid("1/0")
		assertInvalid("0^-1")
	}

	@Test
	@Timeout(10)
	fun `pasted nonsense is refused instead of hanging the client`() {
		assertInvalid("9^9^9^9")
		assertInvalid("9".repeat(5000))
		assertInvalid("1".repeat(2000) + " + 1")
		assertInvalid("(".repeat(200) + "1" + ")".repeat(200))
		assertInvalid("1e99999")
		assertInvalid("2^100000")
		assertInvalid("1..2")
		assertInvalid("10 @ 2")
		assertInvalid("2 3")
	}

	@Test
	fun `display groups the whole part and keeps the asked decimals`() {
		assertEquals("41,666.67", Calculator.display(value("50m / 1.2k"), 2))
		assertEquals("1,000,000", Calculator.display(value("1m"), 2))
		assertEquals("-1,234.5", Calculator.display(value("0-1234.5"), 2))
		assertTrue(Calculator.display(value("2^300"), 0).length > 90)
	}
}
