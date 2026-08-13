package io.github.dzkchen.dhen.config

import kotlin.math.floor

class NumberSetting(
	name: String,
	default: Double,
	val min: Double,
	val max: Double,
	val step: Double = 1.0,
	description: String = ""
) : Setting<Double>(name, description) {

	override val default: Double = coerce(default)

	var amount: Double = this.default
		set(value) {
			field = coerce(value)
		}

	override var value: Double
		get() = amount
		set(value) {
			amount = value
		}

	private fun coerce(raw: Double): Double {
		val stepped = if (step > 0.0) floor(raw / step + 0.5) * step else raw
		return stepped.coerceIn(min, max)
	}
}
