package io.github.dzkchen.dhen

internal class AutomationNotice(
	private val persist: () -> Unit,
	private val announceLine: (String) -> Unit
) {
	var hypixelNoticeShown: Boolean = false
		private set

	fun install(alreadyShown: Boolean) {
		hypixelNoticeShown = alreadyShown
	}

	fun hypixelConnected() {
		if (hypixelNoticeShown) return
		hypixelNoticeShown = true
		persist()
		announceLine(HYPIXEL_NOTICE)
	}

	fun announce(moduleName: String) {
		announceLine("$moduleName should not be used on Hypixel.")
	}

	private companion object {
		const val HYPIXEL_NOTICE =
			"Dhen does not condone cheating. Its automation features are not for use on Hypixel."
	}
}
