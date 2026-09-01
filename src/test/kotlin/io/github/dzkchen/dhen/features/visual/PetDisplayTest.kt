package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class PetDisplayTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in PetDisplay.settings) setting.reset()
		CurrentPet.reset()
	}

	@Test
	fun `the module declares its twenty eight settings and no movable element`() {
		assertEquals("Pet Display", PetDisplay.name)
		assertEquals(Category.VISUAL, PetDisplay.category)
		assertTrue(PetDisplay.hudElements.isEmpty())
		assertEquals(11, PetDisplay.subscriptionCount)
		assertEquals(
			listOf(
				"Auto Pet Title",
				"Dungeons Only",
				"Hide Autopet Messages",
				"Highlight Active Pet",
				"Highlight Color",
				"Hide Pet Level",
				"Hide Max Pet Level",
				"Pet Candy Used",
				"Hide On Maxed",
				"Show Exp Share",
				"Show Tier Boost",
				"Pet Item Scale",
				"Wheel Scale",
				"Show Key Labels",
				"Favourite Pets Only",
				"Segment Color",
				"Hover Color",
				"Separator Color",
				"Use Hotbar Binds",
				"Pet Slot 1",
				"Pet Slot 2",
				"Pet Slot 3",
				"Pet Slot 4",
				"Pet Slot 5",
				"Pet Slot 6",
				"Pet Slot 7",
				"Pet Slot 8",
				"Pet Slot 9"
			),
			PetDisplay.settings.map { it.name }
		)
	}

	@Test
	fun `the two dependent settings hide until their parent is on`() {
		assertFalse(PetDisplay.dungeonsOnlySetting.isVisible)
		assertFalse(PetDisplay.highlightColorSetting.isVisible)

		PetDisplay.autoPetTitleSetting.on = true
		PetDisplay.highlightSetting.on = true

		assertTrue(PetDisplay.dungeonsOnlySetting.isVisible)
		assertTrue(PetDisplay.highlightColorSetting.isVisible)
	}

	@Test
	fun `Hide On Maxed rides on the candy count that it hides`() {
		assertTrue(PetDisplay.hideOnMaxedSetting.isVisible)

		PetDisplay.petCandySetting.on = false

		assertFalse(PetDisplay.hideOnMaxedSetting.isVisible)
	}

	@Test
	fun `the pet item scale hides once neither icon is drawn`() {
		assertTrue(PetDisplay.petItemScaleSetting.isVisible)

		PetDisplay.expShareSetting.on = false

		assertTrue(PetDisplay.petItemScaleSetting.isVisible)

		PetDisplay.tierBoostSetting.on = false

		assertFalse(PetDisplay.petItemScaleSetting.isVisible)
	}

	@Test
	fun `the candy count is on and the level hiders are off out of the box`() {
		assertTrue(PetDisplay.petCandySetting.on)
		assertTrue(PetDisplay.expShareSetting.on)
		assertTrue(PetDisplay.tierBoostSetting.on)
		assertFalse(PetDisplay.hideOnMaxedSetting.on)
		assertFalse(PetDisplay.hidePetLevelSetting.on)
		assertFalse(PetDisplay.hideMaxPetLevelSetting.on)
	}

	@Test
	fun `the pet item scale keeps the ported default rather than snapping off its grid`() {
		assertEquals(0.9, PetDisplay.petItemScaleSetting.value)
	}

	@Test
	fun `no title fires while Auto Pet Title is off`() {
		assertFalse(PetDisplay.titled("§6Mosquito", dungeon = true))
	}

	@Test
	fun `the title fires anywhere until Dungeons Only is on`() {
		PetDisplay.autoPetTitleSetting.on = true

		assertTrue(PetDisplay.titled("§6Mosquito", dungeon = false))

		PetDisplay.dungeonsOnlySetting.on = true

		assertFalse(PetDisplay.titled("§6Mosquito", dungeon = false))
		assertTrue(PetDisplay.titled("§6Mosquito", dungeon = true))
	}

	@Test
	fun `an empty pet name never becomes a title`() {
		PetDisplay.autoPetTitleSetting.on = true

		assertFalse(PetDisplay.titled("", dungeon = true))
	}

	@Test
	fun `the highlight colour defaults to opaque cyan`() {
		assertEquals(0xFF00FFFF.toInt(), PetDisplay.highlightColorSetting.value.argb)
	}

	@Test
	fun `pet wheel settings keep the reference defaults and bounds`() {
		assertEquals(100.0, PetDisplay.wheelScaleSetting.value)
		PetDisplay.wheelScaleSetting.value = 68.0
		assertEquals(70.0, PetDisplay.wheelScaleSetting.value)
		PetDisplay.wheelScaleSetting.value = 138.0
		assertEquals(135.0, PetDisplay.wheelScaleSetting.value)
		PetDisplay.wheelScaleSetting.value = 112.0
		assertEquals(110.0, PetDisplay.wheelScaleSetting.value)
		assertTrue(PetDisplay.showKeyLabelsSetting.on)
		assertFalse(PetDisplay.favouritePetsOnlySetting.on)
		assertFalse(PetDisplay.useHotbarBindsSetting.on)
		assertEquals(Color.rgba(15, 15, 15, 200), PetDisplay.segmentColorSetting.value)
		assertEquals(Color.rgba(255, 255, 255, 30), PetDisplay.hoverColorSetting.value)
		assertEquals(Color.rgba(255, 255, 255, 40), PetDisplay.separatorColorSetting.value)
		assertEquals((GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9).toList(), PetDisplay.petSlotSettings.map { it.code })
	}

	@Test
	fun `custom pet slot binds hide while vanilla hotbar binds are selected`() {
		assertTrue(PetDisplay.petSlotSettings.all { it.isVisible })

		PetDisplay.useHotbarBindsSetting.on = true

		assertTrue(PetDisplay.petSlotSettings.none { it.isVisible })
	}

	@Test
	fun `wheel controls persist through the module setting codec`() {
		PetDisplay.wheelScaleSetting.value = 135.0
		PetDisplay.favouritePetsOnlySetting.on = true
		PetDisplay.segmentColorSetting.value = Color.rgba(9, 8, 7, 6)
		PetDisplay.petSlot1Setting.value = GLFW.GLFW_MOUSE_BUTTON_5
		val stored = SettingCodec.writeInto(JsonObject(), PetDisplay.settings)

		for (setting in PetDisplay.settings) setting.reset()
		SettingCodec.readInto(stored, PetDisplay.settings, PetDisplay.name)

		assertEquals(135.0, PetDisplay.wheelScaleSetting.value)
		assertTrue(PetDisplay.favouritePetsOnlySetting.on)
		assertEquals(Color.rgba(9, 8, 7, 6), PetDisplay.segmentColorSetting.value)
		assertEquals(GLFW.GLFW_MOUSE_BUTTON_5, PetDisplay.petSlot1Setting.code)
	}
}
