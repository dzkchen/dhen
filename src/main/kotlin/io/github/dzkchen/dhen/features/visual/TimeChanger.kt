package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.mixin.ClientClockManagerAccessor
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.world.clock.ClockNetworkState
import net.minecraft.world.clock.WorldClock
import java.time.LocalTime
import kotlin.math.floor

object TimeChanger : Module(
	name = "Time Changer",
	category = Category.VISUAL,
	description = "Holds the sky at a chosen time of day for you only."
) {
	internal val timeSetting = SelectorSetting(
		"Time",
		DAY,
		listOf(DAY, NOON, SUNSET, NIGHT, MIDNIGHT, SUNRISE, REAL_TIME),
		listed = true,
		description = "Time of day your client renders."
	)
	internal var time by timeSetting

	private val anchors = HashMap<Holder<WorldClock>, WorldClockAnchor>()
	private var appliedTicks = UNAPPLIED
	private var realTimeMinute = Long.MIN_VALUE
	private var realTimeTicks = 0L

	init {
		on<ClientTickEvent.Start> { applyPending() }
		on<PacketReceiveEvent.Post> { event ->
			val packet = event.packet as? ClientboundSetTimePacket ?: return@on
			if (packet.clockUpdates().isEmpty()) return@on
			anchorTo(packet)
			appliedTicks = UNAPPLIED
			applyPending()
		}
		on<WorldChangeEvent> { forget() }
	}

	override fun onEnabled() {
		appliedTicks = UNAPPLIED
		clientLevel()?.let(::anchorTo)
		applyPending()
	}

	override fun onDisabled() {
		val level = clientLevel() ?: return
		if (anchors.isEmpty()) return
		val gameTime = level.gameTime
		level.clockManager().handleUpdates(gameTime, restoreStates(gameTime))
	}

	internal fun ticksAt(hour: Int, minute: Int): Long {
		val ticks = hour * HOUR_TICKS + (minute * MINUTE_TICKS).toLong() - DAWN_OFFSET
		return if (ticks < 0) ticks + DAY_TICKS else ticks
	}

	internal fun presetTicks(index: Int): Long = if (index < PRESETS.size) PRESETS[index] else NO_PRESET

	internal fun frozenState(ticks: Long): ClockNetworkState = ClockNetworkState(ticks, NO_PARTIAL, FROZEN_RATE)

	internal fun anchorFrom(clock: Holder<WorldClock>, state: ClockNetworkState, gameTime: Long) {
		anchors.getOrPut(clock) { WorldClockAnchor() }.at(state.totalTicks(), state.partialTick(), state.rate(), gameTime)
	}

	internal fun restoreStates(gameTime: Long): Map<Holder<WorldClock>, ClockNetworkState> =
		anchors.mapValues { (_, anchor) -> anchor.progressedTo(gameTime) }

	internal fun forget() {
		anchors.clear()
		appliedTicks = UNAPPLIED
	}

	private fun clientLevel(): ClientLevel? {
		val client: Minecraft? = Minecraft.getInstance()
		return client?.level
	}

	private fun applyPending() {
		val target = targetTicks()
		if (target == appliedTicks) return
		val level = clientLevel() ?: return
		if (anchors.isEmpty()) anchorTo(level)
		if (anchors.isEmpty()) return
		val frozen = frozenState(target)
		level.clockManager().handleUpdates(level.gameTime, anchors.mapValues { frozen })
		appliedTicks = target
	}

	private fun targetTicks(): Long {
		val preset = presetTicks(timeSetting.index)
		if (preset != NO_PRESET) return preset
		val minute = System.currentTimeMillis() / MINUTE_MILLIS
		if (minute != realTimeMinute) {
			realTimeMinute = minute
			realTimeTicks = LocalTime.now().let { ticksAt(it.hour, it.minute) }
		}
		return realTimeTicks
	}

	private fun anchorTo(level: ClientLevel) {
		val clocks = level.clockManager()
		val live = (clocks as ClientClockManagerAccessor).liveClocks()
		val gameTime = level.gameTime
		level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).listElements().forEach { clock ->
			clocks.getTotalTicks(clock)
			val instance = live[clock] as WorldClockAccess
			anchors.getOrPut(clock) { WorldClockAnchor() }
				.at(instance.dhenTotalTicks(), instance.dhenPartialTick(), instance.dhenRate(), gameTime)
		}
	}

	private fun anchorTo(packet: ClientboundSetTimePacket) {
		for ((clock, state) in packet.clockUpdates()) anchorFrom(clock, state, packet.gameTime())
	}

	internal const val DAY = "Day"
	internal const val NOON = "Noon"
	internal const val SUNSET = "Sunset"
	internal const val NIGHT = "Night"
	internal const val MIDNIGHT = "Midnight"
	internal const val SUNRISE = "Sunrise"
	internal const val REAL_TIME = "Real Time"

	internal const val NO_PRESET = -1L

	private val PRESETS = longArrayOf(1000L, 6000L, 12000L, 13000L, 18000L, 23000L)
	private const val UNAPPLIED = Long.MIN_VALUE
	private const val HOUR_TICKS = 1000L
	private const val MINUTE_TICKS = 16.66
	private const val DAWN_OFFSET = 6000L
	private const val DAY_TICKS = 24000L
	private const val MINUTE_MILLIS = 60_000L
	private const val NO_PARTIAL = 0.0f
	private const val FROZEN_RATE = 0.0f
}

internal class WorldClockAnchor {
	private var totalTicks = 0L
	private var partialTick = 0.0f
	private var rate = 1.0f
	private var gameTime = 0L

	fun at(totalTicks: Long, partialTick: Float, rate: Float, gameTime: Long) {
		this.totalTicks = totalTicks
		this.partialTick = partialTick
		this.rate = rate
		this.gameTime = gameTime
	}

	fun progressedTo(now: Long): ClockNetworkState {
		val progressed = partialTick + (now - gameTime).toDouble() * rate
		val whole = floor(progressed).toLong()
		return ClockNetworkState(totalTicks + whole, (progressed - whole).toFloat(), rate)
	}
}
