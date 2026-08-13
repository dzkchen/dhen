package io.github.dzkchen.dhen.util

import io.github.dzkchen.dhen.Dhen
import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class ClientThreadDispatcher : CoroutineDispatcher() {
	private val queue = ConcurrentLinkedQueue<Runnable>()

	@Volatile
	private var stopped = false

	override fun dispatch(context: CoroutineContext, block: Runnable) {
		if (!stopped) queue.add(block)
	}

	fun shutdown() {
		stopped = true
		queue.clear()
	}

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
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
