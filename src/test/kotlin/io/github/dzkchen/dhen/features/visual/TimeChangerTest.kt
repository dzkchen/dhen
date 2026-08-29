package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.core.Holder
import net.minecraft.core.HolderOwner
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.clock.ClockNetworkState
import net.minecraft.world.clock.WorldClock
import net.minecraft.world.clock.WorldClocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TimeChangerTest {
	@AfterEach
	fun reset() {
		for (setting in TimeChanger.settings) setting.reset()
		TimeChanger.forget()
	}

	@Test
	fun `declares one visual module offering the seven reference modes`() {
		assertEquals("Time Changer", TimeChanger.name)
		assertEquals(Category.VISUAL, TimeChanger.category)
		assertEquals(3, TimeChanger.subscriptionCount)
		assertEquals(listOf("Time"), TimeChanger.settings.map { it.name })
		assertEquals(
			listOf("Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Real Time"),
			TimeChanger.timeSetting.options
		)
		assertEquals("Day", TimeChanger.timeSetting.default)
	}

	@Test
	fun `every preset maps to its reference tick value`() {
		val expected = mapOf(
			"Day" to 1000L,
			"Noon" to 6000L,
			"Sunset" to 12000L,
			"Night" to 13000L,
			"Midnight" to 18000L,
			"Sunrise" to 23000L
		)

		for ((mode, ticks) in expected) {
			TimeChanger.time = mode
			assertEquals(ticks, TimeChanger.presetTicks(TimeChanger.timeSetting.index), mode)
		}
	}

	@Test
	fun `real time has no preset and falls through to the wall clock`() {
		TimeChanger.time = "Real Time"

		assertEquals(TimeChanger.NO_PRESET, TimeChanger.presetTicks(TimeChanger.timeSetting.index))
	}

	@Test
	fun `wall clock hours convert to the reference tick offsets`() {
		assertEquals(0L, TimeChanger.ticksAt(6, 0))
		assertEquals(6000L, TimeChanger.ticksAt(12, 0))
		assertEquals(12000L, TimeChanger.ticksAt(18, 0))
		assertEquals(17499L, TimeChanger.ticksAt(23, 30))
	}

	@Test
	fun `hours before dawn wrap into the previous evening rather than going negative`() {
		assertEquals(18000L, TimeChanger.ticksAt(0, 0))
		assertEquals(23982L, TimeChanger.ticksAt(5, 59))
		assertTrue(TimeChanger.ticksAt(0, 0) in 0L until 24000L)
		assertTrue(TimeChanger.ticksAt(5, 59) in 0L until 24000L)
	}

	@Test
	fun `the chosen mode round trips through the setting codec`() {
		TimeChanger.time = "Midnight"
		val saved = SettingCodec.writeInto(JsonObject(), TimeChanger.settings)

		TimeChanger.timeSetting.reset()
		SettingCodec.readInto(saved, TimeChanger.settings, TimeChanger.name)

		assertEquals("Midnight", TimeChanger.time)
	}

	@Test
	fun `the module registers, enables and disables without a level`() {
		val manager = ModuleManager()
		manager.register(TimeChanger)
		try {
			manager.enable(TimeChanger)

			assertTrue(TimeChanger.enabled)
			assertEquals(0, TimeChanger.errorCount)

			manager.disable(TimeChanger)

			assertFalse(TimeChanger.enabled)
			assertEquals(0, TimeChanger.errorCount)
		} finally {
			manager.unregister(TimeChanger)
		}
	}

	@Test
	fun `an applied time is handed to the clocks frozen at rate zero`() {
		val frozen = TimeChanger.frozenState(18000L)

		assertEquals(18000L, frozen.totalTicks())
		assertEquals(0.0f, frozen.partialTick())
		assertEquals(0.0f, frozen.rate())
	}

	@Test
	fun `a server clock update is recorded per clock and restored progressed`() {
		TimeChanger.anchorFrom(overworldClock(), ClockNetworkState(24000L, 0.5f, 1.0f), 900L)
		TimeChanger.anchorFrom(endClock(), ClockNetworkState(500L, 0.0f, 0.0f), 900L)

		val restored = TimeChanger.restoreStates(1000L)

		assertEquals(24100L, restored.getValue(overworldClock()).totalTicks())
		assertEquals(1.0f, restored.getValue(overworldClock()).rate())
		assertEquals(500L, restored.getValue(endClock()).totalTicks())
		assertEquals(0.0f, restored.getValue(endClock()).rate())
	}

	@Test
	fun `a later server clock update replaces the earlier anchor for that clock`() {
		TimeChanger.anchorFrom(overworldClock(), ClockNetworkState(1000L, 0.0f, 1.0f), 100L)
		TimeChanger.anchorFrom(overworldClock(), ClockNetworkState(50000L, 0.0f, 0.0f), 200L)

		val restored = TimeChanger.restoreStates(400L)

		assertEquals(1, restored.size)
		assertEquals(50000L, restored.getValue(overworldClock()).totalTicks())
		assertEquals(0.0f, restored.getValue(overworldClock()).rate())
	}

	@Test
	fun `forgetting drops every anchor so a new connection re-reads the clocks`() {
		TimeChanger.anchorFrom(overworldClock(), ClockNetworkState(1000L, 0.0f, 1.0f), 100L)

		TimeChanger.forget()

		assertTrue(TimeChanger.restoreStates(400L).isEmpty())
	}

	@Test
	fun `a running clock is restored to where the server would have carried it`() {
		val anchor = WorldClockAnchor()
		anchor.at(1000L, 0.0f, 1.0f, 100L)

		val restored = anchor.progressedTo(160L)

		assertEquals(1060L, restored.totalTicks())
		assertEquals(0.0f, restored.partialTick())
		assertEquals(1.0f, restored.rate())
	}

	@Test
	fun `a partial tick and a fractional rate carry into the restored state`() {
		val anchor = WorldClockAnchor()
		anchor.at(500L, 0.25f, 0.5f, 10L)

		val restored = anchor.progressedTo(20L)

		assertEquals(505L, restored.totalTicks())
		assertEquals(0.25f, restored.partialTick())
		assertEquals(0.5f, restored.rate())
	}

	@Test
	fun `a server-paused clock is restored paused and where it stood`() {
		val anchor = WorldClockAnchor()
		anchor.at(7777L, 0.0f, 0.0f, 40L)

		val restored = anchor.progressedTo(4000L)

		assertEquals(7777L, restored.totalTicks())
		assertEquals(0.0f, restored.rate())
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}

		fun overworldClock(): Holder<WorldClock> = clock(WorldClocks.OVERWORLD)

		fun endClock(): Holder<WorldClock> = clock(WorldClocks.THE_END)

		fun clock(key: ResourceKey<WorldClock>): Holder<WorldClock> =
			CLOCKS.getOrPut(key) { Holder.Reference.createStandAlone(OWNER, key) }

		private val OWNER = object : HolderOwner<WorldClock> {}
		private val CLOCKS = HashMap<ResourceKey<WorldClock>, Holder<WorldClock>>()
	}
}
