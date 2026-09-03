package io.github.dzkchen.dhen.features.visual

import kotlin.math.abs
import kotlin.math.sqrt

internal const val CHAT_GROUP = "chat"
internal const val PING_GROUP = "ping"
internal const val SAVED_GROUP = "saved"

internal const val COORDINATE_LIMIT = 5000
internal const val GUILD_CHANNEL = "Guild"
internal const val PARTY_CHANNEL = "Party"

internal val CHAT_COORDINATES = Regex(
	"^(?:([\\w-]+) > )?(?:\\[\\d+] )?(?:[^ ] )?(?:\\[[^]]*?] ?)?(\\w{1,16})(?: [ቾ⚒])?" +
		": x: (-?\\d+),? y: (-?\\d+),? z: (-?\\d+)(.*)$"
)

internal class ChatCoordinates(
	val channel: String,
	val sender: String,
	val x: Int,
	val y: Int,
	val z: Int,
	val note: String
)

internal fun chatCoordinates(stripped: String): ChatCoordinates? {
	if (!stripped.contains(COORDINATE_MARK)) return null
	val groups = (CHAT_COORDINATES.find(stripped) ?: return null).groupValues
	return ChatCoordinates(
		groups[1],
		groups[2],
		groups[3].toIntOrNull() ?: return null,
		groups[4].toIntOrNull() ?: return null,
		groups[5].toIntOrNull() ?: return null,
		groups[6].trim().removePrefix(NOTE_MARK).trim()
	)
}

internal fun outOfBounds(x: Int, y: Int, z: Int): Boolean =
	abs(x) > COORDINATE_LIMIT || abs(y) > COORDINATE_LIMIT || abs(z) > COORDINATE_LIMIT

internal fun waypointLabel(title: String, metres: Int, cutoff: Int): String =
	if (cutoff > 0 && metres <= cutoff) title else "$title (${metres}m)"

internal fun dynamicOpacity(distance: Double): Float = when {
	!distance.isFinite() -> MAX_OPACITY
	distance <= FADE_START -> MIN_OPACITY
	distance >= FADE_END -> MAX_OPACITY
	else -> MIN_OPACITY + (MAX_OPACITY - MIN_OPACITY) * ((distance - FADE_START) / (FADE_END - FADE_START)).toFloat()
}

internal fun labelScale(distance: Double, textScale: Double): Float =
	maxOf(1f, (distance * DISTANCE_GROWTH).toFloat()) * textScale.toFloat()

internal fun coordinateLine(x: Int, y: Int, z: Int, note: String): String {
	val coordinates = "x: $x, y: $y, z: $z"
	return if (note.isEmpty()) coordinates else "$coordinates $NOTE_MARK $note"
}

internal class WarpPoint(val name: String, val x: Double, val y: Double, val z: Double, val extraBlocks: Int = 0)

internal val HUB_WARPS = listOf(
	WarpPoint("hub", 0.5, 77.0, -0.5),
	WarpPoint("castle", -250.0, 130.0, 45.0),
	WarpPoint("wizard", 44.5, 119.0, 93.5),
	WarpPoint("crypt", -160.5, 62.0, -106.5, extraBlocks = 10),
	WarpPoint("stonks", -36.5, 70.0, -81.5),
	WarpPoint("da", 91.5, 75.0, 173.5),
	WarpPoint("taylor", 29.5, 73.0, -41.5),
	WarpPoint("museum", 29.5, 72.0, 1.5)
)

internal fun nearestWarp(x: Double, y: Double, z: Double): String {
	var best = HUB_WARPS[0]
	var bestCost = Double.MAX_VALUE
	for (warp in HUB_WARPS) {
		val cost = span(warp.x - x, warp.y - y, warp.z - z) + warp.extraBlocks
		if (cost < bestCost) {
			bestCost = cost
			best = warp
		}
	}
	return best.name
}

internal fun span(dx: Double, dy: Double, dz: Double): Double = sqrt(dx * dx + dy * dy + dz * dz)

internal class SavedPoint(val name: String, val island: String, val x: Int, val y: Int, val z: Int)

internal class WaypointBook(private val stored: () -> String) {
	private var source: String? = null
	private val entries = linkedMapOf<String, SavedPoint>()

	fun all(): Map<String, SavedPoint> = current()

	private fun current(): Map<String, SavedPoint> {
		val text = stored()
		if (text === source) return entries
		source = text
		entries.clear()
		for (line in text.split(SAVED_SEPARATOR)) {
			val point = readSaved(line) ?: continue
			entries[point.name] = point
		}
		return entries
	}
}

internal fun readSaved(line: String): SavedPoint? {
	val parts = line.split(' ')
	if (parts.size != SAVED_FIELDS) return null
	val name = parts[0]
	if (name.isEmpty()) return null
	return SavedPoint(
		name,
		parts[1],
		parts[2].toIntOrNull() ?: return null,
		parts[3].toIntOrNull() ?: return null,
		parts[4].toIntOrNull() ?: return null
	)
}

internal fun formatSaved(entries: Collection<SavedPoint>): String =
	entries.joinToString(SAVED_SEPARATOR) { "${it.name} ${it.island} ${it.x} ${it.y} ${it.z}" }

internal fun savedNameFault(name: String): String? = when {
	name.isBlank() -> "A waypoint needs a name."
	name.any { it.isWhitespace() } -> "A waypoint name cannot contain a space."
	else -> null
}

private const val COORDINATE_MARK = "x: "
private const val NOTE_MARK = "|"
private const val SAVED_SEPARATOR = "\n"
private const val SAVED_FIELDS = 5
private const val MIN_OPACITY = 0.2f
private const val MAX_OPACITY = 1.0f
private const val FADE_START = 4.5
private const val FADE_END = 100.0
private const val DISTANCE_GROWTH = 0.05
