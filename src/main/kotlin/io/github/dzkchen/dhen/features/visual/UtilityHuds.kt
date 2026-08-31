package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudEditorScreen
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.ServerClock
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.util.Util
import java.time.Instant
import java.time.ZoneId

object UtilityHuds : Module(
	name = "Utility HUDs",
	category = Category.VISUAL,
	description = "Shows movable frame rate, server tick rate, ping, clicks per second, clock, " +
		"server-freeze and queue-estimate readouts."
) {
	internal val fpsSetting = BooleanSetting("FPS", true)
	internal val fpsColorSetting = ColorSetting("FPS Color", Color.rgba(230, 114, 230))
		.withDependency { fpsShown }
	internal val tpsSetting = BooleanSetting("TPS", true)
	internal val tpsColorSetting = ColorSetting("TPS Color", Color.rgba(0, 114, 255))
		.withDependency { tpsShown }
	internal val pingSetting = BooleanSetting("Ping", true)
	internal val pingColorSetting = ColorSetting("Ping Color", Color.rgba(255, 255, 255))
		.withDependency { pingShown }
	internal val cpsSetting = BooleanSetting("CPS", true)
	internal val cpsColorSetting = ColorSetting("CPS Color", Color.rgba(255, 255, 255))
		.withDependency { cpsShown }
	internal val clockSetting = BooleanSetting("Clock", true)
	internal val clockSecondsSetting = BooleanSetting("Show Seconds").withDependency { clockShown }
	internal val clockColorSetting = ColorSetting("Clock Color", Color.rgba(255, 134, 0))
		.withDependency { clockShown }
	internal val freezeSetting = BooleanSetting("Server Freeze", true)
	internal val freezeColorSetting = ColorSetting("Server Freeze Color", Color.rgba(245, 73, 39))
		.withDependency { freezeShown }
	internal val freezeThresholdSetting =
		NumberSetting("Threshold", 500.0, 100.0, 2000.0, 100.0).withDependency { freezeShown }
	internal val freezeDungeonsSetting = BooleanSetting("Only in Dungeons", true).withDependency { freezeShown }
	internal val queueSetting = BooleanSetting("Queue Estimate", true)
	internal val queueColorSetting = ColorSetting("Queue Estimate Color", Color.rgba(255, 255, 255))
		.withDependency { queueShown }

	private var fpsShown by fpsSetting
	private var fpsColor by fpsColorSetting
	private var tpsShown by tpsSetting
	private var tpsColor by tpsColorSetting
	private var pingShown by pingSetting
	private var pingColor by pingColorSetting
	private var cpsShown by cpsSetting
	private var cpsColor by cpsColorSetting
	private var clockShown by clockSetting
	private var clockSecondsShown by clockSecondsSetting
	private var clockColor by clockColorSetting
	private var freezeShown by freezeSetting
	private var freezeColor by freezeColorSetting
	private var freezeThreshold by freezeThresholdSetting
	private var freezeDungeonsOnly by freezeDungeonsSetting
	private var queueShown by queueSetting
	private var queueColor by queueColorSetting

	internal val clockSeconds: Boolean get() = clockSecondsShown
	internal val dungeonsOnly: Boolean get() = freezeDungeonsOnly
	internal val thresholdMillis: Long get() = freezeThreshold.toLong()

	internal val fpsElement = readout(Readout.FPS, fpsSetting, HudAnchor.TOP_LEFT, ROW_X, rowY(0)) { fpsColor.argb }
	internal val tpsElement = readout(Readout.TPS, tpsSetting, HudAnchor.TOP_LEFT, ROW_X, rowY(1)) { tpsColor.argb }
	internal val pingElement = readout(Readout.PING, pingSetting, HudAnchor.TOP_LEFT, ROW_X, rowY(2)) { pingColor.argb }
	internal val cpsElement = readout(Readout.CPS, cpsSetting, HudAnchor.TOP_LEFT, ROW_X, rowY(3)) { cpsColor.argb }
	internal val clockElement = readout(Readout.CLOCK, clockSetting, HudAnchor.TOP_LEFT, ROW_X, rowY(4)) { clockColor.argb }
	internal val freezeElement =
		readout(Readout.FREEZE, freezeSetting, HudAnchor.TOP_CENTER, 0, FREEZE_Y) { freezeColor.argb }
	internal val queueElement =
		readout(Readout.QUEUE, queueSetting, HudAnchor.TOP_CENTER, 0, QUEUE_Y) { queueColor.argb }

	private val readouts = arrayOf(
		fpsElement,
		tpsElement,
		pingElement,
		cpsElement,
		clockElement,
		freezeElement,
		queueElement
	)
	private val sample = UtilitySample()
	private val zone: ZoneId = ZoneId.systemDefault()
	private var zoneOffsetMillis = 0L
	private var zoneRefreshTicks = 0

	init {
		on<ClientTickEvent.End> { sampled() }
		on<PacketReceiveEvent.Post> { received(it.packet) }
		on<MouseInputEvent> { if (it.action == InputAction.PRESS) clicked(it.button) }
		on<WorldChangeEvent> { clear() }
	}

	override fun onEnabled() = clear()

	override fun onDisabled() = clear()

	override fun onReset() = clear()

	internal fun clear() {
		ClickRate.reset()
		PingProbe.reset()
		QueueEstimate.reset()
		zoneRefreshTicks = 0
		for (index in readouts.indices) readouts[index].forget()
	}

	internal fun refresh(sample: UtilitySample) {
		for (index in readouts.indices) readouts[index].update(sample)
	}

	private fun sampled() {
		val minecraft = Minecraft.getInstance()
		val now = Util.getMillis()
		sample.millis = now
		sample.epochMillis = Util.getEpochMillis()
		sample.nanos = NanoClock.SYSTEM.nanoTime()
		sample.fps = minecraft.fps
		sample.inDungeon = SkyBlockLocation.island == Island.CATACOMBS
		sample.localServer = minecraft.isLocalServer
		sample.zoneOffsetMillis = zoneOffset(sample.epochMillis)
		val connection = minecraft.connection
		if (pingShown && connection != null && !sample.localServer && PingProbe.due(now)) {
			connection.send(ServerboundPingRequestPacket(now))
		}
		QueueEstimate.tick(now)
		refresh(sample)
	}

	private fun clicked(button: Int) {
		if (!cpsShown || Minecraft.getInstance().gui.screen() != null) return
		ClickRate.pressed(button, Util.getMillis())
	}

	private fun received(packet: Packet<*>) {
		when (packet) {
			is ClientboundPongResponsePacket -> if (pingShown) PingProbe.pong(Util.getMillis(), packet.time())
			is ClientboundSetSubtitleTextPacket ->
				if (queueShown) QueueEstimate.subtitle(packet.text().string, Util.getMillis())
			is ClientboundClearTitlesPacket -> QueueEstimate.reset()
			else -> Unit
		}
	}

	private fun zoneOffset(nowMillis: Long): Long {
		if (zoneRefreshTicks-- <= 0) {
			zoneRefreshTicks = ZONE_REFRESH_TICKS
			zoneOffsetMillis = zone.rules.getOffset(Instant.ofEpochMilli(nowMillis)).totalSeconds * MILLIS_PER_SECOND
		}
		return zoneOffsetMillis
	}

	private fun rowY(row: Int): Int = ROW_Y + row * ROW_HEIGHT

	private fun readout(
		kind: Readout,
		toggle: BooleanSetting,
		anchor: HudAnchor,
		offsetX: Int,
		offsetY: Int,
		ink: () -> Int
	): UtilityReadout = hud(UtilityReadout(kind, toggle, anchor, offsetX, offsetY, ink))

	private const val ROW_X = 2
	private const val ROW_Y = 2
	private const val ROW_HEIGHT = 10
	private const val FREEZE_Y = 40
	private const val QUEUE_Y = 56
	private const val ZONE_REFRESH_TICKS = 1200
	private const val MILLIS_PER_SECOND = 1000L
}

