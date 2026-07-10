package io.github.dzkchen.dhen.config

class BooleanSetting(
	name: String,
	override val default: Boolean = false,
	description: String = ""
) : Setting<Boolean>(name, description) {
	override var value: Boolean = default
}
