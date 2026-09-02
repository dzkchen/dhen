package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.HypixelModApi
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.pet.PetHooks
import io.github.dzkchen.dhen.data.quiver.QuiverHooks
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.diagnostic.WorldRenderProbe
import io.github.dzkchen.dhen.event.ContainerHooks
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Hooks
import io.github.dzkchen.dhen.event.InputHooks
import io.github.dzkchen.dhen.event.InteractionHooks
import io.github.dzkchen.dhen.event.NetworkHooks
import io.github.dzkchen.dhen.event.RenderHooks
import io.github.dzkchen.dhen.data.cookie.CookieHooks
import io.github.dzkchen.dhen.data.maxwell.MaxwellHooks
import io.github.dzkchen.dhen.event.ScreenHooks
import io.github.dzkchen.dhen.event.TickHooks
import io.github.dzkchen.dhen.event.WorldHooks
import io.github.dzkchen.dhen.event.WorldRenderHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LatchOffTest {
	@BeforeAll
	fun bootstrap() = bootstrapMinecraft()

	@Test
	fun `latching off leaves no hook with a live feed`(@TempDir dir: Path) {
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
		PetHooks.install(bus)
		QuiverHooks.install(bus)
		MaxwellHooks.install(bus)
		CookieHooks.install(bus)
		ProfileHooks.install(bus, ConfigStore(dir.resolve("profiles.json"), CoroutineScope(Dispatchers.Unconfined)))
		Dhen.firstRunExperience.install(bus, alreadyShown = true)
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
	fun `a hook that throws on uninstall does not strand the hooks after it`() {
		val torn = mutableListOf<String>()
		val throwing = object : Hooks {
			override val feed = "throwing feed"
			override fun uninstall(): Unit = throw IllegalStateException("teardown")
			override fun active() = true
		}
		val recording = object : Hooks {
			override val feed = "recording feed"
			override fun uninstall() {
				torn += feed
			}

			override fun active() = torn.isEmpty()
		}

		for (hook in listOf(throwing, recording)) Dhen.contained(hook.feed, hook::uninstall)

		assertEquals(listOf("recording feed"), torn)
		assertFalse(recording.active())
	}

	@Test
	fun `the registry names every hook the entrypoint installs`() {
		val installed = setOf(
			NetworkHooks, ScreenHooks, ContainerHooks, InputHooks, WorldHooks, RenderHooks,
			InteractionHooks, WorldRenderHooks, TickHooks, HypixelLocationHooks, ScoreboardHooks,
			TablistHooks, TabWidgetHooks, PartyHooks, PlayerStatsHooks, PetHooks, QuiverHooks, MaxwellHooks,
			CookieHooks, ProfileHooks, Dhen.firstRunExperience, HypixelModApi
		)
		assertEquals(installed, Dhen.hooks.toSet())
		assertEquals(Dhen.hooks.size, Dhen.hooks.mapTo(mutableSetOf(), Hooks::feed).size)
	}
}