internal class UtilitySample {
	var millis = 0L
	var epochMillis = 0L
	var nanos = 0L
	var fps = 0
	var inDungeon = false
	var localServer = false
	var zoneOffsetMillis = 0L
}

internal object ClickRate {
	const val LEFT_BUTTON = 0
	const val RIGHT_BUTTON = 1

	private const val CAPACITY = 40
	private const val WINDOW_MILLIS = 1000L

	private val left = LongArray(CAPACITY)
	private val right = LongArray(CAPACITY)
	private var leftHead = 0
	private var rightHead = 0

	fun pressed(button: Int, nowMillis: Long) {
		when (button) {
			LEFT_BUTTON -> {
				left[leftHead] = nowMillis
				leftHead = (leftHead + 1) % CAPACITY
			}
			RIGHT_BUTTON -> {
				right[rightHead] = nowMillis
				rightHead = (rightHead + 1) % CAPACITY
			}
		}
	}

	fun count(button: Int, nowMillis: Long): Int {
		val ring = if (button == LEFT_BUTTON) left else right
		var total = 0
		for (index in ring.indices) {
			val at = ring[index]
			if (at != 0L && nowMillis - at in 0..WINDOW_MILLIS) total++
		}
		return total
	}

	fun reset() {
		left.fill(0L)
		right.fill(0L)
		leftHead = 0
		rightHead = 0
	}
}

