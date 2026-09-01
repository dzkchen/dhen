package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.cookie.CookieState
import io.github.dzkchen.dhen.data.maxwell.MaxwellHooks
import io.github.dzkchen.dhen.data.maxwell.MaxwellState
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
	private var epoch = 0L

	@AfterEach
	fun reset() {
		if (CustomScoreboard.enabled) CustomScoreboard.setEnabled(false)
		for (setting in CustomScoreboard.settings) setting.reset()
		SidebarValues.reset()
		ScoreboardState.reset()
		QuiverState.reset()
		PartyState.disband()
		SkyBlockLocation.reset()
		TabWidgetState.reset()
		CookieState.reset()
		MaxwellHooks.uninstall()
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
			listOf("§711/15/24 §8mini1A", "Late Summer 1st", "§7⏣ §bVillage", "§fPurse: §6100"),
			composer().compose(listOf("Lobby Code", "Date", "Location", "Purse"))
		)
	}

	@Test
	fun `the lobby code line reads the server the mod API named, not the sidebar`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf("§711/15/24 §8staleCode"))

		assertEquals(listOf("§711/15/24 §8mini1A"), composer().compose(listOf("Lobby Code", "Extra")))
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

	@Test
	fun `the bank line splits the co-op half off the total, and shows only the total when solo`() {
		inSkyBlock(Island.HUB)
		widget(TabWidget.BANK, "Bank: 10M / 50M")

		assertEquals(listOf("§fBank: §610M §7/ §650M"), composer().compose(listOf("Bank")))

		widget(TabWidget.BANK, "Bank: 249M")
		assertEquals(listOf("§fBank: §6249M"), composer().compose(listOf("Bank")))

		inSkyBlock(Island.THE_RIFT)
		assertEquals(emptyList<String>(), composer().compose(listOf("Bank")))
	}

	@Test
	fun `the player count adds guests and takes its maximum from the visiting line`() {
		inSkyBlock(Island.PRIVATE_ISLAND)
		widget(TabWidget.PLAYER_LIST, "Players (3)")
		widget(TabWidget.GUESTS, "Guests (2)")
		SidebarValues.read(listOf(" §a✌ §7(§a5§7/6§7)"))

		assertEquals(listOf("§fPlayers: §a5§7/§a6"), composer().compose(listOf("Player Count")))
	}

	@Test
	fun `a mega lobby without a visiting line counts up to eighty`() {
		inSkyBlock(Island.HUB)
		SkyBlockLocation.located("mega77CK", skyBlock = true, mode = Island.HUB.modeId, map = null)
		widget(TabWidget.PLAYER_LIST, "Players (69)")

		assertEquals(listOf("§fPlayers: §a69§7/§a80"), composer().compose(listOf("Player Count")))
	}

	@Test
	fun `the visiting line only shows on a personal island`() {
		SidebarValues.read(listOf(" §a✌ §7(§a1§7/6§7)"))

		inSkyBlock(Island.GARDEN)
		assertEquals(listOf("§a✌ §7(§a1§7/6§7)"), composer().compose(listOf("Visiting")))

		inSkyBlock(Island.HUB)
		assertEquals(emptyList<String>(), composer().compose(listOf("Visiting")))
	}

	@Test
	fun `the profile line reads the sidebar marker, then the title, then falls back to normal`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" §7♲ §7Ironman"))
		assertEquals(listOf("§7♲ Ironman"), composer().compose(listOf("Profile")))

		SidebarValues.read(listOf(" §a☀ §aStranded"))
		assertEquals(listOf("§a☀ Stranded"), composer().compose(listOf("Profile")))

		SidebarValues.reset()
		ScoreboardState.heading("objective", "§6SKYBLOCK §7♲")
		assertEquals(listOf("§7♲ Ironman"), composer().compose(listOf("Profile")))

		ScoreboardState.heading("objective", "§6SKYBLOCK")
		assertEquals(listOf("§eNormal"), composer().compose(listOf("Profile")))
	}

	@Test
	fun `motes only show in the rift and copper only in the garden`() {
		SidebarValues.read(listOf(" Motes: §5137,242", " Copper: §c3,416"))

		inSkyBlock(Island.THE_RIFT)
		assertEquals(listOf("§fMotes: §d137,242"), composer().compose(listOf("Motes", "Copper")))

		inSkyBlock(Island.GARDEN)
		assertEquals(listOf("§fCopper: §c3,416"), composer().compose(listOf("Motes", "Copper")))
	}

	@Test
	fun `heat, cold and north stars each show only where SkyBlock shows them`() {
		SidebarValues.read(listOf(" Heat: §c14♨", " Cold: §b-3❄", " North Stars: §d1,539"))
		val lines = listOf("Heat", "Cold", "North Stars")

		inSkyBlock(Island.CRYSTAL_HOLLOWS)
		assertEquals(listOf("§fHeat: §c14♨"), composer().compose(lines))

		inSkyBlock(Island.DWARVEN_MINES)
		assertEquals(listOf("§fCold: §b-3❄"), composer().compose(lines))

		inSkyBlock(Island.JERRYS_WORKSHOP)
		assertEquals(listOf("§fNorth Stars: §d1,539"), composer().compose(lines))
	}

	@Test
	fun `cold shows in the safari only while the sidebar says the icy biome`() {
		SidebarValues.read(listOf(" Cold: §b-3❄"))
		inSkyBlock(Island.CRITTER_SAFARI)

		ScoreboardState.locate("Savanna Woodland")
		assertEquals(emptyList<String>(), composer().compose(listOf("Cold")))

		ScoreboardState.locate("Icy Biome")
		assertEquals(listOf("§fCold: §b-3❄"), composer().compose(listOf("Cold")))
	}

	@Test
	fun `the profile type is held while visiting somebody else's island`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" §7♲ §7Ironman"))
		val composer = composer()
		assertEquals(listOf("§7♲ Ironman"), composer.compose(listOf("Profile")))

		SkyBlockLocation.located("mini1A", skyBlock = true, mode = Island.PRIVATE_ISLAND.modeId, map = null)
		SkyBlockLocation.titled("SKYBLOCK GUEST")
		SidebarValues.reset()

		assertEquals(listOf("§7♲ Ironman"), composer.compose(listOf("Profile")))
	}

	@Test
	fun `the powder block opens once and lists every kind the sidebar carries`() {
		inSkyBlock(Island.CRYSTAL_HOLLOWS)
		SidebarValues.read(listOf(" §2᠅ §fMithril§f: §235,448", " §d᠅ §fGemstone Powder§f: §d36,758"))

		assertEquals(
			listOf("§9§lPowder", "§7- §fMithril: §235,448", "§7- §fGemstone: §d36,758"),
			composer().compose(listOf("Powder"))
		)

		inSkyBlock(Island.HUB)
		assertEquals(emptyList<String>(), composer().compose(listOf("Powder")))
	}

	@Test
	fun `soulflow and gems read the tab list, and the copper diff follows the tab list too`() {
		inSkyBlock(Island.GARDEN)
		widget(TabWidget.SOULFLOW, "Soulflow: 761")
		widget(TabWidget.GEMS, "Gems: 57,873")
		widget(TabWidget.COPPER, "Copper: 100")
		val composer = composer()
		composer.sampled()
		widget(TabWidget.COPPER, "Copper: 150")
		composer.sampled()

		assertEquals(
			listOf("§fGems: §a57,873", "§fSoulflow: §3761", "§fCopper: §c150 §7(§c+50§7)"),
			composer.compose(listOf("Gems", "Soulflow", "Copper"))
		)
	}

	@Test
	fun `the objective block renders the lines the sidebar claimed for it`() {
		inSkyBlock(Island.HUB)
		SidebarValues.read(listOf(" Objective", " §eProtect Elle §7(§a98%§7)", " §7(§e1§7/§a100§7)"))

		assertEquals(
			listOf("Objective", "§eProtect Elle §7(§a98%§7)", "§7(§e1§7/§a100§7)"),
			composer().compose(listOf("Objective", "Extra"))
		)
	}

	@Test
	fun `the SkyBlock level line pairs the level with its experience out of a hundred`() {
		inSkyBlock(Island.HUB)
		widget(TabWidget.SB_LEVEL, "SB Level: [287] 26")

		assertEquals(
			listOf("§fSB Level: §9287", "§fXP: §b26§3/§b100"),
			composer().compose(listOf("SkyBlock XP"))
		)
	}

	@Test
	fun `the quiver line shows infinity while the Skeleton Master chestplate is worn`() {
		inSkyBlock(Island.HUB)
		QuiverState.select(QuiverArrow.FLINT)
		QuiverState.setAmount(QuiverArrow.FLINT, 1_234)

		assertEquals(listOf("§fFlint Arrow: §f1,234"), composer().compose(listOf("Quiver")))

		QuiverState.wearInfinite(true)
		assertEquals(listOf("§fFlint Arrow: §f∞"), composer().compose(listOf("Quiver")))
	}

	@Test
	fun `power and tuning name the menu to open until the menu has been read`() {
		inSkyBlock(Island.HUB)

		assertEquals(
			listOf("§cOpen \"Your Bags\"!", "§cTalk to \"Maxwell\"!"),
			composer().compose(listOf("Power", "Tuning"))
		)
	}

	@Test
	fun `the cookie line counts down, says not active, and asks for the menu when unread`() {
		inSkyBlock(Island.HUB)
		assertEquals(listOf("§dCookie Buff§f: §cOpen SB Menu!"), composer().compose(listOf("Cookie Buff")))

		CookieState.expires(CookieState.EXPIRED)
		epoch = 10_000L
		assertEquals(listOf("§dCookie Buff§f: §cNot Active"), composer().compose(listOf("Cookie Buff")))

		CookieState.expires(epoch + 3 * 86_400_000L + 17 * 3_600_000L)
		assertEquals(listOf("§dCookie Buff§f: 3d 17h"), composer().compose(listOf("Cookie Buff")))
	}

	@Test
	fun `a live countdown asks for a rebuild once a second and never before`() {
		inSkyBlock(Island.HUB)
		val composer = composer()
		assertFalse(composer.ticked())

		CookieState.expires(600_000L)
		composer.compose(listOf("Cookie Buff"))
		assertTrue(composer.ticked())
		assertFalse(composer.ticked())

		epoch += 1_000L
		assertTrue(composer.ticked())
	}

	private fun composer() = ScoreboardComposer(NanoClock { now }, { epoch })

	private fun widget(widget: TabWidget, line: String) =
		TabWidgetState.read(widget, listOf(line), listOf(line))

	private fun inSkyBlock(island: Island) {
		SkyBlockLocation.located("mini1A", skyBlock = true, mode = island.modeId, map = null, resolvedIsland = island)
		if (SkyBlockLocation.awaitingGuestTitle) SkyBlockLocation.titled("SKYBLOCK")
	}
}
