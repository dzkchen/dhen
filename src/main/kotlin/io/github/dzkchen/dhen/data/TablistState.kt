package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.withoutCodes

object TablistState {
	var lines: List<String> = emptyList()
		private set

	var stripped: List<String> = emptyList()
		private set

	var header: String = ""
		private set

	var strippedHeader: String = ""
		private set

	var footer: String = ""
		private set

	var strippedFooter: String = ""
		private set

	internal fun read(lines: List<String>, stripped: List<String>): Boolean {
		if (this.lines == lines) return false
		this.lines = lines
		this.stripped = stripped
		return true
	}

	internal fun frame(header: String, footer: String): Boolean {
		if (this.header == header && this.footer == footer) return false
		this.header = header
		this.footer = footer
		strippedHeader = withoutCodes(header)
		strippedFooter = withoutCodes(footer)
		return true
	}

	internal fun reset() {
		lines = emptyList()
		stripped = emptyList()
		header = ""
		strippedHeader = ""
		footer = ""
		strippedFooter = ""
	}
}
