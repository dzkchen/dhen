package io.github.dzkchen.dhen.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

sealed interface Calculated {
	class Value(val amount: BigDecimal) : Calculated

	class Incomplete(val message: String) : Calculated

	class Invalid(val message: String) : Calculated
}

object Calculator {
	val precision: MathContext = MathContext(50, RoundingMode.HALF_EVEN)

	var answer: BigDecimal? = null
		private set

	fun remember(value: BigDecimal) {
		answer = value
	}

	fun forget() {
		answer = null
	}

	fun evaluate(source: String): Calculated = try {
		if (source.length > MAX_SOURCE_LENGTH) stop(TOO_LONG)
		val value = Parser(Tokenizer(source).tokenize()).parse()
		checkMagnitude(value)
		Calculated.Value(value)
	} catch (stop: Stop) {
		if (stop.incomplete) Calculated.Incomplete(stop.reason) else Calculated.Invalid(stop.reason)
	}

	fun display(value: BigDecimal, decimals: Int): String {
		val rounded = value.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
		val plain = if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
		val dot = plain.indexOf('.')
		val whole = if (dot < 0) plain else plain.substring(0, dot)
		val negative = whole.startsWith("-")
		val digits = if (negative) whole.substring(1) else whole
		val grouped = StringBuilder(digits.length + digits.length / GROUPING)
		for (place in digits.indices) {
			if (place > 0 && (digits.length - place) % GROUPING == 0) grouped.append(',')
			grouped.append(digits[place])
		}
		return (if (negative) "-" else "") + grouped + (if (dot < 0) "" else plain.substring(dot))
	}

	private fun checkMagnitude(value: BigDecimal) {
		if (value.signum() == 0) return
		val exponent = value.precision().toLong() - value.scale() - 1L
		if (kotlin.math.abs(exponent) > MAX_RESULT_EXPONENT || kotlin.math.abs(value.scale().toLong()) > MAX_RESULT_EXPONENT) {
			stop(TOO_LARGE)
		}
	}

	private fun stop(reason: String): Nothing = throw Stop(reason, false)

	private fun unfinished(reason: String): Nothing = throw Stop(reason, true)

	private class Stop(val reason: String, val incomplete: Boolean) : Exception(reason, null, false, false)

	private enum class Kind { NUMBER, IDENTIFIER, OPERATOR, POSTFIX, LEFT, RIGHT, COMMA, END }

	private class Token(val kind: Kind, val text: String)

	private class Tokenizer(private val source: String) {
		private val tokens = ArrayList<Token>()
		private var position = 0

		fun tokenize(): List<Token> {
			while (position < source.length) {
				val character = source[position]
				when {
					character.isWhitespace() -> position++
					character.isDigit() || character == '.' -> readNumber()
					isSuffix(character) && endsValue() -> {
						position++
						add(Kind.POSTFIX, character.lowercaseChar().toString())
					}

					(character == 'x' || character == 'X') && endsValue() -> {
						position++
						add(Kind.OPERATOR, TIMES_LETTER)
					}

					character.isLetter() || character == '_' -> readWord()
					else -> readSymbol(character)
				}
			}
			add(Kind.END, "")
			return tokens
		}

		private fun readSymbol(character: Char) {
			position++
			when (character) {
				'+', '-', '/', '^' -> add(Kind.OPERATOR, character.toString())
				'*' -> if (position < source.length && source[position] == '*') {
					position++
					add(Kind.OPERATOR, "^")
				} else {
					add(Kind.OPERATOR, "*")
				}

				'(' -> add(Kind.LEFT, "(")
				')' -> add(Kind.RIGHT, ")")
				',' -> add(Kind.COMMA, ",")
				'%' -> add(Kind.POSTFIX, "%")
				else -> stop("Dhen does not understand '$character'")
			}
		}

		private fun readNumber() {
			val start = position
			var digits = 0
			var decimalPoint = false
			if (source[position] == '.') {
				decimalPoint = true
				position++
				if (position == source.length || !source[position].isDigit()) stop(BAD_NUMBER)
			}
			while (position < source.length && source[position].isDigit()) {
				position++
				digits++
			}
			if (position < source.length && source[position] == '.') {
				if (decimalPoint) stop(BAD_NUMBER)
				position++
				while (position < source.length && source[position].isDigit()) {
					position++
					digits++
				}
			}
			if (digits > MAX_LITERAL_DIGITS) stop("That number has too many digits")
			readExponent()
			add(Kind.NUMBER, source.substring(start, position))
		}

		private fun readExponent() {
			if (position >= source.length) return
			if (source[position] != 'e' && source[position] != 'E') return
			var probe = position + 1
			if (probe < source.length && (source[probe] == '+' || source[probe] == '-')) probe++
			val digitsStart = probe
			while (probe < source.length && source[probe].isDigit()) probe++
			if (probe == digitsStart) return
			var exponent = 0
			for (index in digitsStart until probe) {
				val digit = source[index] - '0'
				if (exponent > (MAX_SCIENTIFIC_EXPONENT - digit) / 10) stop("That exponent is too large")
				exponent = exponent * 10 + digit
			}
			position = probe
		}