internal object PingProbe {
	private const val SAMPLES = 20
	private const val REQUEST_INTERVAL_TICKS = 80
	private const val AWAIT_TIMEOUT_MILLIS = 10_000L

	private val samples = LongArray(SAMPLES)
	private var head = 0
	private var filled = 0
	private var awaiting = false
	private var awaitingSince = 0L
	private var countdown = 0

	var average: Int = 0
		private set

	val answered: Boolean get() = filled > 0

	fun due(nowMillis: Long): Boolean {
		if (awaiting && nowMillis - awaitingSince > AWAIT_TIMEOUT_MILLIS) awaiting = false
		if (countdown-- > 0) return false
		countdown = REQUEST_INTERVAL_TICKS
		if (awaiting) return false
		awaiting = true
		awaitingSince = nowMillis
		return true
	}

	fun pong(nowMillis: Long, sentMillis: Long) {
		awaiting = false
		samples[head] = (nowMillis - sentMillis).coerceAtLeast(0L)
		head = (head + 1) % SAMPLES
		if (filled < SAMPLES) filled++
		var total = 0L
		for (index in 0 until filled) total += samples[index]
		average = (total / filled).toInt()
	}

	fun reset() {
		samples.fill(0L)
		head = 0
		filled = 0
		awaiting = false
		awaitingSince = 0L
		countdown = 0
		average = 0
	}
}

internal object QueueEstimate {
	const val COLLECTING = -1L

	private val queueLine = Regex("You are #(\\d+) in the queue!")
	private const val CAPACITY = 32
	private const val STALE_MILLIS = 30_000L
	private const val MILLIS_PER_SECOND = 1000.0

	private val times = LongArray(CAPACITY)
	private val positions = IntArray(CAPACITY)
	private var count = 0
	private var lastPosition = -1
	private var lastSeenMillis = 0L
	private var finishMillis = 0L

	val collecting: Boolean get() = count > 0

	fun secondsRemaining(nowMillis: Long): Long =
		if (finishMillis == 0L) COLLECTING else ((finishMillis - nowMillis) / 1000L).coerceAtLeast(0L)

	fun subtitle(text: String, nowMillis: Long) {
		val position = queueLine.find(text)?.groupValues?.get(1)?.toIntOrNull()
		if (position == null) {
			reset()
			return
		}
		lastSeenMillis = nowMillis
		if (position == lastPosition) return
		lastPosition = position
		if (count == CAPACITY) {
			System.arraycopy(times, 1, times, 0, CAPACITY - 1)
			System.arraycopy(positions, 1, positions, 0, CAPACITY - 1)
			count--
		}
		times[count] = nowMillis
		positions[count] = position
		count++
		fit()
	}

