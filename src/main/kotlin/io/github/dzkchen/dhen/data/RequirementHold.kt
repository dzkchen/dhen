package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.Handle
import java.util.function.BooleanSupplier

internal class RequirementHold(private val active: BooleanSupplier, private val require: () -> Handle) {
	private var handle: Handle? = null

	fun ensure(wanted: Boolean = true) {
		if (!wanted) {
			release()
		} else if (handle == null && active.asBoolean) {
			handle = require()
		}
	}

	fun release() {
		handle?.unsubscribe()
		handle = null
	}
}
