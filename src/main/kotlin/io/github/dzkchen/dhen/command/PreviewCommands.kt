package io.github.dzkchen.dhen.command

interface PreviewCommands {
	fun toggleWorldRender(): String

	fun toggleHighlight(): String

	fun openArcPreview(): String

	fun showAlert(): String

	fun showNotice(): String

	companion object {
		const val UNAVAILABLE = "The previews are not available."

		val NONE: PreviewCommands = object : PreviewCommands {
			override fun toggleWorldRender(): String = UNAVAILABLE

			override fun toggleHighlight(): String = UNAVAILABLE

			override fun openArcPreview(): String = UNAVAILABLE

			override fun showAlert(): String = UNAVAILABLE

			override fun showNotice(): String = UNAVAILABLE
		}
	}
}
