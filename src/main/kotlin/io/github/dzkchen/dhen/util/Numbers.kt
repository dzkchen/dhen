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
