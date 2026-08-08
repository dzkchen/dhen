package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiRows {
	fun bodyHeight(rowCount: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int =
		rowTop(rowCount, rowHeight, settingsHeightAt)

	fun rowTop(rowIndex: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int {
		var offset = 0
		for (i in 0 until rowIndex) offset += rowHeight + settingsHeightAt.applyAsInt(i)
		return offset
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
