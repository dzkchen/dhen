package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PetDisplayTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in PetDisplay.settings) setting.reset()
		CurrentPet.reset()
	}

	@Test
	fun `the module declares its five settings and no movable element`() {
		assertEquals("Pet Display", PetDisplay.name)
		assertEquals(Category.VISUAL, PetDisplay.category)
		assertTrue(PetDisplay.hudElements.isEmpty())
		assertEquals(
			listOf(
				"Auto Pet Title",
				"Dungeons Only",
				"Hide Autopet Messages",
				"Highlight Active Pet",
				"Highlight Color"
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
}
