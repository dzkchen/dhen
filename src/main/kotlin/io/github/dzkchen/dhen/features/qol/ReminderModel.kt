package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.features.visual.FIELD_SEPARATOR
import java.util.Locale

internal const val REPEAT_FOREVER = -1
internal const val REPEAT_ONCE = 1
internal const val MIN_REPEATS = 2

internal enum class ReminderKind { TIMER, TODO, KAT }

internal enum class ReminderOutput(val chat: Boolean, val title: Boolean, val sound: Boolean) {
	CHAT(true, false, false),
	TITLE_BOX(false, true, false),
	CHAT_AND_TITLE(true, true, false),
	SOUND_ONLY(false, false, true),
	CHAT_AND_SOUND(true, false, true),
	TITLE_AND_SOUND(false, true, true),
	ALL(true, true, true);

	companion object {
		val VISUALS = listOf(CHAT, TITLE_BOX, CHAT_AND_TITLE, SOUND_ONLY)

		fun named(raw: String): ReminderOutput? =
			entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

		fun composed(visual: ReminderOutput, sound: Boolean): ReminderOutput {
			if (!sound) return visual
			return when (visual) {
				CHAT -> CHAT_AND_SOUND
				TITLE_BOX -> TITLE_AND_SOUND
				CHAT_AND_TITLE -> ALL
				else -> visual
			}
		}

		fun visualOf(output: ReminderOutput): ReminderOutput = when {
			output.chat && output.title -> CHAT_AND_TITLE
			output.title -> TITLE_BOX
			output.chat -> CHAT
			else -> SOUND_ONLY
		}
	}
}

internal enum class ReminderUnit(val label: String, val multiplierMs: Long) {
	SECONDS("seconds", 1_000L),
	MINUTES("minutes", 60_000L),
	HOURS("hours", 3_600_000L),
	DAYS("days", 86_400_000L);

	companion object {
		fun named(raw: String): ReminderUnit? = when (raw.lowercase(Locale.ROOT)) {
			"s", "sec", "seconds" -> SECONDS
			"m", "min", "minutes" -> MINUTES
			"h", "hour", "hours" -> HOURS
			"d", "day", "days" -> DAYS
			else -> null
		}
	}
}

internal class Reminder(
	val id: Int,
	val kind: ReminderKind,
	var label: String,
	var message: String,
	var realTime: Boolean,
	var output: ReminderOutput,
	var durationMs: Long,
	var totalRepeats: Int,
	var remainingMs: Long,
	var dueAtMs: Long,
	var repeatCount: Int,
	var off: Boolean
) {
	val display: String
		get() = label.ifBlank { message }

	fun remaining(now: Long): Long = if (!off && realTime) maxOf(0L, dueAtMs - now) else maxOf(0L, remainingMs)

	fun lateBy(now: Long): Long = if (realTime) maxOf(0L, now - dueAtMs) else 0L

	fun due(now: Long, stepMs: Long, playing: Boolean): Boolean {
		if (off) return false
		if (realTime) return now >= dueAtMs
		if (playing) remainingMs -= stepMs
		return remainingMs <= 0L
	}

	fun restart(now: Long) {
		remainingMs = durationMs
		if (realTime) dueAtMs = now + durationMs
	}
}

internal object ReminderText {
	fun format(ms: Long): String {
		val seconds = maxOf(0L, ms) / 1_000L
		val minutes = seconds / 60L
		val hours = minutes / 60L
		val days = hours / 24L
		return when {
			days > 0L -> plural(days, "day")
			hours > 0L -> plural(hours, "hour")
			minutes > 0L -> plural(minutes, "minute")
			else -> plural(seconds, "second")
		}
	}

	fun split(totalMs: Long): Pair<Long, ReminderUnit> {
		for (unit in SPLIT_ORDER) {
			if (totalMs >= unit.multiplierMs) return totalMs / unit.multiplierMs to unit
		}
		return 0L to ReminderUnit.SECONDS
	}

	fun remainder(totalMs: Long): Long {
		val (amount, unit) = split(totalMs)
		return totalMs - amount * unit.multiplierMs
	}

	fun sanitize(text: String): String =
		text.replace(ROW_SEPARATOR, " ").replace(FIELD_SEPARATOR, " ").trim()

	private fun plural(count: Long, unit: String): String = "$count " + if (count == 1L) unit else unit + "s"

	private val SPLIT_ORDER = listOf(ReminderUnit.DAYS, ReminderUnit.HOURS, ReminderUnit.MINUTES, ReminderUnit.SECONDS)
}

internal class ReminderClock {
	private var started = false
	private var lastTickMs = 0L
	private var accumulatedMs = 0L

	fun restart(now: Long) {
		started = true
		lastTickMs = now
		accumulatedMs = 0L
	}

	fun step(now: Long): Long {
		if (!started) {
			started = true
			lastTickMs = now
			return IDLE
		}
		accumulatedMs += maxOf(0L, now - lastTickMs)
		lastTickMs = now
		if (accumulatedMs < SECOND) return REAL_TIME_ONLY
		val whole = accumulatedMs / SECOND * SECOND
		accumulatedMs %= SECOND
		return whole
	}

	internal companion object {
		const val IDLE = -1L
		const val REAL_TIME_ONLY = 0L
		private const val SECOND = 1_000L
	}
}

internal fun reminderRow(reminder: Reminder): String = listOf(
	reminder.id.toString(),
	reminder.kind.name,
	if (reminder.realTime) "1" else "0",
	reminder.output.name,
	reminder.durationMs.toString(),
	reminder.totalRepeats.toString(),
	reminder.remainingMs.toString(),
	reminder.dueAtMs.toString(),
	reminder.repeatCount.toString(),
	if (reminder.off) "1" else "0",
	reminder.label,
	reminder.message
).joinToString(FIELD_SEPARATOR)

internal fun readReminderRow(line: String): Reminder? {
	val fields = line.split(FIELD_SEPARATOR)
	if (fields.size != ROW_FIELDS) return null
	val id = fields[0].toIntOrNull() ?: return null
	val kind = ReminderKind.entries.firstOrNull { it.name == fields[1] } ?: return null
	val output = ReminderOutput.named(fields[3]) ?: return null
	val message = fields[11]
	if (message.isEmpty()) return null
	return Reminder(
		id = id,
		kind = kind,
		label = fields[10],
		message = message,
		realTime = fields[2] == "1",
		output = output,
		durationMs = fields[4].toLongOrNull() ?: return null,
		totalRepeats = fields[5].toIntOrNull() ?: return null,
		remainingMs = fields[6].toLongOrNull() ?: return null,
		dueAtMs = fields[7].toLongOrNull() ?: return null,
		repeatCount = fields[8].toIntOrNull() ?: return null,
		off = fields[9] == "1"
	)
}

internal fun writtenReminders(reminders: List<Reminder>): String =
	reminders.joinToString(ROW_SEPARATOR, transform = ::reminderRow)

private const val ROW_FIELDS = 12
