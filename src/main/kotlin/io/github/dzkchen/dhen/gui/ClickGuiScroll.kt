package io.github.dzkchen.dhen.gui

internal object ClickGuiScroll {
	const val TOP = 0

	fun maxScroll(contentBottom: Int, viewportHeight: Int, margin: Int): Int =
		maxOf(0, contentBottom + margin - viewportHeight)

	fun clampOffset(offset: Int, maxScroll: Int): Int =
		offset.coerceIn(0, maxOf(0, maxScroll))

	fun stash(offset: Int, stashed: Int, maxScroll: Int): Int =
		when {
			// An existing stash outranks the live offset: once the content collapses the
			// offset is already truncated, so adopting it would degrade what we remember.
			maxScroll <= TOP -> if (stashed > TOP) stashed else offset
			stashed > maxScroll -> stashed
			else -> TOP
		}

	fun restore(offset: Int, stashed: Int, maxScroll: Int): Int =
		clampOffset(if (stashed > TOP) stashed else offset, maxScroll)
}

/**
 * A scroll position plus the offset the search is holding for it. The two belong to one object
 * because the invariant runs between them: the stash only tracks content a query is suppressing,
 * so every reposition except [refilter] and [settle] has to drop it. Spread across two fields on
 * the screen that rule was a comment; here it is the API.
 */
internal class ScrollState {
	var offset = ClickGuiScroll.TOP
		private set

	var stashed = ClickGuiScroll.TOP
		private set

	/** A query changed which rows are visible: stash what is about to be truncated away, and give back what a widening query makes room for again. */
	fun refilter(maxScroll: Int) {
		val next = ClickGuiScroll.stash(offset, stashed, maxScroll)
		offset = ClickGuiScroll.restore(offset, stashed, maxScroll)
		stashed = next
	}

	/** A direct user scroll — the user has overruled whatever the search was holding. */
	fun scrollTo(target: Int, maxScroll: Int) {
		offset = ClickGuiScroll.clampOffset(target, maxScroll)
		stashed = ClickGuiScroll.TOP
	}

	/**
	 * Pulls a row into view without disturbing the stash. This runs *after* [refilter] on the
	 * search path, so dropping the stash here would throw away the position a widening query is
	 * still owed.
	 */
	fun settle(target: Int, maxScroll: Int) {
		offset = ClickGuiScroll.clampOffset(target, maxScroll)
	}

	/** The content extent moved under a direct user action, so re-clamp and drop the stash. */
	fun reclamp(maxScroll: Int) {
		offset = ClickGuiScroll.clampOffset(offset, maxScroll)
		stashed = ClickGuiScroll.TOP
	}

	/** A direct user action repositioned focus without changing the extent. */
	fun dropStash() {
		stashed = ClickGuiScroll.TOP
	}
}
