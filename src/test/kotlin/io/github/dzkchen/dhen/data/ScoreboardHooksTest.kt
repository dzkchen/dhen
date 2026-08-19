package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.ScoreboardAreaChangeEvent
import io.github.dzkchen.dhen.event.ScoreboardUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldHooks
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScoreboardHooksTest {
	private val bus = EventBus()
	private val updates = mutableListOf<ScoreboardUpdateEvent>()
	private val areas = mutableListOf<ScoreboardAreaChangeEvent>()
	private var scoreboard: Scoreboard? = null
	private var objective: Objective? = null

	@BeforeEach
	fun install() {
		SkyBlockLocation.reset()
		ScoreboardHooks.install(bus) { scoreboard }
		bus.subscribe<ScoreboardUpdateEvent> { updates += it }
		bus.subscribe<ScoreboardAreaChangeEvent> { areas += it }
	}

	@AfterEach
	fun uninstall() {
		ScoreboardHooks.uninstall()
		HypixelLocationHooks.uninstall()
		SkyBlockLocation.reset()
	}

	@Test
	fun `the sidebar reads top to bottom with the vanilla score order`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 3, "Bank: 10M" to 1, " ⏣ Village" to 2)
		ScoreboardHooks.refresh()

		assertEquals(listOf("Purse: 1,234", " ⏣ Village", "Bank: 10M"), ScoreboardState.stripped)
		assertEquals("SKYBLOCK", ScoreboardState.title)
		assertEquals("SBScoreboard", ScoreboardState.objective)
	}

	@Test
	fun `only the fifteen highest scores are read`() {
		sidebar("SKYBLOCK", *Array(20) { "line $it" to it })
		ScoreboardHooks.refresh()

		assertEquals(15, ScoreboardState.lines.size)
		assertEquals("line 19", ScoreboardState.stripped.first())
		assertEquals("line 5", ScoreboardState.stripped.last())
	}

	@Test
	fun `a line the server hid is not a line`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 2)
		team("#hidden", Component.literal("Bank: 10M"), Component.empty(), 1)
		ScoreboardHooks.refresh()

		assertEquals(listOf("Purse: 1,234"), ScoreboardState.stripped)
	}

	@Test
	fun `the split colour code is written once, not twice`() {
		sidebar("SKYBLOCK")
		team("holder0", Component.literal("- ").withStyle(ChatFormatting.RED), Component.literal("Dragon").withStyle(ChatFormatting.RED), 1)
		ScoreboardHooks.refresh()

		assertEquals(listOf("§c- Dragon"), ScoreboardState.lines)
	}

	@Test
	fun `a line with no team of its own is read from its score holder`() {
		sidebar("SKYBLOCK")
		val board = scoreboard!!
		board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Steve"), objective!!).set(1)
		ScoreboardHooks.refresh()

		assertEquals(listOf("Steve"), ScoreboardState.stripped)
	}

	@Test
	fun `the area line becomes the scoreboard area`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 2)
		team("holder1", areaPrefix(), Component.literal("Village").withStyle(ChatFormatting.AQUA), 1)
		ScoreboardHooks.refresh()

		assertEquals("Village", ScoreboardState.area)
		assertEquals(listOf(null to "Village"), areas.map { it.previous to it.area })
	}

	@Test
	fun `a scoreboard with no area line leaves the area unset`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()

		assertNull(ScoreboardState.area)
		assertTrue(areas.isEmpty())
	}

	@Test
	fun `a sidebar that did not change publishes nothing the second time`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()
		ScoreboardHooks.refresh()

		assertEquals(1, updates.size)
		assertEquals(listOf(emptyList<String>()), updates.map { it.previous })
	}

	@Test
	fun `a scoreboard packet is read on the next tick, not in the handler`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		bus.type<PacketReceiveEvent.Post>().dispatch(PacketReceiveEvent.Post().also { it.packet = ClientboundResetScorePacket("holder0", "SBScoreboard") })

		assertTrue(updates.isEmpty())

		bus.type<ClientTickEvent.Start>().dispatch(ClientTickEvent.Start)

		assertEquals(1, updates.size)
		bus.type<ClientTickEvent.Start>().dispatch(ClientTickEvent.Start)
		assertEquals(1, updates.size)
	}

	@Test
	fun `losing the sidebar clears the lines`() {
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()
		scoreboard = null
		ScoreboardHooks.refresh()

		assertTrue(ScoreboardState.lines.isEmpty())
		assertEquals(2, updates.size)
	}

	@Test
	fun `joining a world forgets the sidebar it read on the last one`() {
		WorldHooks.install(bus)
		try {
			sidebar("SKYBLOCK", "Purse: 1,234" to 1)
			ScoreboardHooks.refresh()
			WorldHooks.worldChanged(WorldChange.JOIN)
		} finally {
			WorldHooks.uninstall()
		}

		assertTrue(ScoreboardState.lines.isEmpty())
		assertEquals("", ScoreboardState.title)
	}

	@Test
	fun `a guest title settles the island the location packet could not`() {
		val islands = mutableListOf<IslandChangeEvent>()
		HypixelLocationHooks.install(bus)
		bus.subscribe<IslandChangeEvent> { islands += it }
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "dynamic", map = "Private Island")

		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertTrue(SkyBlockLocation.awaitingGuestTitle)

		sidebar("SKYBLOCK GUEST", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()

		assertEquals(Island.PRIVATE_ISLAND_GUEST, SkyBlockLocation.island)
		assertTrue(SkyBlockLocation.isGuest)
		assertFalse(SkyBlockLocation.awaitingGuestTitle)
		assertEquals(listOf(Island.NONE to Island.PRIVATE_ISLAND_GUEST), islands.map { it.previous to it.island })
	}

	@Test
	fun `a title without GUEST settles the island as your own`() {
		HypixelLocationHooks.install(bus)
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "garden", map = "Garden")
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()

		assertEquals(Island.GARDEN, SkyBlockLocation.island)
		assertFalse(SkyBlockLocation.isGuest)
	}

	@Test
	fun `a location packet landing on a sidebar already read settles the island at once`() {
		HypixelLocationHooks.install(bus)
		sidebar("SKYBLOCK GUEST", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "dynamic", map = "Private Island")

		assertEquals(Island.PRIVATE_ISLAND_GUEST, SkyBlockLocation.island)
		assertFalse(SkyBlockLocation.awaitingGuestTitle)
	}

	@Test
	fun `a lobby scoreboard never settles a skyblock island`() {
		HypixelLocationHooks.install(bus)
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "dynamic", map = "Private Island")
		sidebar("HYPIXEL", "Rank: VIP" to 1, objectiveName = "MainScoreboard")
		ScoreboardHooks.refresh()

		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertTrue(SkyBlockLocation.awaitingGuestTitle)
	}

	@Test
	fun `an uninstalled feed reads nothing`() {
		ScoreboardHooks.uninstall()
		sidebar("SKYBLOCK", "Purse: 1,234" to 1)
		ScoreboardHooks.refresh()

		assertFalse(ScoreboardHooks.active())
		assertTrue(ScoreboardState.lines.isEmpty())
		assertTrue(updates.isEmpty())
	}

	private fun sidebar(title: String, vararg lines: Pair<String, Int>, objectiveName: String = "SBScoreboard") {
		val board = Scoreboard()
		scoreboard = board
		objective = board.addObjective(
			objectiveName,
			ObjectiveCriteria.DUMMY,
			Component.literal(title),
			ObjectiveCriteria.RenderType.INTEGER,
			false,
			null
		).also { board.setDisplayObjective(DisplaySlot.SIDEBAR, it) }
		lines.forEachIndexed { index, (text, score) ->
			team("holder$index", Component.literal(text), Component.empty(), score)
		}
	}

	private fun areaPrefix(): Component =
		Component.literal(" ").append(Component.literal("⏣ ").withStyle(ChatFormatting.GRAY))

	private fun team(holder: String, prefix: Component, suffix: Component, score: Int) {
		val board = scoreboard!!
		val team: PlayerTeam = board.addPlayerTeam("team_$holder")
		team.setPlayerPrefix(prefix)
		team.setPlayerSuffix(suffix)
		board.addPlayerToTeam(holder, team)
		board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective!!).set(score)
	}
}
