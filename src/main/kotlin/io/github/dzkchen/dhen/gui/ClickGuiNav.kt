package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiNav {
	private const val NO_GAP = 0

	fun total(count: Int, rowsAt: IntUnaryOperator): Int = ClickGuiShell.spanTotal(count, rowsAt, NO_GAP)

	fun flatOf(column: Int, row: Int, rowsAt: IntUnaryOperator): Int =
		row + ClickGuiShell.spanStart(column, rowsAt, NO_GAP)

	fun columnOf(flat: Int, count: Int, rowsAt: IntUnaryOperator): Int =
		ClickGuiShell.spanAt(flat, count, rowsAt, NO_GAP)

	fun step(flat: Int, delta: Int, total: Int): Int = when {
		total <= 0 -> ClickGuiShell.NONE
		flat == ClickGuiShell.NONE -> if (delta >= 0) 0 else total - 1
		else -> Math.floorMod(flat + delta, total)
	}

	fun columnStep(column: Int, delta: Int, count: Int, rowsAt: IntUnaryOperator): Int {
		if (count <= 0 || delta == 0) return ClickGuiShell.NONE
		var next = column
		repeat(count) {
			next = Math.floorMod(next + delta, count)
			if (rowsAt.applyAsInt(next) > 0) return next
		}
		return ClickGuiShell.NONE
	}
}
