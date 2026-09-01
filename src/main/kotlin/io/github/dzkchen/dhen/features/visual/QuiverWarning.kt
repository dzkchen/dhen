package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.util.NanoClock
import java.util.Arrays
import kotlin.time.Duration.Companion.seconds

internal class QuiverWarning(
	private val clock: NanoClock,
	private val amounts: QuiverAmounts
) {
	private val used = BooleanArray(QuiverArrow.entries.size)
	private val pending = BooleanArray(QuiverArrow.entries.size)
	private var lastAlert = NEVER

	internal fun updated(
		arrow: QuiverArrow?,
		amount: Int,
		inInstance: Boolean,
		enabled: Boolean,
		threshold: Int
	): Boolean {
		if (arrow == null || arrow == QuiverArrow.NONE) return false
		if (inInstance) used[arrow.ordinal] = true
		if (!enabled || amount > threshold) return false
		val now = clock.nanoTime()
		if (lastAlert != NEVER && now - lastAlert < COOLDOWN_NANOS) return false
		lastAlert = now
		return true
	}

	internal fun completed(enabled: Boolean, threshold: Int): Boolean {
		Arrays.fill(pending, false)
		var reminders = false
		var index = 0
		while (index < QuiverArrow.entries.size) {
			val arrow = QuiverArrow.entries[index]
			if (enabled && used[index] && amounts.amount(arrow) <= threshold) {
				pending[index] = true
				reminders = true
			}
			used[index] = false
			index++
		}
		return reminders
	}

	internal fun takeReminder(): String? {
		var count = 0
		for (value in pending) if (value) count++
		if (count == 0) return null
		val names = StringBuilder()
		var emitted = 0
		for (arrow in QuiverArrow.entries) {
			if (!pending[arrow.ordinal]) continue
			emitted++
			when {
				emitted == 1 -> Unit
				emitted == count && count == 2 -> names.append(" and ")
				emitted == count -> names.append(", and ")
				else -> names.append(", ")
			}
			names.append(arrow.displayName)
		}
		Arrays.fill(pending, false)
		return names.toString()
	}

	internal fun reset() {
		Arrays.fill(used, false)
		Arrays.fill(pending, false)
		lastAlert = NEVER
	}

	internal fun used(arrow: QuiverArrow): Boolean = used[arrow.ordinal]

	private companion object {
		const val NEVER = Long.MIN_VALUE
		val COOLDOWN_NANOS = 30.seconds.inWholeNanoseconds
	}
}

internal fun interface QuiverAmounts {
	fun amount(arrow: QuiverArrow): Int
}
