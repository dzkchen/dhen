package io.github.dzkchen.dhen.event

internal class ReusableEvent<T : Event>(
	private val spare: () -> T,
	private val reset: (T) -> Unit = {}
) {
	private val shared = spare()
	private var inUse = false

	fun borrow(): T = if (inUse) spare() else shared.also { inUse = true }

	fun release(event: T) {
		reset(event)
		if (event === shared) inUse = false
	}
}
