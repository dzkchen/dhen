package io.github.dzkchen.dhen.config

class ActionSetting(
	name: String,
	override val default: () -> Unit = {},
	description: String = ""
) : Setting<() -> Unit>(name, description) {

	override var value: () -> Unit = default
}
