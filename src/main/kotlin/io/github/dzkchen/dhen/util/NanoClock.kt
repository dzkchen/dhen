package io.github.dzkchen.dhen.util

fun interface NanoClock {
	fun nanoTime(): Long

	companion object {
		val SYSTEM: NanoClock = NanoClock(System::nanoTime)
	}
}

fun interface EpochClock {
	fun epochMillis(): Long

	companion object {
		val SYSTEM: EpochClock = EpochClock(System::currentTimeMillis)
	}
}
