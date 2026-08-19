package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.withoutCodes

object ScoreboardState {
	var objective: String = ""
		private set

	var title: String = ""
		private set

	var strippedTitle: String = ""
		private set

	var lines: List<String> = emptyList()
		private set

	var stripped: List<String> = emptyList()
		private set

	var area: String? = null
		private set

	internal fun heading(objective: String, title: String) {
		this.objective = objective
		this.title = title
		strippedTitle = withoutCodes(title)
	}

	internal fun read(lines: List<String>, stripped: List<String>): Boolean {
		if (this.lines == lines) return false
		this.lines = lines
		this.stripped = stripped
		return true
	}

	internal fun locate(area: String?): Boolean {
		if (this.area == area) return false
		this.area = area
		return true
	}

	internal fun reset() {
		objective = ""
		title = ""
		strippedTitle = ""
		lines = emptyList()
		stripped = emptyList()
		area = null
	}
}
