package io.github.dzkchen.dhen.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

class ClientThreadDispatcherTest {
	@Test
	fun `tasks run only when drained in FIFO order on the draining thread`() {
		val dispatcher = ClientThreadDispatcher()
		val order = mutableListOf<Int>()
		val threads = mutableListOf<Thread>()

		repeat(3) { index ->
			dispatcher.dispatch(EmptyCoroutineContext, Runnable {
				order += index
				threads += Thread.currentThread()
			})
		}

		assertTrue(order.isEmpty())

		dispatcher.drainQueue()

		assertEquals(listOf(0, 1, 2), order)
		assertTrue(threads.all { it === Thread.currentThread() })
	}

	@Test
	fun `a throwing task does not stop later tasks`() {
		val dispatcher = ClientThreadDispatcher()
		val ran = mutableListOf<Int>()

		dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran += 0 })
		dispatcher.dispatch(EmptyCoroutineContext, Runnable { throw RuntimeException("boom") })
		dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran += 2 })

		dispatcher.drainQueue()

		assertEquals(listOf(0, 2), ran)
	}

	@Test
	fun `empty drain is a no-op`() {
		ClientThreadDispatcher().drainQueue()
	}

	@Test
	fun `tasks dispatched from another thread run on the draining thread`() {
		val dispatcher = ClientThreadDispatcher()
		val ranOn = AtomicReference<Thread>()

		val producer = Thread {
			dispatcher.dispatch(EmptyCoroutineContext, Runnable { ranOn.set(Thread.currentThread()) })
		}
		producer.start()
		producer.join()

		assertNull(ranOn.get())

		dispatcher.drainQueue()

		assertTrue(ranOn.get() === Thread.currentThread())
	}

	@Test
	fun `tasks enqueued during a drain run in the next drain`() {
		val dispatcher = ClientThreadDispatcher()
		val ran = mutableListOf<Int>()

		dispatcher.dispatch(EmptyCoroutineContext, Runnable {
			ran += 0
			dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran += 1 })
		})

		dispatcher.drainQueue()
		assertEquals(listOf(0), ran)

		dispatcher.drainQueue()
		assertEquals(listOf(0, 1), ran)
	}

	@Test
	fun `coroutines resume only when the queue is drained`() {
		val dispatcher = ClientThreadDispatcher()
		val result = CompletableDeferred<String>()

		CoroutineScope(dispatcher).launch { result.complete("done") }

		assertFalse(result.isCompleted)
		dispatcher.drainQueue()
		assertTrue(result.isCompleted)
	}
}
