package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.util.NO_DIGITS
import io.github.dzkchen.dhen.util.digits
import java.util.regex.Matcher
import java.util.regex.Pattern

enum class SidebarField(pattern: String) {
	LOBBY_CODE("\\s*§.(?<v>(?:\\d{2}/?){3}) §8.*"),
	DATE("\\s*(?<v>(?:§.)*(?:(?:Late|Early) )?(?:Spring|Summer|Autumn|Winter) \\d+(?:st|nd|rd|th)?).*"),
	TIME("\\s*(?<v>(?:§.)*\\d+:\\d+(?:am|pm))\\s*(?<symbol>§.[☽☀⚡☔])?\\s*"),
	PURSE("\\s*(?:§.)*(?:Piggy|Purse): §6(?<v>[\\d,.]+).*"),
	BITS("\\s*(?:§.)*Bits: §b(?<v>[\\d,.]+).*"),
	MOTES("\\s*(?:§.)*Motes: (?:§.)*(?<v>[\\d,]+).*"),
	COPPER("\\s*(?:§.)*Copper: (?:§.)*(?<v>[\\d,]+).*"),
	SOWDUST("\\s*(?:§.)*Sowdust: (?:§.)*(?<v>[\\d,]+)"),
	NORTH_STARS("\\s*North Stars: §d(?<v>[\\w,]+).*"),
	GEMS("\\s*(?:§.)*Gems: (?:§.)*(?<v>[\\d,]+).*"),
	HEAT("\\s*Heat: (?<v>§.(?:\\d+|IMMUNE)♨?)\\s*"),
	COLD("\\s*(?:§.)*Cold: §.(?<v>-?\\d+)❄.*"),
	VISITING("\\s*(?<v>§.✌ §.\\(§.\\d+(?:§.)?/(?<max>\\d+)(?:§.)?\\))\\s*"),
	PROFILE_TYPE("\\s*(?<v>§7♲ §7Ironman|§a☀ §aStranded|§.Ⓑ §.Bingo).*"),
	FOOTER("\\s*(?<v>§e(?:www|alpha)\\.hypixel\\.net)\\s*"),
	LOCATION("\\s*(?<v>§\\d. §.(?<area>.*?))\\s*");

	internal val matcher: Matcher = Pattern.compile(pattern).matcher("")
}

enum class PowderKind(val displayName: String, val color: String) {
	MITHRIL("Mithril", "§2"),
	GEMSTONE("Gemstone", "§d"),
	GLACITE("Glacite", "§b")
}

object SidebarValues {
	private const val MIN_UNKNOWN_LENGTH = 4
	private const val DECIMAL_POINT = '.'
	private const val SLAYER_BLOCK_LINES = 3

	private val fields = SidebarField.entries
	private val powderKinds = PowderKind.entries

	private val slayerLine = matcher("\\s*(?:§.)*Slayer Quest\\s*")
	private val powderLine =
		matcher("\\s*(?:§.)*᠅ §.(?<type>Gemstone|Mithril|Glacite)(?: Powder)?(?:§.)*:? (?:§.)*(?<amount>[\\d,.]+).*")
	private val sowdustGainedLine =
		matcher("\\s*(?:§.)*Sowdust: (?:§.)*[\\d,.kKmMbB]+ §7\\(\\+[\\d.kKmMbB]+\\).*")
	private val objectiveLine = matcher("\\s*(?:§.)*(?:Objective|Quest).*")
	private val objectiveTailLine = matcher(
		"\\s*(?:§eProtect Elle §7\\(§.\\d+%§7\\)|§.\\(§.[\\w,.]+§.\\/§.[\\w,.]+§.\\)|§f Mages.*|§f Barbarians.*" +
			"|§edefeat Kuudra|§eand stun him|§.Fish \\d .*[fF]ish §.[✖✔])"
	)
	private val strayObjectiveLine = matcher(
		"\\s*(?:§eMine \\d+ .*|§eKill 100 Automatons|§eFind a Jungle Key|§eFind the \\d+ Missing Pieces?" +
			"|§eTalk to the Goblin King|§eBring items to Moby|Glowing Mushroom §8x\\d)"
	)

