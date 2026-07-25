package io.github.dzkchen.dhen.gui

internal object ClickGuiRows {
	fun bodyHeight(rowCount: Int, expandedCount: Int, rowHeight: Int, settingsHeight: Int): Int =
		rowCount * rowHeight + expandedCount * settingsHeight

	fun rowAt(localY: Int, rowCount: Int, rowHeight: Int, settingsHeight: Int, expanded: (Int) -> Boolean): Int? {
		if (localY < 0) return null
		var offset = 0
		for (i in 0 until rowCount) {
			if (localY < offset + rowHeight) return i
			offset += rowHeight
			if (expanded(i)) {
				if (localY < offset + settingsHeight) return null
				offset += settingsHeight
			}
		}
		return null
	}
}
