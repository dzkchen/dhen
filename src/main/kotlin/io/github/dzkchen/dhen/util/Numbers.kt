package io.github.dzkchen.dhen.util

private const val GROUP_SIZE = 3

internal const val NO_DIGITS = -1L

internal fun grouped(value: Long): String {
	val sign = if (value < 0) "-" else ""
	val text = if (value < 0) (-value).toString() else value.toString()
	val separators = (text.length - 1) / GROUP_SIZE
	if (separators == 0) return sign + text
	val result = StringBuilder(sign.length + text.length + separators)
	result.append(sign)
	var first = text.length % GROUP_SIZE
	if (first == 0) first = GROUP_SIZE
	result.append(text, 0, first)
	var index = first
	while (index < text.length) {
		result.append(',')
		result.append(text, index, index + GROUP_SIZE)
		index += GROUP_SIZE
	}
	return result.toString()
}

internal fun digits(text: String?): Long {
	var value = NO_DIGITS
	for (character in text ?: return NO_DIGITS) {
		if (character !in '0'..'9') continue
		value = (if (value == NO_DIGITS) 0L else value) * 10 + (character - '0')
	}
	return value
}

private val SHORT_DIVISORS = longArrayOf(1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L)
private val SHORT_SUFFIXES = charArrayOf('k', 'M', 'B', 'T')
private val SHORT_DECIMAL_LIMITS = longArrayOf(100L, 1_000L, 1_000_000L, 100L)

internal fun shortNumber(value: Long): String {
	if (value < 0L) return "-" + shortNumber(if (value == Long.MIN_VALUE) Long.MAX_VALUE else -value)
	if (value < SHORT_DIVISORS[0]) return value.toString()
	var index = 0
	while (index + 1 < SHORT_DIVISORS.size && value >= SHORT_DIVISORS[index + 1]) index++
	val truncated = value / (SHORT_DIVISORS[index] / 10)
	val whole = truncated / 10
	val tenth = truncated % 10
	if (tenth == 0L || truncated >= SHORT_DECIMAL_LIMITS[index]) return whole.toString() + SHORT_SUFFIXES[index]
	return whole.toString() + '.' + tenth + SHORT_SUFFIXES[index]
}

private const val COMPACT_SUFFIXES = "kmbtpe"

private val COMPACT_MULTIPLIERS = longArrayOf(
	1_000L,
	1_000_000L,
	1_000_000_000L,
	1_000_000_000_000L,
	1_000_000_000_000_000L,
	1_000_000_000_000_000_000L
)

internal fun compactNumber(text: String): Long? {
	val cleaned = text.lowercase().replace(",", "").trim()
	if (cleaned.isEmpty()) return null
	cleaned.toLongOrNull()?.let { return it }
	val suffix = COMPACT_SUFFIXES.indexOf(cleaned[cleaned.length - 1])
	if (suffix < 0) return null
	val amount = cleaned.substring(0, cleaned.length - 1).toDoubleOrNull() ?: return null
	return (amount * COMPACT_MULTIPLIERS[suffix]).toLong()
}

internal const val NOT_ROMAN = -1

internal fun romanValue(text: String): Int {
	if (text.isEmpty()) return NOT_ROMAN
	var total = 0
	var last = 0
	for (index in text.length - 1 downTo 0) {
		val value = romanDigit(text[index])
		if (value == 0) return NOT_ROMAN
		total += if (value >= last) value else -value
		last = value
	}
	return total
}

private fun romanDigit(character: Char): Int = when (character) {
	'I' -> 1
	'V' -> 5
	'X' -> 10
	'L' -> 50
	'C' -> 100
	'D' -> 500
	'M' -> 1000
	else -> 0
}
