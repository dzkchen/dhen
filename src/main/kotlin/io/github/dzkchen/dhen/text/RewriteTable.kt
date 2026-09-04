package io.github.dzkchen.dhen.text

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink

private const val NO_MATCH = -1
private const val BRANCHES = 4
private const val INITIAL_POINTS = 128
private const val REPLACEMENT_ROOM = 32
private const val RUN_END = ' '

internal class Rewrite(
	val find: String,
	val replacement: Component,
	val unlessFollowedBy: List<String> = emptyList(),
	val wordBounded: Boolean = false,
	val leadingRunOnly: Boolean = false
)

internal class RewriteTable(rewrites: List<Rewrite>) {
	private val entries: Array<Entry>
	private val root = Node()

	init {
		val ordered = rewrites
			.filter { it.find.isNotEmpty() }
			.distinctBy { it.find }
			.sortedByDescending { it.find.length }
		entries = Array(ordered.size) { index -> entry(ordered, ordered[index]) }
		for (index in entries.indices) {
			var node = root
			for (point in entries[index].points) {
				node = node.branches.get(point) ?: Node().also { node.branches.put(point, it) }
			}
			node.match = index
		}
		link()
	}

	private val probe = Probe()

	fun replace(input: String): String {
		if (entries.isEmpty()) return input
		var out: StringBuilder? = null
		var copied = 0
		var index = 0
		var node = root
		var runClosed = false
		while (index < input.length) {
			val point = input.codePointAt(index)
			val next = index + Character.charCount(point)
			node = node.branches.get(point) ?: root
			val match = node.match
			if (match != NO_MATCH) {
				val entry = entries[match]
				val start = next - entry.length
				if (matches(entry, input, start, next, runClosed)) {
					val target = out ?: StringBuilder(input.length + REPLACEMENT_ROOM).also { out = it }
					target.append(input, copied, start).append(entry.plain)
					copied = next
					node = root
					if (entry.runOnly && (entry.closesRun || (next < input.length && input[next] == RUN_END))) runClosed = true
				}
			}
			index = next
		}
		val target = out ?: return input
		return target.append(input, copied, input.length).toString()
	}

	fun replace(input: FormattedCharSequence): FormattedCharSequence {
		if (!probe.finds(input)) return input
		val rewritten = rewrite(flattened(input)) ?: return input
		return Fixed(rewritten.points, rewritten.styles, rewritten.size)
	}

	fun replace(input: Component): Component {
		val visual = input.visualOrderText
		if (!probe.finds(visual)) return input
		val rewritten = rewrite(flattened(visual)) ?: return input
		return joined(rewritten)
	}

	private fun flattened(input: FormattedCharSequence): Flow {
		val source = Flow()
		input.accept(source)
		return source
	}

	private fun rewrite(source: Flow): Flow? {
		var out: Flow? = null
		var copied = 0
		var index = 0
		var node = root
		var runClosed = false
		while (index < source.size) {
			node = node.branches.get(source.points[index]) ?: root
			val match = node.match
			if (match != NO_MATCH) {
				val entry = entries[match]
				val next = index + 1
				val start = next - entry.points.size
				if (matches(entry, source, start, next, runClosed)) {
					val target = out ?: Flow().also { out = it }
					target.take(source, copied, start)
					target.base = source.styleAt(start)
					entry.visual.accept(target)
					target.base = null
					copied = next
					node = root
					if (entry.runOnly && (entry.closesRun || (next < source.size && source.points[next] == RUN_END.code))) {
						runClosed = true
					}
				}
			}
			index++
		}
		val target = out ?: return null
		target.take(source, copied, source.size)
		return target
	}

	private fun joined(flow: Flow): Component {
		val result = Component.empty()
		val run = StringBuilder()
		var index = 0
		while (index < flow.size) {
			val style = flow.styleAt(index)
			run.setLength(0)
			do {
				run.appendCodePoint(flow.points[index])
				index++
			} while (index < flow.size && flow.styleAt(index) === style)
			result.append(Component.literal(run.toString()).setStyle(style))
		}
		return result
	}

	private fun entry(ordered: List<Rewrite>, rewrite: Rewrite): Entry {
		val blockers = rewrite.unlessFollowedBy.toTypedArray()
		val extenders = ArrayList<Int>()
		for (index in ordered.indices) {
			val other = ordered[index].find
			if (other.length > rewrite.find.length && other.startsWith(rewrite.find)) extenders += index
		}
		return Entry(
			rewrite.find,
			rewrite.find.length,
			rewrite.find.codePoints().toArray(),
			rewrite.replacement.string,
			rewrite.replacement.visualOrderText,
			blockers,
			Array(blockers.size) { blockers[it].codePoints().toArray() },
			extenders.toIntArray(),
			rewrite.wordBounded,
			rewrite.leadingRunOnly,
			rewrite.leadingRunOnly && rewrite.find.endsWith(RUN_END)
		)
	}

	private fun link() {
		val pending = ArrayDeque<Node>()
		root.fail = root
		for (child in root.branches.values) {
			child.fail = root
			pending.addLast(child)
		}
		while (pending.isNotEmpty()) {
			val node = pending.removeFirst()
			val fail = node.fail ?: root
			for (branch in node.branches.int2ObjectEntrySet()) {
				val child = branch.value
				child.fail = fail.branches.get(branch.intKey) ?: root
				if (child.match == NO_MATCH) child.match = child.fail?.match ?: NO_MATCH
				pending.addLast(child)
			}
			for (branch in fail.branches.int2ObjectEntrySet()) node.branches.putIfAbsent(branch.intKey, branch.value)
		}
	}

