package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.event.Handle
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

internal class ChordBinding(
	val codes: IntArray,
	val active: () -> Boolean,
	val fire: () -> Unit
)

internal class ChordTree {
	private class Node {
		val next = Int2ObjectOpenHashMap<Node>()
		var bindings: Array<ChordBinding> = emptyArray()
	}

	private var root = Node()

	var depth = 0
		private set

	val isEmpty: Boolean
		get() = depth == 0

	fun rebuild(bindings: List<ChordBinding>) {
		val fresh = Node()
		var deepest = 0
		for (binding in bindings) {
			if (binding.codes.isEmpty()) continue
			var node = fresh
			for (code in binding.codes) {
				node = node.next.get(code) ?: Node().also { node.next.put(code, it) }
			}
			node.bindings += binding
			if (binding.codes.size > deepest) deepest = binding.codes.size
		}
		root = fresh
		depth = deepest
	}

	fun match(buffer: IntArray, count: Int): ChordBinding? {
		for (start in 0 until count) {
			val node = walk(buffer, start, count) ?: continue
			for (binding in node.bindings) if (binding.active()) return binding
		}
		return null
	}

	fun growable(buffer: IntArray, count: Int): Boolean {
		for (start in 0 until count) {
			val node = walk(buffer, start, count) ?: continue
			if (!node.next.isEmpty()) return true
		}
		return false
	}

	private fun walk(buffer: IntArray, start: Int, count: Int): Node? {
		var node = root
		for (index in start until count) {
			node = node.next.get(buffer[index]) ?: return null
		}
		return node
	}
}

internal class ChordRegistry {
	private val bindings = ArrayList<ChordBinding>()

	val tree = ChordTree()

	fun add(binding: ChordBinding): Handle {
		bindings += binding
		tree.rebuild(bindings)
		return Handle {
			bindings.remove(binding)
			tree.rebuild(bindings)
		}
	}
}
