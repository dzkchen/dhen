package io.github.dzkchen.dhen.config

class StringSetting(
	name: String,
	override val default: String = "",
	val maxLength: Int = Int.MAX_VALUE,
	description: String = ""
) : Setting<String>(name, description) {

	override var value: String = default
		set(value) {
			field = value.take(maxLength)
		}
}
