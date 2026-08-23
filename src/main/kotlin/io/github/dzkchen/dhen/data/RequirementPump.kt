package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.Handle
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class Requirement {
	private val held = AtomicInteger()
	private val lock = Any()

	val count: Int get() = held.get()

	fun require(alive: () -> Boolean, taken: () -> Unit = {}, settled: (Int) -> Unit = {}): Handle {
		if (!adjust(1, alive, taken, settled)) return Handle {}
		val released = AtomicBoolean()
		return Handle { if (released.compareAndSet(false, true)) adjust(-1, alive, taken, settled) }
	}

	fun reset(settled: (Int) -> Unit = {}) = synchronized(lock) {
		held.set(0)
		settled(0)
	}

	private fun adjust(delta: Int, alive: () -> Boolean, taken: () -> Unit, settled: (Int) -> Unit): Boolean =
		synchronized(lock) {
			if (!alive()) return false
			val now = held.addAndGet(delta)
			if (delta > 0) taken()
			settled(now)
			true
		}
}

internal class RequirementPump {
	private val requirement = Requirement()

	@Volatile
	private var pump: Job? = null

	val count: Int get() = requirement.count

	val polling: Boolean get() = pump?.isActive == true

	fun require(alive: () -> Boolean, start: () -> Job): Handle =
		requirement.require(alive) { held -> if (held == 0) stop() else launchIfIdle(start) }

	fun requireOnTake(alive: () -> Boolean, start: () -> Job): Handle =
		requirement.require(alive, taken = { launchIfIdle(start) }, settled = { held -> if (held == 0) stop() })

	fun reset() = requirement.reset { stop() }

	private fun launchIfIdle(start: () -> Job) {
		if (pump?.isActive != true) pump = start()
	}

	private fun stop() {
		pump?.cancel()
		pump = null
	}
}
