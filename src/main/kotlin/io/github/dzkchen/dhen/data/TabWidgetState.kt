package io.github.dzkchen.dhen.data

object TabWidgetState {
	private val lines = Array(TabWidget.entries.size) { emptyList<String>() }
	private val stripped = Array(TabWidget.entries.size) { emptyList<String>() }

	fun lines(widget: TabWidget): List<String> = lines[widget.ordinal]

	fun stripped(widget: TabWidget): List<String> = stripped[widget.ordinal]

	fun active(widget: TabWidget): Boolean = lines[widget.ordinal].isNotEmpty()

	fun capture(widget: TabWidget, group: String): String? =
		stripped[widget.ordinal].firstOrNull()?.let {
			runCatching { widget.header.matchEntire(it)?.groups?.get(group)?.value }.getOrNull()
		}

	internal fun read(widget: TabWidget, lines: List<String>, stripped: List<String>) {
		this.lines[widget.ordinal] = lines
		this.stripped[widget.ordinal] = stripped
	}

	internal fun reset() {
		lines.fill(emptyList())
		stripped.fill(emptyList())
	}
}
