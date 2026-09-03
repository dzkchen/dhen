package io.github.dzkchen.dhen.command

interface HudCommands {
	fun openEditor(): String

	fun resetLayout(): String

	companion object {
		const val UNAVAILABLE = "The HUD editor is not available."

		fun resetSummary(reset: Int): String = when (reset) {
			0 -> "Every HUD element is already at its declared layout."
			1 -> "Reset 1 HUD element to the declared layout."
			else -> "Reset $reset HUD elements to the declared layout."
		}

		val NONE: HudCommands = object : HudCommands {
			override fun openEditor(): String = UNAVAILABLE

			override fun resetLayout(): String = UNAVAILABLE
		}
	}
}
