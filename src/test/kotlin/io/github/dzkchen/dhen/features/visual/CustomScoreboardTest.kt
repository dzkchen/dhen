package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomScoreboardTest {
	@AfterEach
	fun reset() {
		if (CustomScoreboard.enabled) CustomScoreboard.setEnabled(false)
		for (setting in CustomScoreboard.settings) setting.reset()
	}

	@Test
	fun `the module declares its setting and movable element`() {
		assertEquals("Custom Scoreboard", CustomScoreboard.name)
		assertEquals(Category.VISUAL, CustomScoreboard.category)
		assertEquals(listOf("Hide Server ID"), CustomScoreboard.settings.map { it.name })
		assertEquals(listOf("Scoreboard"), CustomScoreboard.hudElements.map { it.name })
		assertFalse(CustomScoreboard.hideServerIdSetting.default)
	}

	@Test
	fun `updates cache at most fifteen source ordered lines`() {
		val element = element()
		val lines = (1..20).map { "Line $it" }

		element.update("Title", lines)

		assertEquals("Title", element.shownTitle(editing = false))
		assertEquals(15, element.shownCount(editing = false))
		assertEquals("Line 1", element.shownLine(0, editing = false, inSkyBlock = true))
		assertEquals("Line 15", element.shownLine(14, editing = false, inSkyBlock = true))
	}

	@Test
	fun `hide server id rewrites only the first SkyBlock line`() {
		val element = CustomScoreboardElement()
		element.update("Title", listOf("§7Late Summer 1st §8m151AM", "Village"))

		assertEquals("§7Late Summer 1st §8m151AM", element.shownLine(0, editing = false, inSkyBlock = true))
		CustomScoreboard.hideServerId = true
		assertEquals("§7Date: §7Late Summer 1st", element.shownLine(0, editing = false, inSkyBlock = true))
		assertEquals("Village", element.shownLine(1, editing = false, inSkyBlock = true))
		assertEquals("§7Late Summer 1st §8m151AM", element.shownLine(0, editing = false, inSkyBlock = false))
	}

	@Test
	fun `empty live cache stays hidden while the editor has a preview`() {
		val element = element()

		assertFalse(element.contentAvailable(editing = false))
		assertTrue(element.contentAvailable(editing = true))
		assertEquals("SKYBLOCK", element.shownTitle(editing = true))
		assertTrue(element.shownCount(editing = true) > 0)
	}

	@Test
	fun `vanilla sidebar gate follows module state`() {
		assertFalse(CustomScoreboard.shouldHideVanilla())
		CustomScoreboard.setEnabled(true)
		assertTrue(CustomScoreboard.shouldHideVanilla())
		CustomScoreboard.setEnabled(false)
		assertFalse(CustomScoreboard.shouldHideVanilla())
	}

	private fun element(): CustomScoreboardElement = CustomScoreboardElement()
}
