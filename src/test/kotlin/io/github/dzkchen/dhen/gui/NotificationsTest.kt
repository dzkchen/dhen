package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val CARD_LEFT = 100
private const val CARD_RIGHT = 252
private const val FLOOR = 200

class NotificationsTest {
	private val font = StubFont()

	@BeforeEach
	@AfterEach
	fun reset() = Notifications.clear()

	private fun layout(pointerX: Int = NO_POINTER, pointerY: Int = NO_POINTER) =
		Notifications.layout(font, CARD_LEFT, CARD_RIGHT, FLOOR, pointerX, pointerY)

	private fun onScreenTicks(count: Int, pointerX: Int = NO_POINTER, pointerY: Int = NO_POINTER) {
		repeat(count) {
			layout(pointerX, pointerY)
			Notifications.tick()
		}
	}

	@Test
	fun `a card lives exactly its lifetime in ticks while it is on screen`() {
		Notifications.push("!", "Blocked", null)

		onScreenTicks(Notifications.LIFETIME_TICKS - 1)
		assertEquals(1, Notifications.visible.size)

		onScreenTicks(1)

		assertTrue(Notifications.visible.isEmpty())
	}

	@Test
	fun `a card that never reaches the screen never ages`() {
		Notifications.push("!", "Blocked", null)

		repeat(Notifications.LIFETIME_TICKS * 2) { Notifications.tick() }

		assertEquals(1, Notifications.visible.size)
		assertEquals(Notifications.LIFETIME_TICKS, Notifications.visible.first().remaining)
	}

	@Test
	fun `the stack drops its oldest card once it is full`() {
		repeat(Notifications.MAX_VISIBLE + 1) { index -> Notifications.push("!", "Card $index", null) }

		assertEquals(Notifications.MAX_VISIBLE, Notifications.visible.size)
		assertEquals("! Card 1", Notifications.visible.first().heading)
		assertEquals("! Card ${Notifications.MAX_VISIBLE}", Notifications.visible.last().heading)
	}

	@Test
	fun `the cursor holds the card under it and no other`() {
		Notifications.push("!", "Older", null)
		Notifications.push("!", "Newer", null)
		val older = Notifications.visible.first()
		val newer = Notifications.visible.last()

		onScreenTicks(1, CARD_LEFT + 1, FLOOR - 1)

		assertTrue(newer.held)
		assertFalse(older.held)
		assertEquals(Notifications.LIFETIME_TICKS, newer.remaining)
		assertEquals(Notifications.LIFETIME_TICKS - 1, older.remaining)
	}

	@Test
	fun `a card expires even while an older one is held under the cursor`() {
		Notifications.push("!", "Older", null)
		Notifications.push("!", "Newer", null)
		layout()
		val older = Notifications.visible.first()

		onScreenTicks(Notifications.LIFETIME_TICKS, CARD_LEFT + 1, older.top + 1)

		assertTrue(older.held)
		assertEquals(1, Notifications.visible.size)
		assertEquals("! Older", Notifications.visible.first().heading)
	}

	@Test
	fun `nothing is held when there is no cursor on screen`() {
		Notifications.push("!", "Blocked", null)

		layout()

		assertFalse(Notifications.visible.first().held)
	}

	@Test
	fun `cards stack upward from the floor with the newest at the bottom`() {
		Notifications.push("!", "Older", null)
		Notifications.push("!", "Newer", null)

		layout()

		val older = Notifications.visible.first()
		val newer = Notifications.visible.last()
		assertEquals(FLOOR, newer.bottom)
		assertTrue(older.bottom < newer.top)
	}

	@Test
	fun `a card with a message is taller than one without`() {
		Notifications.push("!", "Blocked", null)
		Notifications.push("!", "Blocked", "127.0.0.1:8080")

		val plain = Notifications.visible.first()
		val detailed = Notifications.visible.last()

		assertTrue(detailed.height(font) > plain.height(font))
	}

	@Test
	fun `the progress bar drains with the remaining lifetime`() {
		Notifications.push("!", "Blocked", null)
		val notice = Notifications.visible.first()
		assertEquals(1f, notice.progress)

		onScreenTicks(Notifications.LIFETIME_TICKS / 2)

		assertEquals(0.5f, notice.progress)
	}
}
