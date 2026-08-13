package io.github.dzkchen.dhen.config

class StringSetting(
	name: String,
	default: String = "",
	val maxLength: Int = Int.MAX_VALUE,
	description: String = ""
) : Setting<String>(name, description) {

	override val default: String = default.take(maxLength)

	override var value: String = this.default
		set(value) {
			field = value.take(maxLength)
		}
}
