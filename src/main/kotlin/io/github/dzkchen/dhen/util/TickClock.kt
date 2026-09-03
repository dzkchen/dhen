package io.github.dzkchen.dhen.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.PriorityBlockingQueue
import kotlin.coroutines.resume

object TickClock {
	private val clientQueue = TickQueue()

	@Volatile
	var serverTick: Long = 0L
		private set

	val clientTick: Long get() = clientQueue.tick

	internal val pending: Int get() = clientQueue.size

	internal fun clientTicked() = clientQueue.advance()

	internal fun serverTicked() {
		serverTick++
	}

	internal fun cancelWaits() = clientQueue.cancelAll()

	internal suspend fun awaitClientTicks(ticks: Int) = clientQueue.await(ticks)
}

suspend fun delayTicks(ticks: Int) = TickClock.awaitClientTicks(ticks)

private class TickQueue {
	private val waiting = PriorityBlockingQueue<Waiter>()

	@Volatile
	var tick: Long = 0L
		private set

	val size: Int get() = waiting.size

	fun advance() {
		tick++
		while (true) {
			val next = waiting.peek() ?: return
			if (next.dueTick > tick) return
			waiting.poll()?.resume()
		}
	}

	suspend fun await(ticks: Int) = suspendCancellableCoroutine { continuation ->
		val waiter = Waiter(tick + ticks.coerceAtLeast(1), continuation)
		waiting.add(waiter)
		continuation.invokeOnCancellation { waiting.remove(waiter) }
	}

	fun cancelAll() = waiting.toList().forEach { it.cancel() }

	private class Waiter(
		val dueTick: Long,
		private val continuation: CancellableContinuation<Unit>
	) : Comparable<Waiter> {
		override fun compareTo(other: Waiter): Int = dueTick.compareTo(other.dueTick)

		fun resume() = continuation.resume(Unit)

		fun cancel() {
			continuation.cancel()
		}
	}
}