	fun tick(nowMillis: Long) {
		if (count > 0 && nowMillis - lastSeenMillis > STALE_MILLIS) reset()
	}

	fun reset() {
		count = 0
		lastPosition = -1
		lastSeenMillis = 0L
		finishMillis = 0L
	}

	private fun fit() {
		finishMillis = 0L
		if (count < 2) return
		val start = times[0]
		var sumX = 0.0
		var sumY = 0.0
		for (index in 0 until count) {
			sumX += (times[index] - start) / MILLIS_PER_SECOND
			sumY += positions[index].toDouble()
		}
		val averageX = sumX / count
		val averageY = sumY / count
		var above = 0.0
		var below = 0.0
		for (index in 0 until count) {
			val x = (times[index] - start) / MILLIS_PER_SECOND
			above += (x - averageX) * (positions[index] - averageY)
			below += (x - averageX) * (x - averageX)
		}
		if (below == 0.0) return
		val slope = above / below
		if (slope >= 0.0) return
		val secondsToZero = -(averageY - slope * averageX) / slope
		finishMillis = start + (secondsToZero * MILLIS_PER_SECOND).toLong()
	}
}

internal enum class Readout(val label: String) {
	FPS("FPS"),
	TPS("TPS"),
	PING("Ping"),
	CPS("CPS"),
	CLOCK("Clock"),
	FREEZE("Server Freeze"),
	QUEUE("Queue Estimate")
}

