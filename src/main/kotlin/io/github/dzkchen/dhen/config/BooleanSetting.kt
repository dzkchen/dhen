package io.github.dzkchen.dhen.config

class BooleanSetting(
	name: String,
	override val default: Boolean = false,
	description: String = ""
) : Setting<Boolean>(name, description) {
	var on: Boolean = default

	override var value: Boolean
		get() = on
		set(value) {
			on = value
		}
}
