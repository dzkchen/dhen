package io.github.dzkchen.dhen.event

interface Cancellable {
	var cancelled: Boolean

	fun cancel() {
		cancelled = true
	}
}
