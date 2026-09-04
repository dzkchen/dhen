package io.github.dzkchen.dhen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EtherwarpTraceTest {
	private class Blocks : VoxelField {
		private val flags = HashMap<Long, Int>()
		private val tops = HashMap<Long, Double>()

		fun solid(x: Int, y: Int, z: Int, top: Double = 1.0): Blocks {
			flags[key(x, y, z)] = 0
			tops[key(x, y, z)] = top
			return this
		}

		fun feetBlocker(x: Int, y: Int, z: Int): Blocks {
			flags[key(x, y, z)] = PASSABLE or BLOCKS_FEET
			return this
		}

		override fun flagsAt(x: Int, y: Int, z: Int): Int = flags[key(x, y, z)] ?: PASSABLE

		override fun collisionTopAt(x: Int, y: Int, z: Int): Double = tops[key(x, y, z)] ?: 0.0

		private fun key(x: Int, y: Int, z: Int): Long =
			(x.toLong() and 0xFFFF shl 32) or (y.toLong() and 0xFFFF shl 16) or (z.toLong() and 0xFFFF)
	}

	private fun alongZ(field: VoxelField, reach: Double = 20.0): EtherwarpTarget =
		traverseVoxels(0.5, 1.5, 0.5, 0.5, 1.5, 0.5 + reach, field, EtherwarpTarget())

	@Test
	fun `the ray lands on the first solid block with two clear blocks above it`() {
		val target = alongZ(Blocks().solid(0, 1, 5))

		assertTrue(target.found)
		assertTrue(target.succeeded)
		assertEquals(0, target.x)
		assertEquals(1, target.y)
		assertEquals(5, target.z)
	}

	@Test
	fun `a blocked head reports the block it hit but not a landing`() {
		val target = alongZ(Blocks().solid(0, 1, 5).solid(0, 3, 5))

		assertTrue(target.found)
		assertFalse(target.succeeded)
		assertEquals(5, target.z)
	}

	@Test
	fun `a block taller than one metre clears from its own top, not from its base`() {
		val fence = Blocks().solid(0, 1, 5, top = 1.5).solid(0, 2, 5)

		val target = traverseVoxels(0.5, 1.5, 0.5, 0.5, 1.5, 20.5, fence, EtherwarpTarget())

		assertTrue(target.succeeded)
		assertEquals(1, target.y)
	}

	@Test
	fun `something you cannot stand inside rejects the landing even though the ray passes it`() {
		val target = alongZ(Blocks().solid(0, 1, 5).feetBlocker(0, 2, 5))

		assertTrue(target.found)
		assertFalse(target.succeeded)
	}

	@Test
	fun `something you cannot stand inside rejects the landing at head height too`() {
		val target = alongZ(Blocks().solid(0, 1, 5).feetBlocker(0, 3, 5))

		assertTrue(target.found)
		assertFalse(target.succeeded)
	}

	@Test
	fun `a ray that reaches its end in open air finds nothing`() {
		val target = alongZ(Blocks())

		assertFalse(target.found)
		assertFalse(target.succeeded)
	}

	@Test
	fun `a diagonal ray walks the voxels between its ends`() {
		val target = traverseVoxels(0.5, 1.5, 0.5, 20.5, 1.5, 20.5, Blocks().solid(3, 1, 3), EtherwarpTarget())

		assertTrue(target.succeeded)
		assertEquals(3, target.x)
		assertEquals(3, target.z)
	}
}
