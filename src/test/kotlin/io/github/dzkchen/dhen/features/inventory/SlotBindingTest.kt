package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SlotBindingTest {
	@BeforeEach
	fun reset() {
		ContainerState.read(JsonObject())
		SlotBinding.setEnabled(true)
		SlotBinding.setEnabled(false)
	}

	@Test
	fun `the module declares itself under inventory`() {
		assertEquals("Slot Binding", SlotBinding.name)
		assertEquals(Category.INVENTORY, SlotBinding.category)
	}

	@Test
	fun `two clicks across the hotbar line make one link`() {
		SlotBinding.edit(40)
		assertEquals(40, SlotBinding.pending)
		SlotBinding.edit(17)
		assertEquals(ContainerState.NO_SLOT, SlotBinding.pending)
		assertEquals(40, ContainerState.partner(17))
		assertEquals(17, ContainerState.partner(40))
	}

	@Test
	fun `two slots on the same side of the hotbar line make nothing`() {
		SlotBinding.edit(17)
		SlotBinding.edit(18)
		assertEquals(ContainerState.NO_SLOT, SlotBinding.pending)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(17))
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(18))
	}

	@Test
	fun `clicking the same slot twice cancels the pending link`() {
		SlotBinding.edit(40)
		SlotBinding.edit(40)
		assertEquals(ContainerState.NO_SLOT, SlotBinding.pending)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(40))
	}

	@Test
	fun `clicking a linked slot with nothing pending clears that link`() {
		ContainerState.bind(17, 40)
		SlotBinding.edit(17)
		assertEquals(ContainerState.NO_SLOT, SlotBinding.pending)
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(17))
		assertEquals(ContainerState.NO_SLOT, ContainerState.partner(40))
	}
}
