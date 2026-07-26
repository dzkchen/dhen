package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiRows {
	fun bodyHeight(rowCount: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int {
		var total = 0
		for (i in 0 until rowCount) total += rowHeight + settingsHeightAt.applyAsInt(i)
		return total
	}

	fun rowAt(localY: Int, rowCount: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int? {
		if (localY < 0) return null
		var offset = 0
		for (i in 0 until rowCount) {
			if (localY < offset + rowHeight) return i
			offset += rowHeight
			val settingsHeight = settingsHeightAt.applyAsInt(i)
			if (settingsHeight > 0) {
				if (localY < offset + settingsHeight) return null
				offset += settingsHeight
			}
		}
		return null
	}
}
