package io.github.dzkchen.dhen.features.qol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotkeyContextTest {
	@Test
	fun `every written scope reads back as the same scope`() {
		for (text in listOf(
			"always",
			"island:hub",
			"island:hub,garden",
			"class:berserk",
			"island:catacombs & class:berserk",
			"island:hub | island:garden",
			"!island:private_island",
			"(island:hub | island:garden) & !class:mage",
			"!(island:hub & class:tank)"
		)) {
			assertEquals(text, parseHotkeyContext(text)?.format(), text)
		}
	}

	@Test
	fun `and binds tighter than or without brackets`() {
		val parsed = parseHotkeyContext("island:hub & class:mage | island:garden")

		assertEquals("(island:hub & class:mage) | island:garden", parsed?.format())
	}
}
