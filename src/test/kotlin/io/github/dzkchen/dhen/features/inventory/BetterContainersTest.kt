package io.github.dzkchen.dhen.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BetterContainersTest {
	@Test
	fun `a run of like slots in one row becomes a single plate`() {
		val plates = merge(
			"..PPPPP..",
			"........."
		)

		assertEquals(listOf(Plate(2, BetterContainers.PLAIN.toInt(), 5, 1)), plates)
	}

	@Test
	fun `a block that is the same width on every row merges downwards into one plate`() {
		val plates = merge(
			".PPP.....",
			".PPP.....",
			".PPP....."
		)

		assertEquals(listOf(Plate(1, BetterContainers.PLAIN.toInt(), 3, 3)), plates)
	}

	@Test
	fun `a ragged block keeps the widest top run and gives the overhang its own plate`() {
		val plates = merge(
			".PPP.....",
			".PP......"
		)

		assertEquals(
			listOf(
				Plate(1, BetterContainers.PLAIN.toInt(), 3, 1),
				Plate(ROW_WIDTH + 1, BetterContainers.PLAIN.toInt(), 2, 1)
			),
			plates
		)
	}

	@Test
	fun `slots and buttons never share a plate even when they touch`() {
		val plates = merge("PPBB.....")

		assertEquals(
			listOf(
				Plate(0, BetterContainers.PLAIN.toInt(), 2, 1),
				Plate(2, BetterContainers.BUTTON.toInt(), 2, 1)
			),
			plates
		)
	}

	@Test
	fun `a run never wraps from the end of one row into the start of the next`() {
		val plates = merge(
			".......PP",
			"PP......."
		)

		assertEquals(
			listOf(
				Plate(7, BetterContainers.PLAIN.toInt(), 2, 1),
				Plate(ROW_WIDTH, BetterContainers.PLAIN.toInt(), 2, 1)
			),
			plates
		)
	}

	@Test
	fun `hidden filler leaves no plate at all`() {
		assertEquals(emptyList<Plate>(), merge("........."))
	}

	private data class Plate(val index: Int, val kind: Int, val width: Int, val height: Int)

	private fun merge(vararg rows: String): List<Plate> {
		val cells = rows.size * ROW_WIDTH
		val kinds = ByteArray(MENU_CELLS)
		for (row in rows.indices) {
			for (column in 0 until ROW_WIDTH) {
				kinds[row * ROW_WIDTH + column] = when (rows[row][column]) {
					'P' -> BetterContainers.PLAIN
					'B' -> BetterContainers.BUTTON
					else -> BetterContainers.HIDDEN
				}
			}
		}
		val plates = IntArray(MAX_PLATES * PLATE_FIELDS)
		val count = mergePlates(kinds, cells, BooleanArray(MENU_CELLS), plates)
		return (0 until count).map { index ->
			val base = index * PLATE_FIELDS
			Plate(plates[base], plates[base + 1], plates[base + 2], plates[base + 3])
		}
	}
}