	private val values = arrayOfNulls<String>(fields.size)
	private val numbers = LongArray(fields.size) { NO_DIGITS }
	private val powderText = arrayOfNulls<String>(powderKinds.size)
	private val slayerBlock = ArrayList<String>(SLAYER_BLOCK_LINES)
	private val objectiveBlock = ArrayList<String>()
	private val unknownBlock = ArrayList<String>()
	private var claimed = BooleanArray(0)

	var area: String? = null
		private set

	var maxVisitors: Long = NO_DIGITS
		private set

	val slayer: List<String> get() = slayerBlock

	val objective: List<String> get() = objectiveBlock

	val unknown: List<String> get() = unknownBlock

	fun text(field: SidebarField): String? = values[field.ordinal]

	fun noTradeProfile(): Boolean = text(SidebarField.PROFILE_TYPE) != null

	fun number(field: SidebarField): Long = numbers[field.ordinal]

	fun powder(kind: PowderKind): String? = powderText[kind.ordinal]

	internal fun read(lines: List<String>) {
		reset()
		if (claimed.size < lines.size) claimed = BooleanArray(lines.size)
		for (index in lines.indices) claim(lines, index)
		SidebarEvents.read(lines, claimed)
		SidebarEvents.sweep(lines, claimed)
		for (index in lines.indices) {
			if (claimed[index] || lines[index].trim().length < MIN_UNKNOWN_LENGTH) continue
			unknownBlock += lines[index].removePrefix(" ")
		}
	}

	internal fun reset() {
		values.fill(null)
		numbers.fill(NO_DIGITS)
		powderText.fill(null)
		area = null
		maxVisitors = NO_DIGITS
		slayerBlock.clear()
		objectiveBlock.clear()
		unknownBlock.clear()
		claimed.fill(false)
		SidebarEvents.reset()
	}

	private fun claim(lines: List<String>, index: Int) {
		if (claimed[index]) return
		val line = lines[index]
		if (slayerBlock.isEmpty() && slayerLine.reset(line).matches()) return slayer(lines, index)
		if (powderLine.reset(line).matches() && powdered(index)) return
		if (sowdustGainedLine.reset(line).matches()) {
			claimed[index] = true
			return
		}
		for (field in fields) {
			if (values[field.ordinal] != null || !field.matcher.reset(line).matches()) continue
			claimed[index] = true
			return keep(field)
		}
		if (strayObjectiveLine.reset(line).matches()) {
			claimed[index] = true
			return
		}
		if (objectiveBlock.isEmpty() && objectiveLine.reset(line).matches()) objective(lines, index)
	}

	private fun keep(field: SidebarField) {
		val matcher = field.matcher
		val text = matcher.group("v")
		values[field.ordinal] = when (field) {
			SidebarField.TIME -> matcher.group("symbol")?.let { "$text $it" } ?: text
			else -> text
		}
		numbers[field.ordinal] = digits(text.substringBefore(DECIMAL_POINT))
		when (field) {
			SidebarField.LOCATION -> area = matcher.group("area")
			SidebarField.VISITING -> maxVisitors = digits(matcher.group("max"))

			else -> Unit
		}
	}

	private fun powdered(index: Int): Boolean {
		val name = powderLine.group("type")
		val kind = powderKinds.firstOrNull { it.displayName == name } ?: return false
		claimed[index] = true
		powderText[kind.ordinal] = powderLine.group("amount")
		return true
	}

	private fun objective(lines: List<String>, index: Int) {
		var at = index
		while (at < lines.size && !claimed[at]) {
			if (at > index + 1 && !objectiveTailLine.reset(lines[at]).matches()) return
			claimed[at] = true
			objectiveBlock += lines[at].removePrefix(" ")
			at++
		}
	}

	private fun slayer(lines: List<String>, index: Int) {
		for (offset in 0 until SLAYER_BLOCK_LINES) {
			val at = index + offset
			if (at >= lines.size) return
			claimed[at] = true
			slayerBlock += lines[at].removePrefix(" ")
		}
	}

	private fun matcher(pattern: String) = Pattern.compile(pattern).matcher("")
}
