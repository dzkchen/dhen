package io.github.dzkchen.dhen.data.maxwell

class PowerTuning internal constructor(
	val name: String,
	val amount: String,
	val color: String,
	val icon: String
)

object MaxwellState {
	const val NO_POWER = "No Power"
	const val ABSENT = -1

	var power: String? = null
		private set

	var magicalPower: Int = ABSENT
		private set

	var tunings: List<PowerTuning>? = null
		private set

	internal fun select(power: String): Boolean {
		if (this.power == power) return false
		this.power = power
		return true
	}

	internal fun empower(magicalPower: Int): Boolean {
		if (this.magicalPower == magicalPower) return false
		this.magicalPower = magicalPower
		return true
	}

	internal fun tune(tunings: List<PowerTuning>): Boolean {
		if (sameTunings(tunings)) return false
		this.tunings = tunings
		return true
	}

	internal fun reset() {
		power = null
		magicalPower = ABSENT
		tunings = null
	}

	private fun sameTunings(replacement: List<PowerTuning>): Boolean {
		val held = tunings ?: return false
		if (held.size != replacement.size) return false
		for (index in held.indices) {
			val one = held[index]
			val other = replacement[index]
			if (one.name != other.name || one.amount != other.amount || one.icon != other.icon) return false
		}
		return true
	}
}
