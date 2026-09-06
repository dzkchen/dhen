package io.github.dzkchen.dhen.data.sack

import io.github.dzkchen.dhen.util.NO_DIGITS
import io.github.dzkchen.dhen.util.digits
import io.github.dzkchen.dhen.util.matcher

internal const val SACKS_PREFIX = "[Sacks]"
internal const val ADDED_HOVER = "Added"
internal const val REMOVED_HOVER = "Removed"
internal const val OTHER_ITEMS = "other items"

private val SACK_CHANGE = matcher("([+-][\\d,]+) (.+) \\((.+)\\)")

internal fun readSackChanges(hover: String, into: MutableMap<String, Long>) {
	val rows = SACK_CHANGE.get().reset(hover)
	while (rows.find()) {
		val amount = digits(rows.group(1))
		if (amount == NO_DIGITS) continue
		val name = rows.group(2).trim()
		if (name.isEmpty()) continue
		val delta = if (rows.group(1).startsWith('-')) -amount else amount
		into[name] = (into[name] ?: 0L) + delta
	}
}
