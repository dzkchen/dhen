package io.github.dzkchen.dhen.gui

internal object ClickGuiNav {
	const val NONE = -1

	fun flatten(counts: IntArray, panel: Int, row: Int): Int {
		if (panel < 0 || panel >= counts.size || row < 0 || row >= counts[panel]) return NONE
		var flat = row
		for (i in 0 until panel) flat += counts[i]
		return flat
	}

	fun panelOf(counts: IntArray, flat: Int): Int {
		if (flat < 0) return NONE
		var remaining = flat
		for (i in counts.indices) {
			if (remaining < counts[i]) return i
			remaining -= counts[i]
		}
		return NONE
	}

	fun rowOf(counts: IntArray, flat: Int): Int {
		if (flat < 0) return NONE
		var remaining = flat
		for (i in counts.indices) {
			if (remaining < counts[i]) return remaining
			remaining -= counts[i]
		}
		return NONE
	}

	fun step(total: Int, flat: Int, delta: Int): Int {
		if (total <= 0) return NONE
		if (flat == NONE) return if (delta >= 0) 0 else total - 1
		return Math.floorMod(flat + delta, total)
	}
}
