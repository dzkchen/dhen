package io.github.dzkchen.dhen.event

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.min

fun interface NanoClock {
	fun nanoTime(): Long

	companion object {
		val SYSTEM: NanoClock = NanoClock(System::nanoTime)
	}
}

internal fun interface HandlerGate {
	fun isActive(): Boolean
}

class EventProfiler internal constructor(
	internal val clock: NanoClock
) {
	@Volatile
	var deepMode: Boolean = false
}

/** Marker for packet and per-frame events that are only timed in deep mode. */
interface DeepProfiledEvent : Event

class HandlerTiming internal constructor(
	val eventName: String
) {
	private val durations = AtomicLongArray(WINDOW_SIZE)
	private val invocations = AtomicLong()

	internal fun record(durationNanos: Long) {
		val invocation = invocations.getAndIncrement()
		durations.set((invocation % WINDOW_SIZE).toInt(), durationNanos.coerceAtLeast(0L))
	}

	fun snapshot(): HandlerTimingSnapshot {
		val totalInvocations = invocations.get()
		val sampleCount = min(totalInvocations, WINDOW_SIZE.toLong()).toInt()
		var totalNanos = 0L
		var maxNanos = 0L
		var index = 0
		while (index < sampleCount) {
			val duration = durations.get(index)
			totalNanos += duration
			if (duration > maxNanos) maxNanos = duration
			index++
		}
		return HandlerTimingSnapshot(
			eventName = eventName,
			totalInvocations = totalInvocations,
			sampleCount = sampleCount,
			averageNanos = if (sampleCount == 0) 0L else totalNanos / sampleCount,
			maxNanos = maxNanos
		)
	}

	private companion object {
		const val WINDOW_SIZE = 128
	}
}

data class HandlerTimingSnapshot(
	val eventName: String,
	val totalInvocations: Long,
	val sampleCount: Int,
	val averageNanos: Long,
	val maxNanos: Long
)