	private fun matches(entry: Entry, input: String, start: Int, next: Int, runClosed: Boolean): Boolean {
		if (entry.runOnly && runClosed) return false
		if (blocked(entry, input, next) || !atBoundary(entry, input, start, next)) return false
		for (index in entry.extenders) {
			val other = entries[index]
			val end = start + other.length
			if (end > input.length || !input.regionMatches(start, other.find, 0, other.length)) continue
			if (!blocked(other, input, end) && atBoundary(other, input, start, end)) return false
		}
		return true
	}

	private fun matches(entry: Entry, source: Flow, start: Int, next: Int, runClosed: Boolean): Boolean {
		if (entry.runOnly && runClosed) return false
		if (blocked(entry, source, next) || !atBoundary(entry, source, start, next)) return false
		for (index in entry.extenders) {
			val other = entries[index]
			val end = start + other.points.size
			if (end > source.size || !occurs(other.points, source, start)) continue
			if (!blocked(other, source, end) && atBoundary(other, source, start, end)) return false
		}
		return true
	}

	private fun occurs(points: IntArray, source: Flow, start: Int): Boolean {
		for (offset in points.indices) {
			if (source.points[start + offset] != points[offset]) return false
		}
		return true
	}

	private fun blocked(entry: Entry, input: String, next: Int): Boolean {
		for (blocker in entry.blockers) {
			if (input.regionMatches(next, blocker, 0, blocker.length)) return true
		}
		return false
	}

	private fun blocked(entry: Entry, source: Flow, next: Int): Boolean {
		for (blocker in entry.blockerPoints) {
			if (next + blocker.size > source.size) continue
			var found = true
			for (offset in blocker.indices) {
				if (source.points[next + offset] != blocker[offset]) {
					found = false
					break
				}
			}
			if (found) return true
		}
		return false
	}

	private fun atBoundary(entry: Entry, input: String, start: Int, next: Int): Boolean =
		!entry.bounded || (
			(start == 0 || !inWord(input.codePointBefore(start))) &&
				(next == input.length || !inWord(input.codePointAt(next)))
			)

	private fun atBoundary(entry: Entry, source: Flow, start: Int, next: Int): Boolean =
		!entry.bounded || (
			(start == 0 || !inWord(source.points[start - 1])) &&
				(next == source.size || !inWord(source.points[next]))
			)

	private fun inWord(point: Int): Boolean =
		point == '_'.code ||
			point in 'a'.code..'z'.code ||
			point in 'A'.code..'Z'.code ||
			point in '0'.code..'9'.code

	private inner class Probe : FormattedCharSink {
		private var node = root
		private var found = false

		fun finds(input: FormattedCharSequence): Boolean {
			if (entries.isEmpty()) return false
			node = root
			found = false
			input.accept(this)
			return found
		}

		override fun accept(position: Int, style: Style, codePoint: Int): Boolean {
			node = node.branches.get(codePoint) ?: root
			if (node.match == NO_MATCH) return true
			found = true
			return false
		}
	}

	private class Node {
		val branches = Int2ObjectOpenHashMap<Node>(BRANCHES)
		var fail: Node? = null
		var match = NO_MATCH
	}

	private class Entry(
		val find: String,
		val length: Int,
		val points: IntArray,
		val plain: String,
		val visual: FormattedCharSequence,
		val blockers: Array<String>,
		val blockerPoints: Array<IntArray>,
		val extenders: IntArray,
		val bounded: Boolean,
		val runOnly: Boolean,
		val closesRun: Boolean
	)
}

private class Flow : FormattedCharSink {
	var points = IntArray(INITIAL_POINTS)
		private set
	var styles = arrayOfNulls<Style>(INITIAL_POINTS)
		private set
	var size = 0
		private set

	var base: Style? = null
		set(value) {
			field = value
			incoming = null
			outgoing = null
		}

	private var incoming: Style? = null
	private var outgoing: Style? = null

	override fun accept(position: Int, style: Style, codePoint: Int): Boolean {
		if (size == points.size) {
			points = points.copyOf(size * 2)
			styles = styles.copyOf(size * 2)
		}
		points[size] = codePoint
		styles[size] = applied(style)
		size++
		return true
	}

	fun styleAt(index: Int): Style = styles[index] ?: Style.EMPTY

	fun take(source: Flow, from: Int, until: Int) {
		for (index in from until until) accept(index, source.styleAt(index), source.points[index])
	}

	private fun applied(style: Style): Style {
		val onto = base ?: return style
		if (style !== incoming) {
			incoming = style
			outgoing = style.applyTo(onto)
		}
		return outgoing ?: style
	}
}

private class Fixed(
	private val points: IntArray,
	private val styles: Array<Style?>,
	private val size: Int
) : FormattedCharSequence {
	override fun accept(output: FormattedCharSink): Boolean {
		for (index in 0 until size) {
			if (!output.accept(index, styles[index] ?: Style.EMPTY, points[index])) return false
		}
		return true
	}
}
