package io.github.dzkchen.dhen.event

class TabCompletionEvent internal constructor(val command: String) : Event {
	private var offered: MutableList<String>? = null

	fun suggest(options: List<String>) {
		if (options.isEmpty()) return
		val list = offered ?: ArrayList<String>(options.size).also { offered = it }
		list += options
	}

	internal fun offers(): List<String>? = offered
}
