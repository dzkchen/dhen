package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.BooleanSetting

internal object Effects {
	const val REDUCED = "Reduced effects"

	val reducedSetting = BooleanSetting(
		REDUCED,
		false,
		"Drop translucency, blur, shadows, and motion for the flat tier."
	)

	var reduced: Boolean
		get() = reducedSetting.value
		set(value) {
			reducedSetting.value = value
		}
}
