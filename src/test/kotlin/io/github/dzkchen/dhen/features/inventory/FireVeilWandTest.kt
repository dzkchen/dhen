package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FireVeilWandTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in FireVeilWand.settings) setting.reset()
		FireVeilWand.forget()
		SkyBlockLocation.reset()
	}

	@Test
	fun `the module declares the two controls its source exposes`() {
		assertEquals("Fire Veil Wand", FireVeilWand.name)
		assertEquals(Category.COMBAT, FireVeilWand.category)
		assertEquals(listOf("Fire Veil Design", "Line Color"), FireVeilWand.settings.map { it.name })
		assertEquals(
			listOf(FireVeilWand.PARTICLES, FireVeilWand.LINE, FireVeilWand.OFF),
			FireVeilWand.designSetting.options
		)
		assertEquals(Color.rgba(255, 85, 85, 245), FireVeilWand.lineColorSetting.default)
	}

	@Test
	fun `only the wand starts the five and a half second window`() {
		SkyBlockLocation.located("mini1A", true, "hub", "Hub")
		FireVeilWand.clicked(ItemFixture.identified("ASPECT_OF_THE_END"), 1_000L)
		assertFalse(FireVeilWand.active(1_000L))
		FireVeilWand.clicked(ItemFixture.identified("FIRE_VEIL_WAND"), 1_000L)
		assertTrue(FireVeilWand.active(1_000L))
		assertTrue(FireVeilWand.active(6_500L))
		assertFalse(FireVeilWand.active(6_501L))
	}

	@Test
	fun `a click outside SkyBlock is ignored`() {
		FireVeilWand.clicked(ItemFixture.identified("FIRE_VEIL_WAND"), 2_000L)
		assertFalse(FireVeilWand.active(2_000L))
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
