package io.github.dzkchen.dhen.config

internal const val ROW_SEPARATOR = "\n"

internal class RowBook<K, V>(private val stored: () -> String, private val read: (String) -> Pair<K, V>?) {
	private var source: String? = null
	private val entries = linkedMapOf<K, V>()

	fun all(): Map<K, V> {
		val text = stored()
		if (text === source) return entries
		source = text
		entries.clear()
		for (line in text.split(ROW_SEPARATOR)) {
			val (key, value) = read(line) ?: continue
			entries[key] = value
		}
		return entries
	}
}
