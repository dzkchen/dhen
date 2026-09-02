package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SlotTintTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		SlotTint.begin()
		SlotTint.take()
	}

	@Test
	fun `the higher priority wins whichever module claims first`() {
		SlotTint.begin()
		SlotTint.claim(LOW_COLOR, LOW)
		SlotTint.claim(HIGH_COLOR, HIGH)
		assertEquals(HIGH_COLOR, SlotTint.take())

		SlotTint.begin()
		SlotTint.claim(HIGH_COLOR, HIGH)
		SlotTint.claim(LOW_COLOR, LOW)
		assertEquals(HIGH_COLOR, SlotTint.take())
	}

	@Test
	fun `an equal priority does not take the slot from the module that claimed it`() {
		SlotTint.begin()
		SlotTint.claim(LOW_COLOR, LOW)
		SlotTint.claim(HIGH_COLOR, LOW)
		assertEquals(LOW_COLOR, SlotTint.take())
	}

	@Test
	fun `each slot is decided on its own`() {
		SlotTint.begin()
		SlotTint.claim(HIGH_COLOR, HIGH)
		assertEquals(HIGH_COLOR, SlotTint.take())

		SlotTint.begin()
		SlotTint.claim(LOW_COLOR, LOW)
		assertEquals(LOW_COLOR, SlotTint.take())
	}

	@Test
	fun `a claim that arrives between two slots is dropped rather than painted on the next one`() {
		SlotTint.claim(HIGH_COLOR, HIGH)

		SlotTint.begin()
		assertEquals(0, SlotTint.take())
	}

	@Test
	fun `a slot nobody claims stays untinted`() {
		SlotTint.begin()
		assertEquals(0, SlotTint.take())
	}

	private companion object {
		const val LOW = 0
		const val HIGH = 20
		const val LOW_COLOR = 0x40FF0000.toInt()
		const val HIGH_COLOR = 0x8200FF00.toInt()
	}
}
