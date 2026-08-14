package io.github.dzkchen.dhen.gui

import java.util.function.IntSupplier
import java.util.function.IntUnaryOperator

internal class ScrollingStack(
	private val start: Int,
	private val gap: Int,
	private val margin: Int,
	private val count: IntSupplier,
	private val viewport: IntSupplier,
	private val extentAt: IntUnaryOperator
) {
	private val state = ScrollState()

	val offset: Int
		get() = state.offset

	fun max(): Int =
		ClickGuiScroll.maxScroll(start + ClickGuiShell.spanTotal(count.asInt, extentAt, gap), viewport.asInt, margin)

	fun startOf(index: Int): Int = ClickGuiShell.spanStart(index, extentAt, gap)

	fun originOf(index: Int): Int = ClickGuiShell.spanOrigin(start, index, extentAt, gap, state.offset)

	fun localOf(position: Int): Int = ClickGuiShell.spanLocal(start, position, state.offset)

	fun slotAt(position: Int): Int =
		ClickGuiShell.spanAt(ClickGuiShell.spanLocal(start, position, state.offset), count.asInt, extentAt, gap)

	fun scrollBy(delta: Int): Boolean {
		val max = max()
		if (max <= ClickGuiScroll.TOP) return false
		state.scrollTo(state.offset - delta, max)
		return true
	}

	fun reveal(index: Int) = revealSpan(startOf(index), extentAt.applyAsInt(index))

	fun revealSpan(spanStart: Int, extent: Int) = state.reveal(spanStart, extent, window(), max())

	fun refilter() = state.refilter(max())

	fun reclamp() = state.reclamp(max())

	private fun window(): Int = maxOf(0, viewport.asInt - margin - start)
}
