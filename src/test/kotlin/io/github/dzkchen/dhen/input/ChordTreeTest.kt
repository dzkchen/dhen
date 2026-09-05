package io.github.dzkchen.dhen.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChordTreeTest {
	@Test
	fun `one key then another is a different chord from the reverse`() {
		val forward = binding(1, 2)
		val backward = binding(2, 1)
		val tree = ChordTree()
		tree.rebuild(listOf(forward, backward))

		assertSame(forward, tree.match(intArrayOf(1, 2), 2))
		assertSame(backward, tree.match(intArrayOf(2, 1), 2))
	}

	@Test
	fun `a chord matches the tail of a longer run of keys`() {
		val chord = binding(4, 5)
		val tree = ChordTree()
		tree.rebuild(listOf(chord))

		assertSame(chord, tree.match(intArrayOf(9, 4, 5), 3))
		assertNull(tree.match(intArrayOf(4, 5, 9), 3))
	}

	@Test
	fun `the longest run wins over a shorter one that also matches`() {
		val short = binding(2)
		val long = binding(1, 2)
		val tree = ChordTree()
		tree.rebuild(listOf(short, long))

		assertSame(long, tree.match(intArrayOf(1, 2), 2))
		assertSame(short, tree.match(intArrayOf(7, 2), 2))
	}

	@Test
	fun `an inactive chord is skipped so a lower priority one can answer`() {
		val asleep = ChordBinding(intArrayOf(3), { false }) {}
		val awake = ChordBinding(intArrayOf(3), { true }) {}
		val tree = ChordTree()
		tree.rebuild(listOf(asleep, awake))

		assertSame(awake, tree.match(intArrayOf(3), 1))
	}

	@Test
	fun `a run is only held open while a longer chord can still grow from it`() {
		val tree = ChordTree()
		tree.rebuild(listOf(binding(1, 2)))

		assertTrue(tree.growable(intArrayOf(1), 1))
		assertFalse(tree.growable(intArrayOf(1, 2), 2))
		assertFalse(tree.growable(intArrayOf(8), 1))
	}

	@Test
	fun `depth follows the longest chord and rebuilding forgets the old set`() {
		val tree = ChordTree()
		assertTrue(tree.isEmpty)

		tree.rebuild(listOf(binding(1), binding(2, 3, 4)))
		assertEquals(3, tree.depth)

		tree.rebuild(listOf(binding(1)))
		assertEquals(1, tree.depth)
		assertNull(tree.match(intArrayOf(2, 3, 4), 3))
	}

	@Test
	fun `a handle removes only its own chord`() {
		val registry = ChordRegistry()
		val kept = binding(1)
		registry.add(kept)
		val handle = registry.add(binding(5, 6))

		handle.unsubscribe()

		assertEquals(1, registry.tree.depth)
		assertSame(kept, registry.tree.match(intArrayOf(1), 1))
	}

	private fun binding(vararg codes: Int) = ChordBinding(codes, { true }) {}
}
