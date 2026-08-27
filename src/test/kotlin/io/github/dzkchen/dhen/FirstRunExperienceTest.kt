package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.IslandChangeEvent
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstRunExperienceTest {
	private val bus = EventBus()
	private val announcements = mutableListOf<Component>()
	private var persists = 0
	private val experience = FirstRunExperience({ persists++ }, announcements::add)

	@Test
	fun `a fresh install welcomes the first skyblock join exactly once`() {
		experience.install(bus, alreadyShown = false)

		change(Island.HUB, Island.NONE)
		change(Island.CATACOMBS, Island.HUB)
		change(Island.NONE, Island.CATACOMBS)
		change(Island.HUB, Island.NONE)

		assertTrue(experience.shown)
		assertEquals(1, persists)
		assertEquals(1, announcements.size)
		assertEquals(
			"Welcome to Dhen! Click /dhen to list its commands, or use /dhen addon list to browse addons.",
			announcements.single().string
		)
		val click = announcements.single().siblings.single { it.string == "/dhen" }.style.clickEvent
		assertEquals("/dhen", (click as ClickEvent.RunCommand).command)
	}

	@Test
	fun `leaving skyblock before the first join does not consume the welcome`() {
		experience.install(bus, alreadyShown = false)

		change(Island.NONE, Island.HUB)

		assertFalse(experience.shown)
		assertEquals(0, persists)
		assertTrue(announcements.isEmpty())
	}

	@Test
	fun `a persisted welcome does not register for island changes`() {
		experience.install(bus, alreadyShown = true)

		change(Island.HUB, Island.NONE)

		assertTrue(experience.active())
		assertEquals(0, persists)
		assertTrue(announcements.isEmpty())
		assertFalse(bus.type<IslandChangeEvent>().hasSubscribers)
	}

	@Test
	fun `teardown before the first join removes the registration`() {
		experience.install(bus, alreadyShown = false)

		experience.uninstall()
		change(Island.HUB, Island.NONE)

		assertFalse(experience.active())
		assertFalse(experience.shown)
		assertEquals(0, persists)
		assertTrue(announcements.isEmpty())
	}

	@Test
	fun `the welcome retires its registration before synchronous callbacks`() {
		val reentrant = FirstRunExperience(
			persist = { change(Island.HUB, Island.NONE) },
			announce = announcements::add
		)
		reentrant.install(bus, alreadyShown = false)

		change(Island.HUB, Island.NONE)

		assertTrue(reentrant.shown)
		assertEquals(1, announcements.size)
		assertFalse(bus.type<IslandChangeEvent>().hasSubscribers)
	}

	private fun change(island: Island, previous: Island) {
		bus.type<IslandChangeEvent>().dispatch(IslandChangeEvent(island, previous))
	}
}
