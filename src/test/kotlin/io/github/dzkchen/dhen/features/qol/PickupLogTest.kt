package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.NanoClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PickupLogTest {
	private var now = NOW
	private lateinit var element: PickupLogElement

	@BeforeEach
	fun prepare() {
		now = NOW
		element = PickupLogElement(NanoClock { now })
	}

	@Test
	fun `the module lands in QOL with one sack toggle and one movable element`() {
		assertEquals(Category.QOL, PickupLog.category)
		assertEquals(listOf("Hide Sack Messages"), PickupLog.settings.map { it.name })
		assertEquals(listOf("Pickup Log"), PickupLog.hudElements.map { it.name })
	}

	@Test
	fun `a gain and a loss compose the coloured lines the source writes`() {
		element.record("MITHRIL", "§9Mithril", 16)
		element.record("FLINT", "§fFlint", -3)

		assertEquals(2, element.shownLines)
		assertEquals("§a+ 16x §r§9Mithril", element.lineAt(0))
		assertEquals("§c- 3x §r§fFlint", element.lineAt(1))
	}

	@Test
	fun `repeat pickups of one item accumulate into a single line`() {
		element.record("MITHRIL", "§9Mithril", 16)
		at(NOW + SECOND) { element.record("MITHRIL", "§9Mithril", 8) }

		assertEquals(1, element.shownLines)
		assertEquals("§a+ 24x §r§9Mithril", element.lineAt(0))
	}

	@Test
	fun `gains and losses of one item stay on their own lines`() {
		element.record("MITHRIL", "§9Mithril", 16)
		element.record("MITHRIL", "§9Mithril", -4)

		assertEquals(2, element.shownLines)
		assertEquals("§a+ 16x §r§9Mithril", element.lineAt(0))
		assertEquals("§c- 4x §r§9Mithril", element.lineAt(1))
	}

	@Test
	fun `gains always list above losses however they arrive`() {
		element.record("FLINT", "§fFlint", -3)
		element.record("MITHRIL", "§9Mithril", 16)

		assertEquals("§a+ 16x §r§9Mithril", element.lineAt(0))
		assertEquals("§c- 3x §r§fFlint", element.lineAt(1))
	}

	@Test
	fun `a line disappears six seconds after it last changed`() {
		element.record("MITHRIL", "§9Mithril", 16)
		at(NOW + 4 * SECOND) { element.record("FLINT", "§fFlint", 2) }

		at(NOW + 6 * SECOND) { element.prune() }

		assertEquals(2, element.shownLines)

		at(NOW + 7 * SECOND) { element.prune() }

		assertEquals(1, element.shownLines)
		assertEquals("§a+ 2x §r§fFlint", element.lineAt(0))

		at(NOW + 11 * SECOND) { element.prune() }

		assertEquals(0, element.shownLines)
	}

	@Test
	fun `refreshing an entry restarts its six seconds`() {
		element.record("MITHRIL", "§9Mithril", 16)
		at(NOW + 5 * SECOND) { element.record("MITHRIL", "§9Mithril", 1) }

		at(NOW + 7 * SECOND) { element.prune() }

		assertEquals(1, element.shownLines)
		assertEquals("§a+ 17x §r§9Mithril", element.lineAt(0))
	}

	@Test
	fun `the log stays bounded by dropping its oldest line`() {
		for (index in 0 until 40) at(NOW + index) { element.record("ITEM_$index", "Item $index", 1) }

		assertEquals(32, element.shownLines)
		assertEquals("§a+ 1x §rItem 8", element.lineAt(0))
		assertEquals("§a+ 1x §rItem 39", element.lineAt(31))
	}

	@Test
	fun `disabling the log empties it`() {
		element.record("MITHRIL", "§9Mithril", 16)

		element.clear()

		assertEquals(0, element.shownLines)
	}

	private inline fun at(instant: Long, action: () -> Unit) {
		now = instant
		action()
	}

	private companion object {
		const val SECOND = 1_000_000_000L
		const val NOW = 500_000_000_000L
	}
}
