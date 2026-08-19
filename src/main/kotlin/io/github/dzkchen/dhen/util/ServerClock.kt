package io.github.dzkchen.dhen.util

object ServerClock {
	var tps: Float = FULL_TPS
		private set

	var lastTickNanos: Long = 0L
		private set

	val ticks: Long get() = TickClock.serverTick

	val clientTicksSinceServerTick: Long get() = TickClock.clientTick - clientTickAtLastServerTick

	private var clientTickAtLastServerTick = 0L
	private var lastTimeSyncNanos = 0L
	private var timeSynced = false

	internal fun serverTicked(nanos: Long) {
		lastTickNanos = nanos
		clientTickAtLastServerTick = TickClock.clientTick
	}

	internal fun timeSynced(nanos: Long) {
		val elapsed = nanos - lastTimeSyncNanos
		if (timeSynced && elapsed > 0L) {
			tps = (TICKS_PER_TIME_SYNC / (elapsed / NANOS_PER_SECOND)).coerceIn(0f, FULL_TPS)
		}
		lastTimeSyncNanos = nanos
		timeSynced = true
	}

	internal fun reset() {
		tps = FULL_TPS
		lastTickNanos = 0L
		clientTickAtLastServerTick = TickClock.clientTick
		lastTimeSyncNanos = 0L
		timeSynced = false
	}

	private const val FULL_TPS = 20f
	private const val TICKS_PER_TIME_SYNC = 20f
	private const val NANOS_PER_SECOND = 1_000_000_000f
}
