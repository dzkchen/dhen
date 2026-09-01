package io.github.dzkchen.dhen.util

private const val GROUP_SIZE = 3

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
