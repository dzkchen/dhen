package io.github.dzkchen.dhen.util

import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class ClientThreadDispatcher : CoroutineDispatcher() {
	private val queue = ConcurrentLinkedQueue<Runnable>()

	override fun dispatch(context: CoroutineContext, block: Runnable) {
		queue.add(block)
	}

	// Bounded to the tasks pending at entry so a self-re-dispatching coroutine
	// (e.g. a yield loop) resumes next tick instead of spinning this one forever.
	fun drainQueue() {
		var remaining = queue.size
		while (remaining-- > 0) {
			val task = queue.poll() ?: break
			try {
				task.run()
			} catch (throwable: Throwable) {
				log.error("Client-thread task threw", throwable)
			}
		}
	}

	private companion object {
		private val log = LoggerFactory.getLogger(ClientThreadDispatcher::class.java)
	}
}
