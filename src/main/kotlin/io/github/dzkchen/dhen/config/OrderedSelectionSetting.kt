package io.github.dzkchen.dhen.config

class OrderedSelectionSetting(
	name: String,
	val options: List<String>,
	default: List<String>,
	description: String = ""
) : Setting<List<String>>(name, description) {
	override val default: List<String> = sanitized(default)

	override var value: List<String> = this.default
		set(value) {
			field = sanitized(value)
		}

	fun enabled(option: String): Boolean = option in value

	fun toggle(option: String) {
		if (option !in options) return
		value = if (enabled(option)) value - option else value + option
	}

	fun move(from: Int, to: Int) {
		if (from !in value.indices || to !in value.indices || from == to) return
		val reordered = value.toMutableList()
		val moved = reordered.removeAt(from)
		reordered.add(to, moved)
		value = reordered
	}

	private fun sanitized(source: List<String>): List<String> {
		val selected = ArrayList<String>(source.size)
		for (option in source) if (option in options && option !in selected) selected += option
		return selected
	}
}
