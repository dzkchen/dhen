package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageScrollingTest {
	@Test
	fun `the module carries three settings on the inventory card`() {
		assertEquals(Category.INVENTORY, PageScrolling.category)
		assertEquals(listOf("Bypass Key", "Invert Bypass", "Invert Scroll"), PageScrolling.settings.map { it.name })
	}

	@Test
	fun `both spellings of every forward button step forward`() {
		for (name in listOf("§aNext Page", "§aNext Page →", "§aScroll Up", "§aScroll Right", "§aLevels 26 - 50")) {
			assertEquals(FORWARD_STEP, pageStep(name), name)
		}
	}

	@Test
	fun `both spellings of every backward button step back`() {
		for (name in listOf("§aPrevious Page", "§a← Previous Page", "§aScroll Down", "§aScroll Left", "§aLevels 1 - 25")) {
			assertEquals(BACKWARD_STEP, pageStep(name), name)
		}
	}

	@Test
	fun `a button without its colour code or with extra words is not a page button`() {
		assertEquals(NO_STEP, pageStep("Next Page"))
		assertEquals(NO_STEP, pageStep("§aNext Pages"))
		assertEquals(NO_STEP, pageStep("§eNext Page"))
		assertEquals(NO_STEP, pageStep("§aClose"))
	}

	@Test
	fun `a plain vanilla chest is left alone and a named menu is not`() {
		assertFalse(scrollableMenu("Chest"))
		assertFalse(scrollableMenu("Large Chest"))
		assertFalse(scrollableMenu(""))
		assertTrue(scrollableMenu("Ender Chest (1/9)"))
		assertTrue(scrollableMenu("Auctions Browser"))
	}
}
