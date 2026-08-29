package io.github.dzkchen.dhen.config

class SelectorSetting(
	name: String,
	override val default: String,
	options: List<String>,
	val listed: Boolean = false,
	description: String = ""
) : Setting<String>(name, description) {

	private var cursor = 0

	internal var changed: (() -> Unit)? = null

	var preferred: String = default
		private set

	var options: List<String> = options
		set(value) {
			if (value.size != field.size) optionCountRevision++
			field = value
			cursor = cursorOnPreferred()
		}

	init {
		cursor = cursorOnPreferred()
	}

	var index: Int
		get() = cursor
		set(value) {
			if (options.size <= 1) {
				cursor = 0
				return
			}
			cursor = when {
				value < 0 -> options.size - 1
				value > options.size - 1 -> 0
				else -> value
			}
			preferred = options[cursor]
			changed?.invoke()
		}

	override var value: String
		get() = if (options.isEmpty()) default else options[cursor]
		set(value) {
			preferred = value
			cursor = cursorOnPreferred()
		}

	private fun cursorOnPreferred(): Int =
		options.indexOfFirst { it.equals(preferred, ignoreCase = true) }.coerceAtLeast(0)

	companion object {
		internal var optionCountRevision = 0
			private set
	}
}
