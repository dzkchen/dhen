package io.github.dzkchen.dhen.event

internal interface Hooks {
	val feed: String

	fun uninstall()

	fun active(): Boolean
}