		private fun readWord() {
			val start = position++
			while (position < source.length && (source[position].isLetterOrDigit() || source[position] == '_')) position++
			val text = source.substring(start, position)
			when {
				text.length == 1 && isSuffix(text[0]) -> add(Kind.POSTFIX, text.lowercase(Locale.ROOT))
				text.equals(TIMES_LETTER, ignoreCase = true) -> add(Kind.OPERATOR, TIMES_LETTER)
				else -> add(Kind.IDENTIFIER, text)
			}
		}

		private fun endsValue(): Boolean = when (tokens.lastOrNull()?.kind) {
			Kind.NUMBER, Kind.IDENTIFIER, Kind.POSTFIX, Kind.RIGHT -> true
			else -> false
		}

		private fun add(kind: Kind, text: String) {
			tokens.add(Token(kind, text))
		}

		private fun isSuffix(character: Char): Boolean = SUFFIXES.indexOf(character.lowercaseChar()) >= 0
	}

	private class Parser(private val tokens: List<Token>) {
		private var index = 0
		private var nesting = 0

		fun parse(): BigDecimal {
			if (peek().kind == Kind.END) unfinished(UNFINISHED)
			val value = addition()
			val trailing = peek()
			when (trailing.kind) {
				Kind.END -> return value
				Kind.LEFT, Kind.NUMBER, Kind.IDENTIFIER -> stop("Write the × between two values")
				else -> stop("Dhen does not expect '${trailing.text}' here")
			}
		}

		private fun addition(): BigDecimal {
			var value = multiplication()
			while (isOperator("+") || isOperator("-")) {
				val plus = take().text == "+"
				if (peek().kind == Kind.END) unfinished(NEEDS_VALUE)
				val right = multiplication()
				value = if (plus) value.add(right, precision) else value.subtract(right, precision)
			}
			return value
		}

		private fun multiplication(): BigDecimal {
			var value = unary()
			while (isOperator("*") || isOperator("/") || isOperator(TIMES_LETTER)) {
				val divide = take().text == "/"
				val right = required()
				if (divide) {
					if (right.signum() == 0) stop(BY_ZERO)
					value = value.divide(right, precision)
				} else {
					value = value.multiply(right, precision)
				}
			}
			return value
		}

		private fun unary(): BigDecimal {
			if (isOperator("+")) {
				take()
				return required()
			}
			if (isOperator("-")) {
				take()
				return required().negate(precision)
			}
			return power()
		}

		private fun power(): BigDecimal {
			val value = postfix()
			if (!isOperator("^")) return value
			take()
			return raise(value, required())
		}

		private fun postfix(): BigDecimal {
			var value = primary()
			while (peek().kind == Kind.POSTFIX) {
				value = when (val suffix = take().text) {
					"k" -> value.multiply(THOUSAND, precision)
					"m" -> value.multiply(MILLION, precision)
					"b" -> value.multiply(BILLION, precision)
					"t" -> value.multiply(TRILLION, precision)
					"s" -> value.multiply(STACK, precision)
					"e" -> value.multiply(ENCHANTED_STACK, precision)
					"%" -> value.divide(HUNDRED, precision)
					else -> stop(BAD_NUMBER)
				}
			}
			return value
		}

		private fun primary(): BigDecimal {
			val token = peek()
			return when (token.kind) {
				Kind.NUMBER -> {
					take()
					try {
						BigDecimal(token.text)
					} catch (_: NumberFormatException) {
						stop(BAD_NUMBER)
					}
				}

				Kind.IDENTIFIER -> named(take())
				Kind.LEFT -> group()
				Kind.END -> unfinished(NEEDS_VALUE)
				else -> stop(NEEDS_VALUE)
			}
		}

		private fun named(token: Token): BigDecimal {
			if (token.text.equals(ANSWER, ignoreCase = true)) {
				return answer ?: stop("There is no earlier answer to use yet")
			}
			val function = token.text.lowercase(Locale.ROOT)
			if (function !in FUNCTIONS) stop("Dhen does not know '${token.text}'")
			return apply(function, arguments(function))
		}

		private fun group(): BigDecimal {
			take()
			enter()
			try {
				if (peek().kind == Kind.RIGHT) stop("The brackets are empty")
				val value = addition()
				if (peek().kind == Kind.END) unfinished(NEEDS_CLOSING)
				if (peek().kind != Kind.RIGHT) stop(NEEDS_CLOSING)
				take()
				return value
			} finally {
				nesting--
			}
		}

		private fun arguments(function: String): List<BigDecimal> {
			if (peek().kind != Kind.LEFT) stop("$function needs brackets around its value")
			take()
			enter()
			try {
				if (peek().kind == Kind.END) unfinished(NEEDS_ARGUMENT)
				if (peek().kind == Kind.RIGHT || peek().kind == Kind.COMMA) stop(NEEDS_ARGUMENT)
				val values = ArrayList<BigDecimal>(2)
				values.add(addition())
				while (peek().kind == Kind.COMMA) {
					take()
					if (peek().kind == Kind.END) unfinished(NEEDS_ARGUMENT)
					if (peek().kind == Kind.RIGHT || peek().kind == Kind.COMMA) stop(NEEDS_ARGUMENT)
					values.add(addition())
				}
				if (peek().kind == Kind.END) unfinished(NEEDS_CLOSING)
				if (peek().kind != Kind.RIGHT) stop(NEEDS_CLOSING)
				take()
				return values
			} finally {
				nesting--
			}
		}

