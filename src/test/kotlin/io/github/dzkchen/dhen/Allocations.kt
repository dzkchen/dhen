package io.github.dzkchen.dhen

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

internal object Allocations {
	private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

	@Volatile
	var sink: Any? = null

	@Volatile
	var floatSink: Float = 0f

	@Volatile
	var flagSink: Boolean = false

	val measurable: Boolean = threads.isThreadAllocatedMemorySupported.also {
		if (it) threads.isThreadAllocatedMemoryEnabled = true
	}

	fun bytesPerCall(body: Runnable): Double = (totalBytes(body) - totalBytes(EMPTY)).toDouble() / ITERATIONS

	private fun totalBytes(body: Runnable): Long {
		for (warmup in 0 until WARMUPS) body.run()
		val thread = Thread.currentThread().threadId()
		val before = threads.getThreadAllocatedBytes(thread)
		for (measured in 0 until ITERATIONS) body.run()
		return threads.getThreadAllocatedBytes(thread) - before
	}

	private val MARK = Any()
	private val EMPTY = Runnable { sink = MARK }
	private const val WARMUPS = 20_000
	private const val ITERATIONS = 200_000
}
