package io.github.dzkchen.dhen.util

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

internal class Failsafe(private val message: String = "Dhen failed during {} and is now inert until restart") {
	private val latch = AtomicBoolean()

	val failed get() = latch.get()

	inline fun <T> guard(label: String, block: () -> T): T? {
		if (failed) return null
		return try {
			block()
		} catch (throwable: Throwable) {
			fail(label, throwable)
			null
		}
	}

	fun fail(label: String, throwable: Throwable) {
		if (latch.compareAndSet(false, true)) log.error(message, label, throwable)
	}

	private companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
