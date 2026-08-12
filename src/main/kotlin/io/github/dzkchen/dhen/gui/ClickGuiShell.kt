package io.github.dzkchen.dhen.gui

import java.util.function.IntUnaryOperator

internal object ClickGuiShell {
	const val NONE = -1

	fun columnLeft(slot: Int, columnWidth: Int, gap: Int): Int = slot * (columnWidth + gap)

	fun fieldWidth(columnCount: Int, columnWidth: Int, gap: Int): Int =
		if (columnCount <= 0) 0 else columnCount * columnWidth + (columnCount - 1) * gap

	fun slotAt(contentX: Int, columnCount: Int, columnWidth: Int, gap: Int): Int {
		if (contentX < 0 || columnCount <= 0) return NONE
		val slot = contentX / (columnWidth + gap)
		if (slot >= columnCount) return NONE
		return if (contentX - columnLeft(slot, columnWidth, gap) < columnWidth) slot else NONE
	}

	fun columnHeight(
		collapsed: Boolean,
		headerHeight: Int,
		bodyPad: Int,
		rowCount: Int,
		rowHeight: Int,
		settingsHeightAt: IntUnaryOperator
	): Int {
		if (collapsed) return headerHeight
		return headerHeight + bodyPad + ClickGuiRows.bodyHeight(rowCount, rowHeight, settingsHeightAt)
	}

	fun segmentsWidth(widths: IntArray, gap: Int): Int {
		if (widths.isEmpty()) return 0
		var total = (widths.size - 1) * gap
		for (i in widths.indices) total += widths[i]
		return total
	}

	fun segmentAt(localX: Int, widths: IntArray, gap: Int): Int {
		if (localX < 0) return NONE
		var offset = 0
		for (i in widths.indices) {
			if (localX < offset + widths[i]) return i
			offset += widths[i] + gap
			if (localX < offset) return NONE
		}
		return NONE
	}

	fun centeredLeft(viewportWidth: Int, width: Int): Int = (viewportWidth - width) / 2

	fun rightAlignedLeft(viewportWidth: Int, width: Int, margin: Int): Int = viewportWidth - margin - width
}
