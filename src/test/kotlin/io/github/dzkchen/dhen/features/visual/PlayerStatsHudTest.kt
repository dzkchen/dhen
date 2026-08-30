package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.data.stats.ActionBarSegment
import io.github.dzkchen.dhen.data.stats.PlayerStats
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerStatsHudTest {
	@AfterEach
	fun reset() {
		if (PlayerStatsHud.enabled) PlayerStatsHud.setEnabled(false)
		for (setting in PlayerStatsHud.settings) setting.reset()
		PlayerStatsHud.clearHiddenSegments()
		PlayerStats.reset()
	}

	@Test
	fun `the module declares every setting and movable element`() {
		assertEquals("Player Stats HUD", PlayerStatsHud.name)
		assertEquals(Category.VISUAL, PlayerStatsHud.category)
		assertEquals(7, PlayerStatsHud.hudElements.size)
		assertEquals(
			listOf("Health", "Defense", "Mana", "Overflow Mana", "Vitality", "Effective HP", "Speed"),
			PlayerStatsHud.hudElements.map { it.name }
		)
		assertEquals(
			listOf(
				"Health",
				"Health Color",
				"Defense",
				"Defense Color",
				"Mana",
				"Mana Color",
				"Overflow Mana",
				"Overflow Mana Color",
				"Vitality",
				"Vitality Color",
				"Effective HP",
				"Effective HP Color",
				"Speed",
				"Speed Color",
				"Separate Overflow Mana",
				"Hide 0 Overflow",
				"Show Icons",
				"Hide Health From Action Bar",
				"Hide Defense From Action Bar",
				"Hide Mana From Action Bar",
				"Hide Overflow Mana From Action Bar",
				"Hide Vitality From Action Bar",
				"Hide Dungeon Room Secrets From Action Bar",
				"Hide Armor Stacks From Action Bar",
				"Hide Terminator Stacks From Action Bar",
				"Hide Hearts",
				"Hide Food",
				"Hide Armor",
				"Hide XP"
			),
			PlayerStatsHud.settings.map { it.name }
		)
		assertFalse(PlayerStatsHud.healthSetting.default)
		assertTrue(PlayerStatsHud.separateOverflowSetting.default)
		assertTrue(PlayerStatsHud.hideZeroOverflowSetting.default)
		assertTrue(PlayerStatsHud.showIconsSetting.default)
		assertTrue(PlayerStatsHud.hideHealthSetting.default)
		assertFalse(PlayerStatsHud.hideSecretsSetting.default)
		assertFalse(PlayerStatsHud.hideArmorStacksSetting.default)
		assertFalse(PlayerStatsHud.hideTerminatorStacksSetting.default)
		assertFalse(PlayerStatsHud.hideHeartsSetting.default)
	}

	@Test
	fun `color settings follow their element toggles`() {
		assertFalse(PlayerStatsHud.healthColorSetting.isVisible)

		PlayerStatsHud.healthSetting.on = true

		assertTrue(PlayerStatsHud.healthColorSetting.isVisible)
	}

	@Test
	fun `stat text groups numbers and follows icon and overflow controls`() {
		PlayerStats.health = 3000
		PlayerStats.maxHealth = 4000
		PlayerStats.mana = 2000
		PlayerStats.maxMana = 20000
		PlayerStats.overflowMana = 333
		PlayerStatsHud.refresh()

		assertEquals("3,000/4,000❤", PlayerStatsHud.healthElement.liveText)
		assertEquals("2,000/20,000✎", PlayerStatsHud.manaElement.liveText)
		assertEquals("333ʬ", PlayerStatsHud.overflowElement.liveText)

		PlayerStatsHud.separateOverflowSetting.on = false
		PlayerStatsHud.showIconsSetting.on = false
		PlayerStatsHud.refresh()

		assertEquals("2,000/20,000", PlayerStatsHud.manaElement.liveText)
		PlayerStatsHud.overflowSetting.on = true
		PlayerStatsHud.refresh()

		assertEquals("2,000/20,000 333", PlayerStatsHud.manaElement.liveText)
		assertEquals("", PlayerStatsHud.overflowElement.liveText)
	}

	@Test
	fun `zero and vitality rules suppress only the specified elements`() {
		PlayerStats.health = 0
		PlayerStats.maxHealth = 0
		PlayerStats.defense = 0
		PlayerStats.overflowMana = 0
		PlayerStats.vitality = 82
		PlayerStats.maxVitality = 122
		PlayerStats.speed = 0
		PlayerStatsHud.refresh()

		assertEquals("0/0❤", PlayerStatsHud.healthElement.liveText)
		assertEquals("", PlayerStatsHud.defenseElement.liveText)
		assertEquals("", PlayerStatsHud.overflowElement.liveText)
		assertEquals("", PlayerStatsHud.vitalityElement.liveText)
		assertEquals("", PlayerStatsHud.speedElement.liveText)

		PlayerStats.vitalityShown = true
		PlayerStatsHud.hideZeroOverflowSetting.on = false
		PlayerStatsHud.refresh()

		assertEquals("0ʬ", PlayerStatsHud.overflowElement.liveText)
		assertEquals("82/122♨", PlayerStatsHud.vitalityElement.liveText)
	}

	@Test
	fun `editor examples stay available without live SkyBlock data`() {
		PlayerStatsHud.healthSetting.on = true
		PlayerStatsHud.refresh()

		assertFalse(PlayerStatsHud.healthElement.contentAvailable(inSkyBlock = false, editing = false))
		assertTrue(PlayerStatsHud.healthElement.contentAvailable(inSkyBlock = false, editing = true))
		assertEquals("3,000/4,000❤", PlayerStatsHud.healthElement.shownText(editing = true))
	}

	@Test
	fun `enable and disable synchronize every action bar hide flag`() {
		val manager = ModuleManager()
		manager.register(PlayerStatsHud)
		try {
			manager.enable(PlayerStatsHud)

			assertTrue(PlayerStats.hidden(ActionBarSegment.HEALTH))
			assertTrue(PlayerStats.hidden(ActionBarSegment.DEFENSE))
			assertTrue(PlayerStats.hidden(ActionBarSegment.MANA))
			assertTrue(PlayerStats.hidden(ActionBarSegment.OVERFLOW_MANA))
			assertTrue(PlayerStats.hidden(ActionBarSegment.VITALITY))
			assertFalse(PlayerStats.hidden(ActionBarSegment.SECRETS))
			assertFalse(PlayerStats.hidden(ActionBarSegment.ARMOR_STACKS))
			assertFalse(PlayerStats.hidden(ActionBarSegment.TERMINATOR_STACKS))

			PlayerStatsHud.hideHealthSetting.on = false
			PlayerStatsHud.hideSecretsSetting.on = true
			PlayerStatsHud.hideArmorStacksSetting.on = true
			PlayerStatsHud.hideTerminatorStacksSetting.on = true
			PlayerStatsHud.synchronizeHiddenSegments()

			assertFalse(PlayerStats.hidden(ActionBarSegment.HEALTH))
			assertTrue(PlayerStats.hidden(ActionBarSegment.SECRETS))
			assertTrue(PlayerStats.hidden(ActionBarSegment.ARMOR_STACKS))
			assertTrue(PlayerStats.hidden(ActionBarSegment.TERMINATOR_STACKS))

			manager.disable(PlayerStatsHud)
			for (segment in ActionBarSegment.entries) assertFalse(PlayerStats.hidden(segment))
			PlayerStatsHud.resetSettings()
			for (segment in ActionBarSegment.entries) assertFalse(PlayerStats.hidden(segment))
		} finally {
			manager.unregister(PlayerStatsHud)
		}
	}

	@Test
	fun `vanilla overlays require the module setting and SkyBlock`() {
		assertFalse(PlayerStatsHud.hidesVanilla(selected = true, inSkyBlock = true))
		PlayerStatsHud.setEnabled(true)
		assertFalse(PlayerStatsHud.hidesVanilla(selected = false, inSkyBlock = true))
		assertFalse(PlayerStatsHud.hidesVanilla(selected = true, inSkyBlock = false))
		assertTrue(PlayerStatsHud.hidesVanilla(selected = true, inSkyBlock = true))
	}

	@Test
	fun `number formatting handles negatives without temporary formatters`() {
		val toggle = BooleanSetting("Speed", true)
		val color = ColorSetting("Speed Color", Color.rgba(255, 255, 255))
		val element = PlayerStatElement(PlayerStat.SPEED, toggle) { color.value }
		PlayerStats.speed = -1234567

		element.update(
			showIcons = true,
			separateOverflow = true,
			hideZeroOverflow = true,
			overflowEnabled = true
		)

		assertEquals("-1,234,567✦", element.liveText)
	}
}
