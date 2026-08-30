package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiRows {
	fun rowAt(localY: Int, rowCount: Int, rowHeightAt: IntUnaryOperator, settingsHeightAt: IntUnaryOperator): Int =
		bandAt(localY, rowCount, rowHeightAt, settingsHeightAt, settings = false)

	fun settingsRowAt(localY: Int, rowCount: Int, rowHeightAt: IntUnaryOperator, settingsHeightAt: IntUnaryOperator): Int =
		bandAt(localY, rowCount, rowHeightAt, settingsHeightAt, settings = true)

	private fun bandAt(
		localY: Int,
		rowCount: Int,
		rowHeightAt: IntUnaryOperator,
		settingsHeightAt: IntUnaryOperator,
		settings: Boolean
	): Int {
		if (localY < 0) return ClickGuiShell.NONE
		var offset = 0
		for (i in 0 until rowCount) {
			offset += rowHeightAt.applyAsInt(i)
			if (localY < offset) return if (settings) ClickGuiShell.NONE else i
			offset += settingsHeightAt.applyAsInt(i)
			if (localY < offset) return if (settings) i else ClickGuiShell.NONE
		}
		return ClickGuiShell.NONE
	}
}
