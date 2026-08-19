package io.github.dzkchen.dhen.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.PriorityBlockingQueue
import kotlin.coroutines.resume

object TickClock {
	private val clientQueue = TickQueue()
	private val serverQueue = TickQueue()

	val clientTick: Long get() = clientQueue.tick
	val serverTick: Long get() = serverQueue.tick

	internal val pending: Int get() = clientQueue.size + serverQueue.size

	internal fun clientTicked() = clientQueue.advance()

	internal fun serverTicked() = serverQueue.advance()

	internal fun cancelWorldScopedWaits() {
		clientQueue.cancelWorldScoped()
		serverQueue.cancelWorldScoped()
	}

	internal fun shutdown() {
		clientQueue.cancelAll()
		serverQueue.cancelAll()
	}

	internal suspend fun awaitClientTicks(ticks: Int, survivesWorldChange: Boolean) =
		clientQueue.await(ticks, survivesWorldChange)

	internal suspend fun awaitServerTicks(ticks: Int, survivesWorldChange: Boolean) =
		serverQueue.await(ticks, survivesWorldChange)
}

suspend fun delayTicks(ticks: Int) = TickClock.awaitClientTicks(ticks, survivesWorldChange = false)

suspend fun delayServerTicks(ticks: Int) = TickClock.awaitServerTicks(ticks, survivesWorldChange = false)

suspend fun awaitTick() = delayTicks(1)

suspend fun repeatTicks(ticks: Int, block: () -> Unit) {
	while (true) {
		TickClock.awaitClientTicks(ticks, survivesWorldChange = true)
		block()
	}
}

suspend fun repeatServerTicks(ticks: Int, block: () -> Unit) {
	while (true) {
		TickClock.awaitServerTicks(ticks, survivesWorldChange = true)
		block()
	}
}

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

	suspend fun await(ticks: Int, survivesWorldChange: Boolean) = suspendCancellableCoroutine { continuation ->
		val waiter = Waiter(tick + ticks.coerceAtLeast(1), survivesWorldChange, continuation)
		waiting.add(waiter)
		continuation.invokeOnCancellation { waiting.remove(waiter) }
	}

	fun cancelWorldScoped() = cancel { !it.survivesWorldChange }

	fun cancelAll() = cancel { true }

	private inline fun cancel(matching: (Waiter) -> Boolean) =
		waiting.filter(matching).forEach { it.cancel() }

	private class Waiter(
		val dueTick: Long,
		val survivesWorldChange: Boolean,
		private val continuation: CancellableContinuation<Unit>
	) : Comparable<Waiter> {
		override fun compareTo(other: Waiter): Int = dueTick.compareTo(other.dueTick)

		fun resume() = continuation.resume(Unit)

		fun cancel() {
			continuation.cancel()
		}
	}
}
