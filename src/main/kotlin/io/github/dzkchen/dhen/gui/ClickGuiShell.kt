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

	fun clampedColumnHeight(naturalHeight: Int, headerHeight: Int, available: Int): Int =
		minOf(naturalHeight, maxOf(headerHeight, available))

	fun spanStart(index: Int, extentAt: IntUnaryOperator, gap: Int): Int {
		var offset = 0
		for (i in 0 until index) offset += extentAt.applyAsInt(i) + gap
		return offset
	}

	fun spanTotal(count: Int, extentAt: IntUnaryOperator, gap: Int): Int =
		if (count <= 0) 0 else spanStart(count, extentAt, gap) - gap

	fun spanAt(local: Int, count: Int, extentAt: IntUnaryOperator, gap: Int): Int {
		if (local < 0) return NONE
		var offset = 0
		for (i in 0 until count) {
			offset += extentAt.applyAsInt(i)
			if (local < offset) return i
			offset += gap
			if (local < offset) return NONE
		}
		return NONE
	}

	fun sectionRowAt(localY: Int, bodyTop: Int, rowCount: Int, rowHeight: Int): Int {
		if (localY < bodyTop) return NONE
		val row = (localY - bodyTop) / rowHeight
		return if (row < rowCount) row else NONE
	}

	fun tooltipLeft(
		columnLeft: Int,
		columnWidth: Int,
		tooltipWidth: Int,
		viewportWidth: Int,
		gap: Int,
		margin: Int
	): Int {
		val beside = columnLeft + columnWidth + gap
		if (beside + tooltipWidth + margin <= viewportWidth) return beside
		val before = columnLeft - gap - tooltipWidth
		if (before >= margin) return before
		return maxOf(margin, rightAlignedLeft(viewportWidth, tooltipWidth, margin))
	}

	fun tooltipTop(rowTop: Int, tooltipHeight: Int, viewportHeight: Int, margin: Int): Int =
		rowTop.coerceIn(margin, maxOf(margin, viewportHeight - margin - tooltipHeight))

	fun segmentsWidth(widths: IntArray, gap: Int): Int = spanTotal(widths.size, widthAt(widths), gap)

	fun segmentAt(localX: Int, widths: IntArray, gap: Int): Int = spanAt(localX, widths.size, widthAt(widths), gap)

	private fun widthAt(widths: IntArray) = IntUnaryOperator { index -> widths[index] }

	fun centeredLeft(viewportWidth: Int, width: Int): Int = (viewportWidth - width) / 2

	fun rightAlignedLeft(viewportWidth: Int, width: Int, margin: Int): Int = viewportWidth - margin - width
}
