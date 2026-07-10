package io.github.dzkchen.dhen.config

class SelectorSetting(
	name: String,
	override val default: String,
	val options: List<String>,
	description: String = ""
) : Setting<String>(name, description) {

	var index: Int = indexOf(default)
		set(value) {
			field = when {
				options.isEmpty() -> 0
				value < 0 -> options.size - 1
				value > options.size - 1 -> 0
				else -> value
			}
		}

	override var value: String
		get() = if (options.isEmpty()) default else options[index]
		set(value) {
			index = indexOf(value)
		}

	private fun indexOf(option: String): Int =
		options.indexOfFirst { it.equals(option, ignoreCase = true) }.coerceAtLeast(0)
}
