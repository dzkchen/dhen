package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.util.Color

class ColorSetting(
	name: String,
	default: Color,
	val allowAlpha: Boolean = false,
	description: String = ""
) : Setting<Color>(name, description) {

	override val default: Color = coerce(default)

	override var value: Color = this.default
		set(value) {
			field = coerce(value)
		}

	private fun coerce(color: Color): Color = if (allowAlpha) color else color.opaque()
}