internal class UtilityReadout(
	private val kind: Readout,
	private val toggle: BooleanSetting,
	anchor: HudAnchor,
	offsetX: Int,
	offsetY: Int,
	private val ink: () -> Int
) : HudElement(kind.label, anchor, offsetX, offsetY) {
	private val memo = DhenType.memo()
	private val builder = StringBuilder(TEXT_CAPACITY)
	private var shown = false
	private var valueA = Long.MIN_VALUE
	private var valueB = Long.MIN_VALUE
	internal var liveText: String = ""
		private set

	override val hasContent: Boolean
		get() = contentAvailable(editing())

	override fun width(font: Font): Int = memo.width(font, shownText(editing()))

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, shownText(editing()), 0, 0, ink(), scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	internal fun contentAvailable(editing: Boolean): Boolean =
		toggle.on && (editing || liveText.isNotEmpty())

	internal fun shownText(editing: Boolean): String = if (editing) exampleText() else liveText

	internal fun forget() {
		shown = false
		valueA = Long.MIN_VALUE
		valueB = Long.MIN_VALUE
		liveText = ""
	}

	internal fun update(sample: UtilitySample) {
		var visible = toggle.on
		var a = 0L
		var b = 0L
		if (visible) when (kind) {
			Readout.FPS -> a = sample.fps.toLong()
			Readout.TPS -> a = (ServerClock.tps * TENTHS + HALF).toLong().coerceAtLeast(0L)
			Readout.PING -> {
				visible = PingProbe.answered
				a = PingProbe.average.toLong()
			}
			Readout.CPS -> {
				a = ClickRate.count(ClickRate.LEFT_BUTTON, sample.millis).toLong()
				b = ClickRate.count(ClickRate.RIGHT_BUTTON, sample.millis).toLong()
			}
			Readout.CLOCK -> {
				b = if (UtilityHuds.clockSeconds) 1L else 0L
				val secondOfDay = localSecondOfDay(sample)
				a = if (b == 1L) secondOfDay else secondOfDay / SECONDS_PER_MINUTE
			}
			Readout.FREEZE -> {
				val elapsed = freezeMillis(sample)
				visible = elapsed >= 0L
				a = elapsed / FREEZE_BUCKET_MILLIS
			}
			Readout.QUEUE -> {
				visible = QueueEstimate.collecting
				a = QueueEstimate.secondsRemaining(sample.millis)
			}
		}
		if (visible == shown && a == valueA && b == valueB) return
		shown = visible
		valueA = a
		valueB = b
		builder.setLength(0)
		if (visible) compose()
		liveText = if (builder.isEmpty()) "" else builder.toString()
	}

	private fun compose() {
		when (kind) {
			Readout.FPS -> builder.append(valueA).append(" fps")
			Readout.TPS -> builder.append("TPS: ").append(valueA / TENTHS_STEP).append('.').append(valueA % TENTHS_STEP)
			Readout.PING -> builder.append("Ping: ").append(valueA).append("ms")
			Readout.CPS -> builder.append(valueA).append(" | ").append(valueB).append(" CPS")
			Readout.CLOCK -> appendClock()
			Readout.FREEZE -> builder.append(valueA * FREEZE_BUCKET_MILLIS).append("ms")
			Readout.QUEUE -> {
				builder.append("Queue: ")
				if (valueA == QueueEstimate.COLLECTING) builder.append("collecting") else appendDuration(valueA)
			}
		}
	}

	private fun appendClock() {
		val withSeconds = valueB == 1L
		val hours = if (withSeconds) valueA / SECONDS_PER_HOUR else valueA / MINUTES_PER_HOUR
		val minutes = if (withSeconds) valueA / SECONDS_PER_MINUTE % MINUTES_PER_HOUR else valueA % MINUTES_PER_HOUR
		padded(hours)
		builder.append(':')
		padded(minutes)
		if (withSeconds) {
			builder.append(':')
			padded(valueA % SECONDS_PER_MINUTE)
		}
	}

	private fun appendDuration(seconds: Long) {
		val hours = seconds / SECONDS_PER_HOUR
		val minutes = seconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
		if (hours > 0L) builder.append(hours).append("h ")
		if (hours > 0L || minutes > 0L) builder.append(minutes).append("m ")
		builder.append(seconds % SECONDS_PER_MINUTE).append('s')
	}

	private fun padded(value: Long) {
		if (value < 10L) builder.append('0')
		builder.append(value)
	}

	private fun localSecondOfDay(sample: UtilitySample): Long =
		Math.floorMod((sample.epochMillis + sample.zoneOffsetMillis) / MILLIS_PER_SECOND, SECONDS_PER_DAY)

	private fun freezeMillis(sample: UtilitySample): Long {
		if (sample.localServer) return HIDDEN
		if (UtilityHuds.dungeonsOnly && !sample.inDungeon) return HIDDEN
		val lastTick = ServerClock.lastTickNanos
		if (lastTick == 0L) return HIDDEN
		val elapsed = (sample.nanos - lastTick) / NANOS_PER_MILLI
		return if (elapsed > UtilityHuds.thresholdMillis) elapsed else HIDDEN
	}

	private fun exampleText(): String = when (kind) {
		Readout.FPS -> "120 fps"
		Readout.TPS -> "TPS: 20.0"
		Readout.PING -> "Ping: 45ms"
		Readout.CPS -> "12 | 3 CPS"
		Readout.CLOCK -> if (UtilityHuds.clockSeconds) "14:32:07" else "14:32"
		Readout.FREEZE -> "567ms"
		Readout.QUEUE -> "Queue: 3m 20s"
	}

	private fun editing(): Boolean = Minecraft.getInstance().gui.screen() is HudEditorScreen

	private companion object {
		const val TEXT_CAPACITY = 24
		const val TENTHS = 10f
		const val HALF = 0.5f
		const val MILLIS_PER_SECOND = 1000L
		const val TENTHS_STEP = 10L
		const val FREEZE_BUCKET_MILLIS = 10L
		const val NANOS_PER_MILLI = 1_000_000L
		const val SECONDS_PER_MINUTE = 60L
		const val MINUTES_PER_HOUR = 60L
		const val SECONDS_PER_HOUR = 3600L
		const val SECONDS_PER_DAY = 86_400L
		const val HIDDEN = -1L
	}
}
