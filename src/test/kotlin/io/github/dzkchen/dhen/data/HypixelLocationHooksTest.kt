package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.AreaChangeEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldHooks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HypixelLocationHooksTest {
	private val bus = EventBus()
	private val islands = mutableListOf<IslandChangeEvent>()
	private val areas = mutableListOf<AreaChangeEvent>()

	@BeforeEach
	fun install() {
		SkyBlockLocation.reset()
		HypixelLocationHooks.install(bus)
		bus.subscribe<IslandChangeEvent> { islands += it }
		bus.subscribe<AreaChangeEvent> { areas += it }
	}

	@AfterEach
	fun uninstall() {
		HypixelLocationHooks.uninstall()
		SkyBlockLocation.reset()
	}

	@Test
	fun `joining skyblock publishes the island and the area it maps to`() {
		hub()

		assertEquals(Island.HUB, SkyBlockLocation.island)
		assertEquals("Hub", SkyBlockLocation.area)
		assertTrue(SkyBlockLocation.inSkyBlock)
		assertTrue(SkyBlockLocation.onHypixel)
		assertEquals(listOf(Island.NONE to Island.HUB), islands.map { it.previous to it.island })
		assertEquals(listOf(null to "Hub"), areas.map { it.previous to it.area })
	}

	@Test
	fun `warping carries both the island left and the island joined`() {
		hub()
		HypixelLocationHooks.located("mini5B", skyBlock = true, mode = "dungeon", map = "Dungeon")

		assertEquals(Island.CATACOMBS, SkyBlockLocation.island)
		assertEquals(Island.HUB to Island.CATACOMBS, islands.last().let { it.previous to it.island })
		assertEquals("Hub" to "Dungeon", areas.last().let { it.previous to it.area })
	}

	@Test
	fun `a resent location packet publishes nothing`() {
		hub()
		hub()

		assertEquals(1, islands.size)
		assertEquals(1, areas.size)
		assertEquals(1, HypixelLocationHooks.islandChanges)
		assertEquals(1, HypixelLocationHooks.areaChanges)
	}

	@Test
	fun `an area change on the same island leaves the island alone`() {
		hub()
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "hub", map = "Mega Hub")

		assertEquals(1, islands.size)
		assertEquals(listOf("Hub", "Mega Hub"), areas.map { it.area })
	}

	@Test
	fun `leaving skyblock for a lobby clears the island and the area`() {
		hub()
		HypixelLocationHooks.located("lobby3", skyBlock = false, mode = null, map = null)

		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertNull(SkyBlockLocation.area)
		assertFalse(SkyBlockLocation.inSkyBlock)
		assertTrue(SkyBlockLocation.onHypixel)
		assertEquals(Island.HUB to Island.NONE, islands.last().let { it.previous to it.island })
	}

	@Test
	fun `a mode Dhen does not know still reports an island`() {
		HypixelLocationHooks.located("mini9C", skyBlock = true, mode = "not_yet_shipped", map = "Somewhere")

		assertEquals(Island.UNKNOWN, SkyBlockLocation.island)
		assertEquals("Somewhere", SkyBlockLocation.area)
	}

	@Test
	fun `a skyblock packet with no island named leaves the island alone`() {
		hub()
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = null, map = null)

		assertEquals(Island.HUB, SkyBlockLocation.island)
		assertEquals("Hub", SkyBlockLocation.area)
		assertEquals(1, islands.size)
		assertEquals(1, areas.size)
	}

	@Test
	fun `an uninstalled feed forgets where it was`() {
		hub()
		HypixelLocationHooks.uninstall()

		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertFalse(SkyBlockLocation.onHypixel)
	}

	@Test
	fun `disconnecting forgets the whole location`() {
		WorldHooks.install(bus)
		try {
			hub()
			WorldHooks.worldChanged(WorldChange.DISCONNECT)
		} finally {
			WorldHooks.uninstall()
		}

		assertFalse(SkyBlockLocation.onHypixel)
		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertNull(SkyBlockLocation.serverName)
		assertEquals(Island.HUB to Island.NONE, islands.last().let { it.previous to it.island })
	}

	@Test
	fun `joining a world is not a disconnect`() {
		WorldHooks.install(bus)
		try {
			hub()
			WorldHooks.worldChanged(WorldChange.JOIN)
		} finally {
			WorldHooks.uninstall()
		}

		assertEquals(Island.HUB, SkyBlockLocation.island)
		assertEquals(1, islands.size)
	}

	@Test
	fun `an uninstalled feed publishes nothing`() {
		HypixelLocationHooks.uninstall()

		hub()

		assertFalse(HypixelLocationHooks.active())
		assertEquals(Island.NONE, SkyBlockLocation.island)
		assertTrue(islands.isEmpty())
	}

	@Test
	fun `no two islands claim the same mode id`() {
		val mapped = Island.entries.filter { it.modeId != null }

		assertEquals(mapped.size, mapped.mapTo(mutableSetOf()) { it.modeId }.size)
		assertEquals(Island.CATACOMBS, Island.ofMode("dungeon"))
		assertEquals(Island.KUUDRA, Island.ofMode("kuudra"))
		assertEquals(Island.UNKNOWN, Island.ofMode("nope"))
	}

	private fun hub() =
		HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "hub", map = "Hub")
}
