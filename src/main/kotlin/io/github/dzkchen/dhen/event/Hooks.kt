package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe

internal interface Hooks {
	val feed: String

	fun uninstall()

	fun active(): Boolean
}

internal interface GuardedHooks<C : Any> : Hooks {
	val failsafe: Failsafe

	fun bound(): C?

	override fun active(): Boolean = bound() != null

	fun latchOff() = uninstall()
}

internal inline fun <C : Any, T> GuardedHooks<C>.guarded(label: String, fallback: T, block: (C) -> T): T {
	val bound = bound() ?: return fallback
	return try {
		block(bound)
	} catch (throwable: Throwable) {
		latchOff()
		failsafe.fail(label, throwable)
		fallback
	}
}

internal inline fun <C : Any> GuardedHooks<C>.guarded(label: String, block: (C) -> Unit) = guarded(label, Unit, block)
