package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.WaypointCommands
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.WorldDepth
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.roundToInt

object Waypoints : Module(
	name = "Waypoints",
	category = Category.VISUAL,
	description = "Marks coordinates shared in chat, and the block you are looking at, with a box and a beam."
), WaypointCommands {
	private var fromParty by BooleanSetting(
		"From Party Chat",
		default = true,
		description = "Adds a waypoint for coordinates a party member sends."
	)

	private var fromAll by BooleanSetting(
		"From All Chat",
		description = "Adds a waypoint for coordinates anyone sends in public chat."
	)

	private var personalWaypoint by BooleanSetting(
		"Personal Waypoint",
		description = "Also adds a waypoint for the coordinates you send yourself."
	)

	private var waypointSeconds by NumberSetting(
		"Waypoint Time",
		default = 60.0,
		min = 1.0,
		max = 120.0,
		description = "Seconds a chat waypoint stays on screen."
	)

	internal val waypointColorSetting = ColorSetting(
		"Waypoint Color",
		Color.rgba(0, 51, 255),
		description = "The color of the box, the beam and the label."
	)
	private var waypointColor by waypointColorSetting

	internal val fadeWithDistanceSetting = BooleanSetting(
		"Dynamic Opacity",
		default = true,
		description = "Fades a waypoint out as you get closer to it, instead of using a fixed opacity."
	)
	private var fadeWithDistance by fadeWithDistanceSetting

	private var waypointOpacity by NumberSetting("Waypoint Opacity", 50.0, 0.0, 100.0)
		.withDependency { !fadeWithDistanceSetting.on }

	private var textOpacity by NumberSetting("Text Opacity", 100.0, 0.0, 100.0)

	private var textScale by NumberSetting(
		"Text Scale",
		default = 0.7,
		min = 0.3,
		max = 2.0,
		step = 0.1,
		description = "Size of the label above a waypoint."
	)

	private var distanceCutoff by NumberSetting(
		"Distance Cutoff",
		default = 50.0,
		min = 0.0,
		max = 150.0,
		description = "Hides the distance on the label once you are this close. 0 always shows it."
	)

	private var hideWhenClose by BooleanSetting(
		"Hide When Close",
		default = true,
		description = "Hides a waypoint once you are within five blocks of it."
	)

	private var chatButtons by BooleanSetting(
		"Chat Buttons",
		default = true,
		description = "Puts clickable Hide and Redraw buttons on coordinate messages."
	)

	internal val pingWaypointSetting = BooleanSetting(
		"Ping Waypoint",
		description = "Adds a waypoint at the block you are looking at."
	)
	private var pingWaypoint by pingWaypointSetting

	internal val pingKeySetting = KeybindSetting("Ping Keybind", description = "Drops a waypoint where you are looking.")
		.onPress(::pinged)
		.withDependency { pingWaypointSetting.on }
	private var pingKey by pingKeySetting

	private var sendPingedLocation by BooleanSetting(
		"Send Pinged Location",
		description = "Also sends the pinged coordinates to chat."
	).withDependency { pingWaypointSetting.on }

	private var pingSeconds by NumberSetting("Ping Waypoint Time", 15.0, 1.0, 128.0)
		.withDependency { pingWaypointSetting.on }

	private var pingDistance by NumberSetting("Ping Distance", 64.0, 1.0, 128.0)
		.withDependency { pingWaypointSetting.on }

	private var storedWaypoints by StringSetting("Saved Waypoints").hide()

	private val book = savedBook { storedWaypoints }
	private val live = ArrayList<LiveWaypoint>()

	init {
		on<ChatReceiveEvent> { received(it) }
		on<ChatReceiveEvent>(AFTER_PRODUCERS) { buttoned(it) }
		on<ClientTickEvent.End> { swept() }
		on<WorldRenderEvent> { drew(it) }
		on<WorldChangeEvent> { expire() }
		on<IslandChangeEvent> { reseed() }
	}

	override fun onEnabled() = reseed()

	override fun onDisabled() = live.clear()

	override fun onReset() = reseed()

	fun closestWarp(x: Double, y: Double, z: Double): String = nearestWarp(x, y, z)

	fun add(group: String, title: String, x: Int, y: Int, z: Int, seconds: Double): Boolean {
		if (!enabled || outOfBounds(x, y, z) || holds(x, y, z)) return false
		live += LiveWaypoint(group, title, BlockPos(x, y, z), expiryOf(seconds))
		return true
	}

	fun removeGroup(group: String) {
		live.removeAll { it.group == group }
	}

	override fun sendPing(note: String): String {
		val player = Minecraft.getInstance().player ?: return NOT_IN_WORLD
		val line = coordinateLine(player.blockX, player.blockY - 1, player.blockZ, note)
		Minecraft.getInstance().connection?.sendCommand("$PARTY_COMMAND $line")
		return "Sent your coordinates to party chat."
	}

	override fun save(name: String): String {
		val fault = savedNameFault(name)
		if (fault != null) return fault
		val player = Minecraft.getInstance().player ?: return NOT_IN_WORLD
		val point = SavedPoint(name, SkyBlockLocation.island.name, player.blockX, player.blockY, player.blockZ)
		storedWaypoints = formatSaved(book.all().values.filter { it.name != name } + point)
		reseed()
		return "Saved $name at ${point.x}, ${point.y}, ${point.z}."
	}

	override fun forget(name: String): String {
		val current = book.all()
		if (name !in current) return "No waypoint named '$name'."
		storedWaypoints = formatSaved(current.values.filter { it.name != name })
		reseed()
		return "Removed the $name waypoint."
	}

	override fun list(): List<String> {
		val current = book.all().values
		if (current.isEmpty()) return listOf("No saved waypoints. Use /dhen waypoint add <name>.")
		return current.map { "${it.name} — ${it.x}, ${it.y}, ${it.z} on ${islandName(it.island)}" }
	}

	override fun names(): List<String> = book.all().keys.toList()

	override fun hide(x: Int, y: Int, z: Int): String {
		if (!enabled) return SWITCHED_OFF
		val removed = live.removeAll { it.group != SAVED_GROUP && it.pos.x == x && it.pos.y == y && it.pos.z == z }
		return if (removed) "Hid the waypoint at $x, $y, $z." else "No waypoint at $x, $y, $z."
	}

	override fun redraw(x: Int, y: Int, z: Int, label: String): String {
		if (!enabled) return SWITCHED_OFF
		if (outOfBounds(x, y, z)) return OUT_OF_BOUNDS
		hide(x, y, z)
		return if (add(CHAT_GROUP, label.ifBlank { DEFAULT_TITLE }, x, y, z, waypointSeconds)) {
			"Redrew the waypoint at $x, $y, $z."
		} else {
			"A waypoint is already there."
		}
	}

	private fun received(event: ChatReceiveEvent) {
		val shared = wanted(event) ?: return
		announced(CHAT_GROUP, titleOf(shared), shared.x, shared.y, shared.z, waypointSeconds)
	}

	private fun titleOf(shared: ChatCoordinates): String =
		if (shared.note.isEmpty()) shared.sender else "${shared.sender}: ${shared.note}"

	private fun buttoned(event: ChatReceiveEvent) {
		if (!chatButtons) return
		val shared = wanted(event) ?: return
		event.text = event.text.copy().append(hideButton(shared)).append(redrawButton(shared))
	}

	private fun wanted(event: ChatReceiveEvent): ChatCoordinates? {
		val shared = chatCoordinates(event.stripped) ?: return null
		if (shared.channel == GUILD_CHANNEL) return null
		if (if (shared.channel == PARTY_CHANNEL) !fromParty else !fromAll) return null
		if (shared.sender == Minecraft.getInstance().player?.name?.string && !personalWaypoint) return null
		return shared
	}

	private fun announced(group: String, title: String, x: Int, y: Int, z: Int, seconds: Double) {
		if (outOfBounds(x, y, z)) Dhen.announce(OUT_OF_BOUNDS)
		else if (!add(group, title, x, y, z, seconds)) Dhen.announce("Waypoint already exists at $x, $y, $z.")
	}

	private fun hideButton(shared: ChatCoordinates): Component =
		DhenType.buttonOverWorld(HIDE_LABEL, "$HIDE_COMMAND ${shared.x} ${shared.y} ${shared.z}")

	private fun redrawButton(shared: ChatCoordinates): Component =
		DhenType.buttonOverWorld(
			REDRAW_LABEL,
			"$REDRAW_COMMAND ${shared.x} ${shared.y} ${shared.z} ${titleOf(shared)}"
		)

	private fun pinged() {
		if (!pingWaypoint) return
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val hit = player.pick(pingDistance, client.deltaTracker.getGameTimeDeltaPartialTick(true), false)
		if (hit.type != HitResult.Type.BLOCK) return
		val pos = (hit as BlockHitResult).blockPos
		announced(PING_GROUP, DEFAULT_TITLE, pos.x, pos.y, pos.z, pingSeconds)
		if (sendPingedLocation) client.connection?.sendChat(coordinateLine(pos.x, pos.y, pos.z, ""))
	}

	private fun swept() {
		if (live.isEmpty()) return
		val player = Minecraft.getInstance().player
		if (player == null) {
			expire()
			return
		}
		val now = System.currentTimeMillis()
		val cutoff = distanceCutoff.toInt()
		val fixedAlpha = (waypointOpacity / PERCENT).toFloat().coerceIn(MIN_FIXED_ALPHA, 1f)
		val labelColor = ARGB.color((textOpacity / PERCENT).toFloat(), waypointColor.rgb)
		var index = 0
		while (index < live.size) {
			val waypoint = live[index]
			if (waypoint.expiry != FOREVER && waypoint.expiry < now) {
				live.removeAt(index)
				continue
			}
			val distance = span(
				waypoint.pos.x + HALF_BLOCK - player.x,
				waypoint.pos.y + HALF_BLOCK - player.y,
				waypoint.pos.z + HALF_BLOCK - player.z
			)
			val metres = distance.roundToInt()
			if (metres != waypoint.metres || cutoff != waypoint.cutoff) {
				waypoint.metres = metres
				waypoint.cutoff = cutoff
				waypoint.label = waypointLabel(waypoint.title, metres, cutoff)
			}
			waypoint.visible = !hideWhenClose || distance > HIDE_DISTANCE
			waypoint.scale = labelScale(distance, textScale)
			waypoint.color = ARGB.color(if (fadeWithDistance) dynamicOpacity(distance) else fixedAlpha, waypointColor.rgb)
			waypoint.labelColor = labelColor
			index++
		}
	}

	private fun drew(event: WorldRenderEvent) {
		for (index in 0 until live.size) {
			val waypoint = live[index]
			if (!waypoint.visible) continue
			WorldDraw.drawWireBox(event, waypoint.pos, waypoint.color, depth = WorldDepth.THROUGH_WALLS)
			WorldDraw.drawBeaconBeam(event, waypoint.pos, waypoint.color)
			WorldDraw.drawText(
				event,
				waypoint.memo,
				waypoint.label,
				waypoint.pos.x + HALF_BLOCK,
				waypoint.pos.y + LABEL_LIFT,
				waypoint.pos.z + HALF_BLOCK,
				waypoint.labelColor,
				waypoint.scale,
				WorldDepth.THROUGH_WALLS
			)
		}
	}

	private fun expire() {
		live.removeAll { it.expiry != FOREVER }
	}

	private fun reseed() {
		live.removeAll { it.group == SAVED_GROUP }
		if (!enabled) return
		val here = SkyBlockLocation.island.name
		for (point in book.all().values) {
			if (point.island != here) continue
			live += LiveWaypoint(SAVED_GROUP, point.name, BlockPos(point.x, point.y, point.z), FOREVER)
		}
	}

	private fun islandName(island: String): String =
		Island.entries.firstOrNull { it.name == island }?.displayName ?: island

	private fun holds(x: Int, y: Int, z: Int): Boolean =
		live.any { it.group != SAVED_GROUP && it.pos.x == x && it.pos.y == y && it.pos.z == z }

	private fun expiryOf(seconds: Double): Long = System.currentTimeMillis() + (seconds * MILLIS).toLong()

	private const val NOT_IN_WORLD = "You are not in a world."
	private const val SWITCHED_OFF = "Waypoints is switched off."
	private const val MIN_FIXED_ALPHA = 0.2f
	private const val OUT_OF_BOUNDS = "Waypoint out of bounds."
	private const val DEFAULT_TITLE = "Waypoint"
	private const val PARTY_COMMAND = "pc"
	private const val HIDE_LABEL = " [Hide]"
	private const val REDRAW_LABEL = " [Redraw]"
	private const val HIDE_COMMAND = "/dhen waypoint hide"
	private const val REDRAW_COMMAND = "/dhen waypoint redraw"
	private const val FOREVER = 0L
	private const val MILLIS = 1000.0
	private const val PERCENT = 100.0
	private const val HALF_BLOCK = 0.5
	private const val LABEL_LIFT = 1.7
	private const val HIDE_DISTANCE = 5.0
}

internal class LiveWaypoint(
	val group: String,
	val title: String,
	val pos: BlockPos,
	val expiry: Long
) {
	val memo: TextMemo = DhenType.memo()
	var label: String = title
	var metres: Int = -1
	var cutoff: Int = -1
	var scale: Float = 1f
	var color: Int = 0
	var labelColor: Int = 0
	var visible: Boolean = false
}
