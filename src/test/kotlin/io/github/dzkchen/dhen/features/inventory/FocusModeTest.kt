package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.input.keyDisplayName
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class FocusModeTest {
	private val toggleKey = FocusMode.settings.first { it.name == "Toggle Key" } as KeybindSetting
	private val alwaysEnabled = FocusMode.settings.first { it.name == "Always Enabled" } as BooleanSetting
	private val keepMenuItems = FocusMode.settings.first { it.name == "Keep Menu Items" } as BooleanSetting

	@BeforeEach
	fun arm() {
		for (setting in FocusMode.settings) setting.reset()
		SkyBlockLocation.located("mini1A", skyBlock = true, mode = Island.HUB.modeId, map = null)
		FocusMode.opened("Auctions Browser", emptyList())
	}

	@AfterEach
	fun disarm() = SkyBlockLocation.reset()

	@Test
	fun `an unbound toggle key leaves the tooltip untouched`() {
		val event = tooltip("Hyperion", "Damage: 260", "LEGENDARY SWORD")
		FocusMode.condense(event)
		assertEquals(listOf("Hyperion", "Damage: 260", "LEGENDARY SWORD"), plain(event))
	}

	@Test
	fun `a bound toggle key offers the hint on the second line while it is off`() {
		toggleKey.value = GLFW.GLFW_KEY_H
		val event = tooltip("Hyperion", "Damage: 260")
		FocusMode.condense(event)
		val hint = "§7Press ${keyDisplayName(GLFW.GLFW_KEY_H)} to shorten this tooltip."
		assertEquals(listOf("Hyperion", hint, "Damage: 260"), plain(event))
	}

	@Test
	fun `always enabled cuts the tooltip to its name and drops the hint`() {
		toggleKey.value = GLFW.GLFW_KEY_H
		alwaysEnabled.value = true
		val event = tooltip("Hyperion", "Damage: 260", "LEGENDARY SWORD")
		FocusMode.condense(event)
		assertEquals(listOf("Hyperion"), plain(event))
	}

	@Test
	fun `the auction section is kept from its separator for twenty lines`() {
		alwaysEnabled.value = true
		val lore = mutableListOf("Hyperion", "Damage: 260", "-----------------")
		for (line in 1..25) lore.add("Auction line $line")
		val event = tooltip(*lore.toTypedArray())
		FocusMode.condense(event)
		val kept = plain(event)
		assertEquals("Hyperion", kept[0])
		assertEquals("-----------------", kept[1])
		assertEquals("Auction line 19", kept.last())
		assertEquals(20 + 1, kept.size)
	}

	@Test
	fun `outside the auction house nothing below the name survives`() {
		alwaysEnabled.value = true
		FocusMode.opened("Stats & Equipment", emptyList())
		val event = tooltip("Hyperion", "-----------------", "Seller: Someone")
		FocusMode.condense(event)
		assertEquals(listOf("Hyperion"), plain(event))
	}

	@Test
	fun `a menu button keeps its whole description until the setting is turned off`() {
		alwaysEnabled.value = true
		val event = tooltip("Close", "Click to go back")
		FocusMode.condense(event, ItemStack(ItemFixture.vanilla().item))
		assertEquals(listOf("Close", "Click to go back"), plain(event))
		keepMenuItems.value = false
		val second = tooltip("Close", "Click to go back")
		FocusMode.condense(second, ItemStack(ItemFixture.vanilla().item))
		assertEquals(listOf("Close"), plain(second))
	}

	private fun tooltip(vararg lines: String): TooltipEvent = TooltipEvent().also {
		it.hoveredSlot = Slot(SimpleContainer(SLOTS), 0, 0, 0)
		it.stack = ItemFixture.identified("HYPERION")
		it.reuse(lines.map(Component::literal))
	}

	private fun FocusMode.condense(event: TooltipEvent, stack: ItemStack) {
		event.stack = stack
		condense(event)
	}

	private fun plain(event: TooltipEvent): List<String> = event.lines.map { it.string }

	companion object {
		private const val SLOTS = 9

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
