package io.github.dzkchen.dhen.gui

internal object ClickGuiScroll {
	const val TOP = 0

	fun maxScroll(contentBottom: Int, viewportHeight: Int, margin: Int): Int =
		maxOf(0, contentBottom + margin - viewportHeight)

	fun clampOffset(offset: Int, maxScroll: Int): Int =
		offset.coerceIn(0, maxOf(0, maxScroll))

	fun thumbHeight(trackHeight: Int, viewportHeight: Int, maxScroll: Int, minimum: Int): Int {
		if (trackHeight <= 0 || maxScroll <= TOP) return trackHeight
		val proportional = trackHeight.toLong() * viewportHeight / (viewportHeight + maxScroll)
		return proportional.toInt().coerceIn(minOf(minimum, trackHeight), trackHeight)
	}

	fun thumbTop(trackTop: Int, trackHeight: Int, thumbHeight: Int, offset: Int, maxScroll: Int): Int {
		if (maxScroll <= TOP) return trackTop
		val travel = maxOf(0, trackHeight - thumbHeight)
		return trackTop + (travel.toLong() * clampOffset(offset, maxScroll) / maxScroll).toInt()
	}

	fun stash(offset: Int, stashed: Int, maxScroll: Int): Int =
		when {
			maxScroll <= TOP -> if (stashed > TOP) stashed else offset
			stashed > maxScroll -> stashed
			else -> TOP
		}

	fun restore(offset: Int, stashed: Int, maxScroll: Int): Int =
		clampOffset(if (stashed > TOP) stashed else offset, maxScroll)

	fun reveal(offset: Int, spanStart: Int, extent: Int, window: Int, maxScroll: Int): Int {
		val target = when {
			spanStart < offset -> spanStart
			spanStart + extent > offset + window -> minOf(spanStart, spanStart + extent - window)
			else -> offset
		}
		return clampOffset(target, maxScroll)
	}
}

internal class ScrollState {
	var offset = ClickGuiScroll.TOP
		private set

	var stashed = ClickGuiScroll.TOP
		private set

	fun refilter(maxScroll: Int) {
		val next = ClickGuiScroll.stash(offset, stashed, maxScroll)
		offset = ClickGuiScroll.restore(offset, stashed, maxScroll)
		stashed = next
	}

	fun scrollTo(target: Int, maxScroll: Int) {
		offset = ClickGuiScroll.clampOffset(target, maxScroll)
		stashed = ClickGuiScroll.TOP
	}

	fun reveal(spanStart: Int, extent: Int, window: Int, maxScroll: Int) {
		offset = ClickGuiScroll.reveal(offset, spanStart, extent, window, maxScroll)
	}

	fun reclamp(maxScroll: Int) = scrollTo(offset, maxScroll)
}
