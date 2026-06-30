package io.github.dzkchen.dhen.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IdsTest {
	@Test
	fun acceptsValidIds() {
		assertEquals("dhen.dungeon-map", AddonId("dhen.dungeon-map").value)
		assertEquals(AddonId("dhen.dungeon-map"), ModuleId("dhen.dungeon-map:greeter").addonId)
	}

	@Test
	fun rejectsInvalidIds() {
		assertThrows(IllegalArgumentException::class.java) { AddonId("Dhen") }
		assertThrows(IllegalArgumentException::class.java) { AddonId("dhen..map") }
		assertThrows(IllegalArgumentException::class.java) { ModuleId("dhen.dungeon-map") }
		assertThrows(IllegalArgumentException::class.java) { ModuleId("addon:Module") }
	}
}
