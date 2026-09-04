package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.data.repo.RepoItem
import io.github.dzkchen.dhen.data.repo.RepoState
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class QuiverDisplayTest {
	@AfterEach
	fun reset() {
		if (QuiverDisplay.enabled) QuiverDisplay.setEnabled(false)
		for (setting in QuiverDisplay.settings) setting.reset()
		QuiverDisplay.equipment.clear()
		QuiverDisplay.samplingDue(false)
		SkyBlockLocation.reset()
	}

	@Test
	fun `the module declares the requested controls and movable element`() {
		assertEquals("Quiver Display", QuiverDisplay.name)
		assertEquals(Category.VISUAL, QuiverDisplay.category)
		assertEquals(
			listOf("Show Arrow Icon", "Show When", "Low Quiver Alert", "Reminder After Run", "Low Quiver Amount"),
			QuiverDisplay.settings.map { it.name }
		)
		assertEquals(listOf("Quiver Display"), QuiverDisplay.hudElements.map { it.name })
		assertTrue(QuiverDisplay.showIconSetting.default)
		assertEquals(QuiverDisplay.BOW_IN_HAND, QuiverDisplay.showWhenSetting.default)
		assertTrue(QuiverDisplay.lowQuiverSetting.default)
		assertTrue(QuiverDisplay.reminderAfterRunSetting.default)
		assertEquals(100.0, QuiverDisplay.lowQuiverAmountSetting.default)
		assertEquals(50.0, QuiverDisplay.lowQuiverAmountSetting.min)
		assertEquals(500.0, QuiverDisplay.lowQuiverAmountSetting.max)
		assertEquals(50.0, QuiverDisplay.lowQuiverAmountSetting.step)
		assertEquals(
			listOf(QuiverDisplay.ALWAYS, QuiverDisplay.BOW_IN_INVENTORY, QuiverDisplay.BOW_IN_HAND),
			QuiverDisplay.showWhenSetting.options
		)
	}

	@Test
	fun `real bows exclude the two fake ids and selected hand is tracked separately`() {
		val equipment = QuiverEquipment()
		val items = MutableList(36) { ItemStack.EMPTY }
		items[4] = bow("BOSS_SPIRIT_BOW")
		items[12] = bow("CRYPT_BOW")

		assertFalse(equipment.sample(items, items[4]))
		assertFalse(equipment.hasBow)
		assertFalse(equipment.holdingBow)

		items[20] = bow("TERMINATOR")
		assertTrue(equipment.sample(items, items[4]))
		assertTrue(equipment.hasBow)
		assertFalse(equipment.holdingBow)

		assertTrue(equipment.sample(items, items[20]))
		assertTrue(equipment.holdingBow)
		assertFalse(equipment.sample(items, items[20]))
	}

	@Test
	fun `a bow snapshot clears in one call and stays cleared`() {
		val equipment = QuiverEquipment()
		val items = MutableList(36) { ItemStack.EMPTY }
		items[0] = bow("JUJU_SHORTBOW")

		assertTrue(equipment.sample(items, items[0]))
		assertTrue(equipment.hasBow)
		assertTrue(equipment.holdingBow)
		assertTrue(equipment.clear())
		assertFalse(equipment.hasBow)
		assertFalse(equipment.holdingBow)
		assertFalse(equipment.clear())
	}

	@Test
	fun `equipment sampling runs every forty in SkyBlock ticks and restarts after leaving`() {
		repeat(39) { assertFalse(QuiverDisplay.samplingDue(true)) }
		assertTrue(QuiverDisplay.samplingDue(true))
		assertFalse(QuiverDisplay.samplingDue(true))

		repeat(20) { assertFalse(QuiverDisplay.samplingDue(true)) }
		assertFalse(QuiverDisplay.samplingDue(false))
		repeat(39) { assertFalse(QuiverDisplay.samplingDue(true)) }
		assertTrue(QuiverDisplay.samplingDue(true))
	}

	@Test
	fun `all display modes use the equipment snapshot and editor remains discoverable`() {
		val items = MutableList(36) { ItemStack.EMPTY }
		items[8] = bow("TERMINATOR")
		QuiverDisplay.equipment.sample(items, ItemStack.EMPTY)

		assertTrue(QuiverDisplay.shows(QuiverDisplay.ALWAYS, inSkyBlock = true, editing = false))
		assertTrue(QuiverDisplay.shows(QuiverDisplay.BOW_IN_INVENTORY, inSkyBlock = true, editing = false))
		assertFalse(QuiverDisplay.shows(QuiverDisplay.BOW_IN_HAND, inSkyBlock = true, editing = false))
		assertFalse(QuiverDisplay.shows(QuiverDisplay.ALWAYS, inSkyBlock = false, editing = false))
		assertTrue(QuiverDisplay.shows(QuiverDisplay.BOW_IN_HAND, inSkyBlock = false, editing = true))

		QuiverDisplay.equipment.sample(items, items[8])
		assertTrue(QuiverDisplay.shows(QuiverDisplay.BOW_IN_HAND, inSkyBlock = true, editing = false))
	}

	@Test
	fun `display cache groups amounts pluralizes and rebuilds only for changed inputs`() {
		val element = QuiverDisplayElement()
		val arrowItem = {
			RepoItem(
				id = "ARROW",
				itemId = "minecraft:flint",
				displayName = "Flint Arrow",
				damage = 0,
				lore = listOf("§9§lRARE"),
				nbttag = "",
				info = emptyList(),
				recipes = emptyList()
			)
		}
		val item = arrowItem()

		element.refresh(QuiverArrow.FLINT, 2880, false, true, RepoState.READY, "first", item)

		assertEquals("2,880x Flint Arrows", element.shownText)
		assertSame(Items.FLINT, element.shownIcon.item)
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.BLUE)?.value, element.shownNameColor)
		assertEquals(1, element.rebuilds)

		element.refresh(QuiverArrow.FLINT, 2880, false, true, RepoState.READY, "first", arrowItem())
		assertEquals(1, element.rebuilds)

		element.refresh(QuiverArrow.FLINT, 1, false, true, RepoState.READY, "first", item)
		assertEquals("1x Flint Arrow", element.shownText)
		assertEquals(2, element.rebuilds)

		element.refresh(QuiverArrow.FLINT, 1, true, true, RepoState.READY, "first", item)
		assertEquals("Flint Arrow", element.shownText)

		element.refresh(null, 0, false, false, RepoState.UNAVAILABLE, null, null)
		assertEquals("None", element.shownText)
		assertSame(Items.ARROW, element.shownIcon.item)
	}

	@Test
	fun `module controls survive a persistence round trip`() {
		val manager = ModuleManager()
		manager.register(QuiverDisplay)
		try {
			manager.enable(QuiverDisplay)
			QuiverDisplay.showIconSetting.value = false
			QuiverDisplay.showWhenSetting.value = QuiverDisplay.ALWAYS
			QuiverDisplay.lowQuiverSetting.value = false
			QuiverDisplay.reminderAfterRunSetting.value = false
			QuiverDisplay.lowQuiverAmountSetting.value = 350.0
			val saved = ModulePersistence.snapshot(manager)

			manager.disable(QuiverDisplay)
			QuiverDisplay.showIconSetting.reset()
			QuiverDisplay.showWhenSetting.reset()
			QuiverDisplay.lowQuiverSetting.reset()
			QuiverDisplay.reminderAfterRunSetting.reset()
			QuiverDisplay.lowQuiverAmountSetting.reset()
			ModulePersistence.apply(manager, saved)

			assertTrue(QuiverDisplay.enabled)
			assertFalse(QuiverDisplay.showIconSetting.on)
			assertEquals(QuiverDisplay.ALWAYS, QuiverDisplay.showWhenSetting.value)
			assertFalse(QuiverDisplay.lowQuiverSetting.on)
			assertFalse(QuiverDisplay.reminderAfterRunSetting.on)
			assertEquals(350.0, QuiverDisplay.lowQuiverAmountSetting.value)
		} finally {
			manager.unregister(QuiverDisplay)
		}
	}

	@Test
	fun `resetting island transitions clear retained warning state`() {
		val manager = ModuleManager()
		manager.register(QuiverDisplay)
		try {
			manager.enable(QuiverDisplay)
			QuiverDisplay.warning.updated(QuiverArrow.FLINT, 50, true, false, 100)
			assertTrue(QuiverDisplay.warning.used(QuiverArrow.FLINT))

			manager.eventBus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.HUB, Island.CATACOMBS))

			assertFalse(QuiverDisplay.warning.used(QuiverArrow.FLINT))
		} finally {
			manager.unregister(QuiverDisplay)
		}
	}

	private fun bow(id: String): ItemStack = identified(ItemStack(Items.BOW), id)

	private fun identified(stack: ItemStack, id: String): ItemStack = stack.also {
		it.set(DataComponents.CUSTOM_DATA, ItemFixture.customData { putString("id", id) })
	}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
