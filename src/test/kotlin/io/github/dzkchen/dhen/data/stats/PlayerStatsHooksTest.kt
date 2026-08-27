package io.github.dzkchen.dhen.data.stats

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.event.ActionBarEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PlayerStatsEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlayerStatsHooksTest {
	private val bus = EventBus()
	private var inSkyBlock = true
	private var inDungeon = false
	private var healthRatio = -1f
	private var walkSpeed = 0.0
	private var updates = 0

	@BeforeEach
	fun install() {
		PlayerStatsHooks.install(bus, { inSkyBlock }, { inDungeon }, { healthRatio }, { walkSpeed })
		bus.subscribe<PlayerStatsEvent> { updates++ }
	}

	@AfterEach
	fun uninstall() {
		for (segment in ActionBarSegment.entries) PlayerStats.hide(segment, false)
		PlayerStatsHooks.uninstall()
	}

	@Test
	fun `the legacy symbol form of every segment is parsed`() {
		read("§c1,530/1,530❤     §a1,204❈ Defense     §b1,050/1,050✎ Mana")

		assertEquals(1530, PlayerStats.health)
		assertEquals(1530, PlayerStats.maxHealth)
		assertEquals(1204, PlayerStats.defense)
		assertEquals(1050, PlayerStats.mana)
		assertEquals(1050, PlayerStats.maxMana)
	}

	@Test
	fun `the private-use glyph form of every segment is parsed`() {
		read("§c1,530/1,530     §a1,204 Defense     §b1,050/1,050")

		assertEquals(1530, PlayerStats.health)
		assertEquals(1530, PlayerStats.maxHealth)
		assertEquals(1204, PlayerStats.defense)
		assertEquals(1050, PlayerStats.mana)
		assertEquals(1050, PlayerStats.maxMana)
	}

	@Test
	fun `overflow mana reads both forms and resets when the line drops it`() {
		read("§b1,050/1,050✎ Mana     §3600ʬ")
		assertEquals(600, PlayerStats.overflowMana)

		read("§b1,050/1,050✎ Mana     §3450")
		assertEquals(450, PlayerStats.overflowMana)

		read("§b1,050/1,050✎ Mana")
		assertEquals(0, PlayerStats.overflowMana)
	}

	@Test
	fun `vitality reads both forms and its shown flag follows the line`() {
		read("§482.5/122♨ Vitality")

		assertEquals(82, PlayerStats.vitality)
		assertEquals(122, PlayerStats.maxVitality)
		assertTrue(PlayerStats.vitalityShown)

		read("§4100/122")
		assertEquals(100, PlayerStats.vitality)
		assertTrue(PlayerStats.vitalityShown)

		read("§c1,530/1,530❤")
		assertFalse(PlayerStats.vitalityShown)
		assertEquals(100, PlayerStats.vitality)
	}

	@Test
	fun `nether armor stacks and salvation are read with their symbols`() {
		read("§610⁑     §aT3!")

		assertEquals(10, PlayerStats.netherArmorStacks)
		assertEquals("⁑", PlayerStats.stackSymbol)
		assertEquals(3, PlayerStats.salvation)
	}

	@Test
	fun `secrets are read only in a dungeon and cleared everywhere else`() {
		inDungeon = true
		read("§76/10 Secrets")

		assertEquals(6, PlayerStats.secrets)
		assertEquals(10, PlayerStats.maxSecrets)

		inDungeon = false
		read("§76/10 Secrets")

		assertEquals(0, PlayerStats.secrets)
		assertEquals(0, PlayerStats.maxSecrets)
	}

	@Test
	fun `an ability cost lowers mana immediately and never below zero`() {
		read("§b1,050/1,050✎ Mana")
		read("§b-50 Mana (§6Speed Boost§b)")

		assertEquals(1000, PlayerStats.mana)

		read("§b-2,000 Mana (§6Wither Impact§b)")
		assertEquals(0, PlayerStats.mana)
	}

	@Test
	fun `a line without a segment keeps the value it last had`() {
		read("§c1,530/1,530❤     §a1,204❈ Defense     §b1,050/1,050✎ Mana")
		read("§eYou are now sneaking")

		assertEquals(1530, PlayerStats.health)
		assertEquals(1204, PlayerStats.defense)
		assertEquals(1050, PlayerStats.mana)
	}

	@Test
	fun `effective health multiplies health by the defense the sources use`() {
		read("§c1,000/1,000❤     §a250❈ Defense")

		assertEquals(3000, PlayerStats.effectiveHp)
	}

	@Test
	fun `each parse publishes one stats event`() {
		read("§c1,530/1,530❤")
		read("§c1,400/1,530❤")

		assertEquals(2, updates)
	}

	@Test
	fun `nothing is parsed outside SkyBlock`() {
		inSkyBlock = false
		read("§c1,530/1,530❤")

		assertEquals(0, PlayerStats.health)
		assertEquals(0, updates)
	}

	@Test
	fun `a hidden segment is cut out of the action bar line`() {
		PlayerStats.hide(ActionBarSegment.HEALTH, true)
		PlayerStats.hide(ActionBarSegment.DEFENSE, true)

		val event = read("§c1,530/1,530❤     §a1,204❈ Defense     §b1,050/1,050✎ Mana")

		assertEquals("§b1,050/1,050✎ Mana", event.styled)
		assertEquals(1530, PlayerStats.health)
		assertEquals(1204, PlayerStats.defense)
	}

	@Test
	fun `the action bar line is left alone when nothing is hidden`() {
		val line = "§c1,530/1,530❤     §b1,050/1,050✎ Mana"
		val event = read(line)

		assertEquals(line, event.styled)
	}

	@Test
	fun `the health the vanilla hearts show is scaled onto the SkyBlock maximum each tick`() {
		read("§c1,530/1,530❤")
		healthRatio = 0.5f
		walkSpeed = 0.4

		tick()

		assertEquals(765, PlayerStats.health)
		assertEquals(400, PlayerStats.speed)
	}

	@Test
	fun `a tick with no player leaves the parsed health alone`() {
		read("§c1,530/1,530❤")
		healthRatio = -1f

		tick()

		assertEquals(1530, PlayerStats.health)
	}

	@Test
	fun `a world change forgets every stat`() {
		read("§c1,530/1,530❤     §a1,204❈ Defense")

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))

		assertEquals(0, PlayerStats.health)
		assertEquals(0, PlayerStats.defense)
	}

	@Test
	fun `an island change forgets every stat`() {
		read("§c1,530/1,530❤     §a1,204❈ Defense")

		bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(Island.CATACOMBS, Island.HUB))

		assertEquals(0, PlayerStats.health)
		assertEquals(0, PlayerStats.defense)
	}

	private fun read(line: String): ActionBarEvent {
		val event = ActionBarEvent()
		event.text = Component.literal(line)
		bus.type<ActionBarEvent>().dispatch(event)
		return event
	}

	private fun tick() = bus.type<ClientTickEvent.End>().dispatch(ClientTickEvent.End)
}
