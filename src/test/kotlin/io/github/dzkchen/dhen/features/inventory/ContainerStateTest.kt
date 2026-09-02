package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContainerStateTest {
	@BeforeEach
	fun reset() {
		ContainerState.read(JsonObject())
	}

	@Test
	fun `a lock toggles on and off and only inside the player inventory range`() {
		assertTrue(ContainerState.toggleLock(4))
		assertTrue(ContainerState.isLocked(4))
		assertFalse(ContainerState.toggleLock(4))
		assertFalse(ContainerState.isLocked(4))
		assertFalse(ContainerState.toggleLock(ContainerState.INVENTORY_SLOTS))
		assertFalse(ContainerState.isLocked(ContainerState.INVENTORY_SLOTS))
	}

	@Test
	fun `a bind is readable from both of its slots and unbinding clears both`() {
		ContainerState.bind(15, 38)
		assertEquals(38, ContainerState.partner(15))
		assertEquals(15, ContainerState.partner(38))
		ContainerState.unbind(38)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(15))
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(38))
	}

	@Test
	fun `binding a slot that already has a partner drops the old pairing`() {
		ContainerState.bind(15, 38)
		ContainerState.bind(20, 38)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(15))
		assertEquals(38, ContainerState.partner(20))
		assertEquals(20, ContainerState.partner(38))
	}

	@Test
	fun `protecting the same key twice removes it again`() {
		assertTrue(ContainerState.protect("abc", byUuid = true))
		assertTrue("abc" in ContainerState.protectedUuids)
		assertFalse(ContainerState.protect("abc", byUuid = true))
		assertFalse("abc" in ContainerState.protectedUuids)
	}

	@Test
	fun `everything written survives being read back`() {
		ContainerState.protect("item-uuid", byUuid = true)
		ContainerState.protect("HYPERION", byUuid = false)
		ContainerState.toggleLock(7)
		ContainerState.bind(11, 44)
		val saved = ContainerState.snapshot()
		ContainerState.read(JsonObject())
		ContainerState.read(saved)
		assertTrue("item-uuid" in ContainerState.protectedUuids)
		assertTrue("HYPERION" in ContainerState.protectedIds)
		assertTrue(ContainerState.isLocked(7))
		assertEquals(44, ContainerState.partner(11))
		assertEquals(11, ContainerState.partner(44))
	}

	@Test
	fun `a bind pointing outside the hotbar is dropped on read`() {
		val document = JsonObject()
		val binds = JsonObject()
		binds.addProperty("11", 12)
		document.add("binds", binds)
		ContainerState.read(document)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(11))
	}
}
