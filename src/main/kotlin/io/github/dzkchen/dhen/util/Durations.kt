package io.github.dzkchen.dhen.util

import java.util.regex.Pattern

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val YEAR = 365 * DAY

private const val COUNTDOWN_UNITS = 2

private val FACTORS = longArrayOf(YEAR, DAY, HOUR, MINUTE, SECOND)
private val SUFFIXES = charArrayOf('y', 'd', 'h', 'm', 's')
private val SPAN = Pattern.compile("(?<amount>\\d+)(?<unit>[ydhms])")

internal fun countdown(millis: Long): String {
	if (millis <= 0L) return "Soon"
	val result = StringBuilder()
	var left = millis
	var shown = 0
	for (index in FACTORS.indices) {
		val value = left / FACTORS[index]
		left %= FACTORS[index]
		if (value == 0L) continue
		if (shown > 0) result.append(' ')
		result.append(grouped(value)).append(SUFFIXES[index])
		if (++shown == COUNTDOWN_UNITS) break
	}
	return if (shown == 0) "0s" else result.toString()
}

internal fun spanMillis(text: String): Long {
	val span = SPAN.matcher(text)
	var total = 0L
	while (span.find()) {
		total += span.group("amount").toLong() * FACTORS[SUFFIXES.indexOf(span.group("unit")[0])]
	}
	return total
}
