package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.event.withoutCodes
import java.util.Locale

internal class CompactTabRow(val text: String, val title: Boolean = false, val player: String? = null)

internal class TabToggle {
	private var held = false
	private var open = false

	fun update(down: Boolean, screen: Boolean, toggle: Boolean): Boolean {
		if (!screen && toggle && down && !held) open = !open
		held = down
		if (!toggle) open = false
		return !screen && if (toggle) open else down
	}

	fun reset() {
		held = false
		open = false
	}
}

internal object CompactTabModel {
	private const val SOURCE_ROWS = 20
	private const val RENDER_ROWS = 22
	private val playerName = Regex("^\\[[\\d,]+] (?:\\[\\w+[+]*] )?(\\w+)")
	private val effectCount = Regex("You have ([0-9]+) (?:active|non-god) effects?.*")
	private val upgrades = Regex("([A-Za-z ]+)( [\\w ]+)")

	fun name(line: String): String? = playerName.find(withoutCodes(line))?.groupValues?.get(1)

	fun frame(text: String, hideAdverts: Boolean): List<String> = text.split('\n').filter {
		it.isNotBlank() && (!hideAdverts || !withoutCodes(it).contains("HYPIXEL.NET"))
	}

	fun columns(lines: List<String>, footer: String, hideFireSales: Boolean): List<List<CompactTabRow>> {
		val source = linkedMapOf<String, MutableList<String>>()
		var offset = 0
		while (offset < lines.size) {
			val title = lines[offset].trim()
			val rows = source.getOrPut(title) { ArrayList() }
			rows.addAll(lines.subList(offset + 1, minOf(offset + SOURCE_ROWS, lines.size)))
			offset += SOURCE_ROWS
		}
		val other = footer(footer)
		if (other.isNotEmpty()) source.getOrPut("§2§lOther") { ArrayList() }.addAll(other)
		val result = ArrayList<MutableList<CompactTabRow>>()
		var current = ArrayList<CompactTabRow>()
		var lastTitle: String? = null
		fun append(row: CompactTabRow) {
			if (current.size >= RENDER_ROWS) {
				result += current
				current = ArrayList()
			}
			current += row
		}
		for ((title, rows) in source) {
			var section = ArrayList<String>()
			var skipping = false
			fun flush() {
				if (section.isEmpty()) return
				if (current.size >= RENDER_ROWS) {
					result += current
					current = ArrayList()
				} else if (current.isNotEmpty()) append(CompactTabRow(""))
				if (lastTitle != title) {
					append(CompactTabRow(title, title = true))
					lastTitle = title
				}
				for (line in section) append(CompactTabRow(line, player = name(line)))
				section = ArrayList()
			}
			for (line in rows) {
				val plain = withoutCodes(line)
				if (plain.isEmpty()) {
					flush()
					skipping = false
					continue
				}
				if (!plain.startsWith(' ') && name(line) == null) skipping = hideFireSales && plain.startsWith("Fire Sales: (")
				if (!skipping) section += line
			}
			flush()
		}
		if (current.isNotEmpty()) result += current
		return result
	}

	internal fun footer(text: String): List<String> {
		val lines = text.split('\n')
		val god = lines.firstOrNull { withoutCodes(it).startsWith("You have a God Potion active! ") }
			?.let { withoutCodes(it).substringAfter("You have a God Potion active! ") }
		val count = lines.firstNotNullOfOrNull { effectCount.matchEntire(withoutCodes(it))?.groupValues?.get(1) } ?: "0"
		val result = ArrayList<String>()
		var upgrading = false
		for (line in lines) {
			val plain = withoutCodes(line)
			when {
				plain.contains("HYPIXEL.NET") -> Unit
				plain == "Active Effects" -> {
					result += if (god == null) "§a§lActive Effects: $count" else "§a§lActive Effects:"
					if (god != null) result += "§cGod Potion: $god"
				}
				plain.startsWith("You have a God Potion active!") || effectCount.matches(plain) || plain.startsWith("Use \"/effects\"") -> Unit
				plain.startsWith("No effects active.") || plain == "ground to buff yourself!" -> Unit
				plain.startsWith("Not active! Obtain booster cookies") || plain == "shop in the hub." -> {
					if (result.lastOrNull() != "§7 Not Active") result += "§7 Not Active"
				}
				plain == "No Buffs active." && result.lastOrNull()?.let(::withoutCodes) == "Dungeon Buffs" -> result += "§7 None Found"
				plain == "No Power Ups active." && result.lastOrNull()?.let(::withoutCodes) == "Active Power Ups" -> result += "§7 None"
				plain == "Upgrades" -> {
					upgrading = true
					result += line
				}
				plain.isBlank() -> {
					upgrading = false
					result += ""
				}
				upgrading && line.startsWith("§e") && upgrades.matches(plain) -> {
					val match = upgrades.matchEntire(plain)!!
					result += " ${match.groupValues[1]}"
					result += match.groupValues[2]
				}
				else -> result += if (line.contains("§l")) line else " $line"
			}
		}
		while (result.lastOrNull()?.isBlank() == true) result.removeLast()
		return result
	}
}

internal class CompactTabPlayer(
	val original: String,
	val level: Int,
	val levelText: String,
	val name: String,
	val coloredName: String,
	val suffix: String,
	val faction: String,
	val bingo: Int,
	val ironman: Boolean
) {
	val sortName: String = name.lowercase(Locale.ROOT).replace("_", "")

	companion object {
		private val pattern = Regex("^(?!SB Level).*\\[((?:§.)*[\\d,]+)(?:§.)*] (.*)")

		fun read(line: String, crimson: Boolean, bingoRank: (String) -> Int): CompactTabPlayer? {
			val match = pattern.matchEntire(line) ?: return null
			val levelText = match.groupValues[1]
			val level = withoutCodes(levelText).replace(",", "").toIntOrNull() ?: return null
			val parts = match.groupValues[2].split(' ')
			val index = if (withoutCodes(parts[0]).startsWith('[')) 1 else 0
			val colored = parts.getOrNull(index) ?: return null
			val name = withoutCodes(colored)
			if (name.isEmpty()) return null
			var suffix = parts.drop(index + 1).joinToString(" ")
			val faction = if (!crimson) "" else when {
				suffix.contains("⚒") -> "§c⚒"
				suffix.contains("ቾ") -> "§5ቾ"
				else -> ""
			}
			if (faction.isNotEmpty()) suffix = suffix.replace(Regex("(?:§.)*${faction.last()}"), "")
			return CompactTabPlayer(line, level, levelText, name, parts.take(index + 1).joinToString(" "), suffix,
				faction, bingoRank(line), suffix.contains("♲"))
		}
	}
}
