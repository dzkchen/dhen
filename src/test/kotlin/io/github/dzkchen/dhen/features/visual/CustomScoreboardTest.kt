package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.data.quiver.QuiverState
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.NanoClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomScoreboardTest {
	private var now = 0L

	@AfterEach
	fun reset() {
		if (CustomScoreboard.enabled) CustomScoreboard.setEnabled(false)
		for (setting in CustomScoreboard.settings) setting.reset()
		SidebarValues.reset()
		ScoreboardState.reset()
		QuiverState.reset()
		PartyState.disband()
		SkyBlockLocation.reset()
	}

	@Test
	fun `the module declares its line catalogue and movable element`() {
		assertEquals(listOf("Lines"), CustomScoreboard.settings.map { it.name })
		assertEquals(Category.VISUAL, CustomScoreboard.category)
		assertEquals(listOf("Scoreboard"), CustomScoreboard.hudElements.map { it.name })
		assertEquals(ScoreboardLine.labels, CustomScoreboard.linesSetting.options)
		assertTrue(CustomScoreboard.linesSetting.enabled("Purse"))
	}

	@Test
	fun `enabled lines render in the order the setting lists them`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf("§711/15/24 §8m151AM", " Late Summer 1st", " §7⏣ §bVillage", " Purse: §6100"))

		assertEquals(
			listOf("§8mini1A", "Late Summer 1st", "§7⏣ §bVillage", "§fPurse: §6100"),
			composer().compose(listOf("Lobby Code", "Date", "Location", "Purse"))
		)
	}

	@Test
	fun `the lobby code line reads the server the mod API named, not the sidebar`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf("§711/15/24 §8staleCode"))

		assertEquals(listOf("§8mini1A"), composer().compose(listOf("Lobby Code", "Extra")))
	}

	@Test
	fun `a separator is dropped when it would open the list or double up`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" Purse: §6100"))

		assertEquals(
			listOf("§fPurse: §6100"),
			composer().compose(listOf("Separator 1", "Separator 2", "Purse", "Separator 3"))
		)
	}

	@Test
	fun `the purse hides in the rift and the bits line hides in dungeons`() {
		SidebarValues.read(listOf(" Purse: §6100", " Bits: §b50"))

		inSkyBlock(Island.THE_RIFT)
		assertEquals(listOf("§fBits: §b50"), composer().compose(listOf("Purse", "Bits")))

		inSkyBlock(Island.CATACOMBS)
		assertEquals(listOf("§fPurse: §6100"), composer().compose(listOf("Purse", "Bits")))
	}

	@Test
	fun `a purse change shows a signed difference for five seconds`() {
		inSkyBlock(Island.HUB)
		val composer = composer()
		SidebarValues.read(listOf(" Purse: §61,000"))
		composer.sampled()
		SidebarValues.read(listOf(" Purse: §62,500"))
		composer.sampled()

		assertEquals(listOf("§fPurse: §62,500 §7(§6+1,500§7)"), composer.compose(listOf("Purse")))

		now += 4_000_000_000L
		assertFalse(composer.faded())
		now += 2_000_000_000L
		assertTrue(composer.faded())
		assertEquals(listOf("§fPurse: §62,500"), composer.compose(listOf("Purse")))
	}

	@Test
	fun `the party line lists members and marks the leader, and hides in dungeons`() {
		PartyState.add("Alice")
		PartyState.add("Bob")
		PartyState.lead("Alice")

		inSkyBlock(Island.HUB)
		assertEquals(
			listOf("§9§lParty §f(2)", "§7- §fAlice §e♚", "§7- §fBob"),
			composer().compose(listOf("Party"))
		)

		inSkyBlock(Island.CATACOMBS)
		assertEquals(emptyList<String>(), composer().compose(listOf("Party")))
	}

	@Test
	fun `a leader who joined late still leads the party block`() {
		for (name in listOf("Ann", "Bob", "Cid", "Dot", "Eve", "Fay")) PartyState.add(name)
		PartyState.lead("Eve")
		inSkyBlock(Island.HUB)

		assertEquals(
			listOf("§9§lParty §f(6)", "§7- §fEve §e♚", "§7- §fAnn", "§7- §fBob", "§7- §fCid", "§7- §fDot"),
			composer().compose(listOf("Party"))
		)
	}

	@Test
	fun `no arrow selected draws no quiver line`() {
		inSkyBlock(Island.HUB)
		QuiverState.select(QuiverArrow.NONE)

		assertEquals(emptyList<String>(), composer().compose(listOf("Quiver")))
	}

	@Test
	fun `sidebar lines no typed line claimed are listed as undetected`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" Purse: §6100", " §aPowder: §b1,000"))

		assertEquals(
			listOf("§fPurse: §6100", "§cUndetected Lines:", "§aPowder: §b1,000"),
			composer().compose(listOf("Purse", "Extra"))
		)
	}

	@Test
	fun `outside SkyBlock the vanilla sidebar lines are shown untouched`() {
		SkyBlockLocation.reset()
		ScoreboardState.read(listOf("§aLobby", "§bPlayers: 12"), listOf("Lobby", "Players: 12"))

		assertEquals(listOf("§aLobby", "§bPlayers: 12"), composer().compose(listOf("Purse")))
	}

	@Test
	fun `the element caches at most its slot count and previews while editing`() {
		val element = CustomScoreboardElement()
		element.update("Title", (1..60).map { "Line $it" })

		assertEquals("Title", element.shownTitle(editing = false))
		assertEquals(40, element.shownCount(editing = false))
		assertEquals("Line 1", element.shownLine(0, editing = false))
		assertEquals("Line 40", element.shownLine(39, editing = false))
		assertTrue(element.contentAvailable(editing = false))
		assertEquals("SKYBLOCK", element.shownTitle(editing = true))
		assertTrue(CustomScoreboardElement().contentAvailable(editing = true))
		assertFalse(CustomScoreboardElement().contentAvailable(editing = false))
	}

	@Test
	fun `vanilla sidebar gate follows module state`() {
		assertFalse(CustomScoreboard.shouldHideVanilla())
		CustomScoreboard.setEnabled(true)
		assertTrue(CustomScoreboard.shouldHideVanilla())
		CustomScoreboard.setEnabled(false)
		assertFalse(CustomScoreboard.shouldHideVanilla())
	}

	private fun composer() = ScoreboardComposer(NanoClock { now })

	private fun inSkyBlock(island: Island) =
		SkyBlockLocation.located("mini1A", skyBlock = true, mode = island.modeId, map = null)
}
