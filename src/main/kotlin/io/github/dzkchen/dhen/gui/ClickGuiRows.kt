package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiRows {
	fun rowAt(localY: Int, rowCount: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int =
		bandAt(localY, rowCount, rowHeight, settingsHeightAt, settings = false)

	fun settingsRowAt(localY: Int, rowCount: Int, rowHeight: Int, settingsHeightAt: IntUnaryOperator): Int =
		bandAt(localY, rowCount, rowHeight, settingsHeightAt, settings = true)

	private fun bandAt(
		localY: Int,
		rowCount: Int,
		rowHeight: Int,
		settingsHeightAt: IntUnaryOperator,
		settings: Boolean
	): Int {
		if (localY < 0) return ClickGuiShell.NONE
		var offset = 0
		for (i in 0 until rowCount) {
			offset += rowHeight
			if (localY < offset) return if (settings) ClickGuiShell.NONE else i
			offset += settingsHeightAt.applyAsInt(i)
			if (localY < offset) return if (settings) i else ClickGuiShell.NONE
		}
		return ClickGuiShell.NONE
	}
}
