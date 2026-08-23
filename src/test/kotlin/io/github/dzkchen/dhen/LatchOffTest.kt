package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.HypixelModApi
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.diagnostic.WorldRenderProbe
import io.github.dzkchen.dhen.event.ContainerHooks
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Hooks
import io.github.dzkchen.dhen.event.InputHooks
import io.github.dzkchen.dhen.event.InteractionHooks
import io.github.dzkchen.dhen.event.NetworkHooks
import io.github.dzkchen.dhen.event.RenderHooks
import io.github.dzkchen.dhen.event.ScreenHooks
import io.github.dzkchen.dhen.event.TickHooks
import io.github.dzkchen.dhen.event.WorldHooks
import io.github.dzkchen.dhen.event.WorldRenderHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LatchOffTest {
	@BeforeAll
	fun bootstrap() = bootstrapMinecraft()

	@Test
	fun `latching off leaves no hook with a live feed`() {
		val bus = EventBus()
		NetworkHooks.install(bus)
		ScreenHooks.install(bus)
		ContainerHooks.install(bus)
		InputHooks.install(bus)
		WorldHooks.install(bus)
		RenderHooks.install(bus)
		InteractionHooks.install(bus)
		WorldRenderHooks.install(bus)
		TickHooks.install(bus)
		HypixelLocationHooks.install(bus)
		ScoreboardHooks.install(bus)
		TablistHooks.install(bus)
		TabWidgetHooks.install(bus)
		PartyHooks.install(bus)
		PlayerStatsHooks.install(bus)
		HypixelModApi.install()
		WorldRenderProbe.install(bus)
		WorldRenderProbe.toggle()

		for (hook in Dhen.hooks) assertTrue(hook.active(), hook.feed)
		assertTrue(TickHooks.serverFeedActive())
		assertTrue(WorldRenderProbe.active())

		Dhen.latchOff()

		for (hook in Dhen.hooks) assertFalse(hook.active(), hook.feed)
		assertFalse(TickHooks.serverFeedActive())
		assertFalse(WorldRenderProbe.active())
	}

	@Test
	fun `the registry names every hook the entrypoint installs`() {
		val installed = setOf(
			NetworkHooks, ScreenHooks, ContainerHooks, InputHooks, WorldHooks, RenderHooks,
			InteractionHooks, WorldRenderHooks, TickHooks, HypixelLocationHooks, ScoreboardHooks,
			TablistHooks, TabWidgetHooks, PartyHooks, PlayerStatsHooks, HypixelModApi
		)
		assertEquals(installed, Dhen.hooks.toSet())
		assertEquals(Dhen.hooks.size, Dhen.hooks.mapTo(mutableSetOf(), Hooks::feed).size)
	}
}