		private fun apply(function: String, values: List<BigDecimal>): BigDecimal = when (function) {
			"abs" -> single(function, values).abs(precision)
			"floor" -> single(function, values).setScale(0, RoundingMode.FLOOR)
			"ceil" -> single(function, values).setScale(0, RoundingMode.CEILING)
			"sqrt" -> single(function, values).let {
				if (it.signum() < 0) stop("sqrt needs a value of zero or more") else it.sqrt(precision)
			}

			"round" -> rounded(values)
			"min" -> extreme(function, values, smallest = true)
			else -> extreme(function, values, smallest = false)
		}

		private fun rounded(values: List<BigDecimal>): BigDecimal {
			if (values.size > 2) stop("round takes one value and, at most, how many decimals to keep")
			var decimals = 0
			if (values.size == 2) {
				decimals = try {
					values[1].intValueExact()
				} catch (_: ArithmeticException) {
					stop(ROUND_PLACES)
				}
				if (decimals < 0 || decimals > MAX_ROUND_SCALE) stop(ROUND_PLACES)
			}
			return values[0].setScale(decimals, RoundingMode.HALF_UP)
		}

		private fun extreme(function: String, values: List<BigDecimal>, smallest: Boolean): BigDecimal {
			if (values.size < 2) stop("$function needs at least two values")
			var best = values[0]
			for (index in 1 until values.size) {
				val candidate = values[index]
				val better = if (smallest) candidate < best else candidate > best
				if (better) best = candidate
			}
			return best
		}

		private fun single(function: String, values: List<BigDecimal>): BigDecimal {
			if (values.size != 1) stop("$function takes exactly one value")
			return values[0]
		}

		private fun raise(base: BigDecimal, exponent: BigDecimal): BigDecimal {
			val whole = try {
				exponent.toBigIntegerExact()
			} catch (_: ArithmeticException) {
				stop("A power has to be a whole number")
			}
			if (whole.abs() > BigInteger.valueOf(MAX_POWER.toLong())) stop("That power is too large")
			val steps = whole.toInt()
			return try {
				when {
					steps >= 0 -> base.pow(steps, precision)
					base.signum() == 0 -> stop(BY_ZERO)
					else -> BigDecimal.ONE.divide(base.pow(-steps, precision), precision)
				}
			} catch (_: ArithmeticException) {
				stop("Dhen cannot work that power out")
			}
		}

		private fun required(): BigDecimal {
			if (peek().kind == Kind.END) unfinished(NEEDS_VALUE)
			return unary()
		}

		private fun enter() {
			nesting++
			if (nesting > MAX_NESTING) stop("That has too many brackets inside brackets")
		}

		private fun isOperator(text: String): Boolean = peek().kind == Kind.OPERATOR && peek().text == text

		private fun peek(): Token = tokens[index]

		private fun take(): Token = tokens[index++]
	}

	private const val MAX_SOURCE_LENGTH = 4096
	private const val MAX_LITERAL_DIGITS = 1024
	private const val MAX_NESTING = 128
	private const val MAX_SCIENTIFIC_EXPONENT = 10_000
	private const val MAX_POWER = 1_000
	private const val MAX_RESULT_EXPONENT = 100_000
	private const val MAX_ROUND_SCALE = 100
	private const val GROUPING = 3
	private const val ANSWER = "ans"
	private const val SUFFIXES = "kmbtse"
	private const val TIMES_LETTER = "x"
	private const val BAD_NUMBER = "That is not a number Dhen can read"
	private const val BY_ZERO = "Dhen cannot divide by zero"
	private const val NEEDS_VALUE = "Dhen expected a value here"
	private const val NEEDS_CLOSING = "A closing bracket is missing"
	private const val NEEDS_ARGUMENT = "A value is missing between the brackets"
	private const val ROUND_PLACES = "round keeps between 0 and 100 decimals"
	private const val TOO_LONG = "That sum is too long to work out"
	private const val TOO_LARGE = "That answer is too large to write down"
	private const val UNFINISHED = "Keep typing"

	private val FUNCTIONS = setOf("abs", "ceil", "floor", "max", "min", "round", "sqrt")
	private val HUNDRED = BigDecimal.valueOf(100L)
	private val THOUSAND = BigDecimal.valueOf(1_000L)
	private val MILLION = BigDecimal.valueOf(1_000_000L)
	private val BILLION = BigDecimal.valueOf(1_000_000_000L)
	private val TRILLION = BigDecimal.valueOf(1_000_000_000_000L)
	private val STACK = BigDecimal.valueOf(64L)
	private val ENCHANTED_STACK = BigDecimal.valueOf(160L)
}
