package io.github.dzkchen.dhen.features.qol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

	@Test
	fun `an unknown island or class is refused rather than silently dropped`() {
		assertNull(parseHotkeyContext("island:atlantis"))
		assertNull(parseHotkeyContext("class:paladin"))
		assertNull(parseHotkeyContext("somewhere:hub"))
		assertNull(parseHotkeyContext("island:"))
	}

	@Test
	fun `trailing rubbish after a complete scope is refused`() {
		assertNull(parseHotkeyContext("island:hub &"))
		assertNull(parseHotkeyContext("(island:hub"))
		assertNull(parseHotkeyContext("island:hub)"))
		assertNull(parseHotkeyContext(""))
	}
}
