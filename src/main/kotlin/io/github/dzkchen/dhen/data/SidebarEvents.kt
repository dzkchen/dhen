package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.withoutCodes
import java.util.regex.Matcher
import java.util.regex.Pattern

private const val AUTO_CLOSING = "(?:§.)*Auto-closing in: §c(?:\\d+:)?\\d+"
private const val STARTING_IN = "(?:§.)*Starting in: §.(?:\\d+:)?\\d+"
private const val TIME_ELAPSED = "(?:§.)*Time Elapsed: (?:§.)*(?:\\w+[ydhms] ?)+"
private const val TIME_LEFT = "(?:§.)*Time Left: (?:§.)*[\\w:,.\\s]+"
private fun matcher(pattern: String) = Pattern.compile("\\s*(?:$pattern)").matcher("")

private const val HEALTH = "[❤]"

enum class SidebarEvent(val label: String, vararg patterns: String) {
	VOTING(
		"Voting",
		"§6Year \\d+ Votes",
		"§.\\|+(?:§f)?\\|+ §.+",
		"§7Waiting for|§7your vote\\.\\.\\."
	),
	SERVER_CLOSE("Server Close", "§cServer closing.*"),
	DUNGEONS(
		"Dungeons",
		"§cNo Alive Dragons|§8- (?:§.)+[\\w\\s]+Dragon§a [\\w,.]+(?:§.$HEALTH)?",
		AUTO_CLOSING,
		STARTING_IN,
		"Keys: §.■ §.[✗✓] §.■ §a.x",
		TIME_ELAPSED,
		"(?:§.)*Cleared: (?:§.)*[\\w,.]+% (?:§.)*\\((?:§.)*[\\w,.]+(?:§.)*\\)",
		"§3§lSolo",
		"(?:§.)*\\[\\w] (?:§.)*\\w{2,16} (?:(?:§.)*\\[Lvl?[\\w,.]*]?|(?:§.)*[\\w,.]+(?:§.)*.?)",
		"§. - §.(?:Healthy|Reinforced|Laser|Chaos)§a [\\w,.]*(?:§c$HEALTH)?"
	),
	KUUDRA(
		"Kuudra",
		AUTO_CLOSING,
		STARTING_IN,
		TIME_ELAPSED,
		"(?:§.)*Instance Shutdown In: (?:§.)*(?:\\w+[ydhms] ?)+",
		"(?:§.)*Wave: (?:§.)*\\d+(?:§.)*(?: §.- §.\\d+:\\d+)?",
		"(?:§.)*Tokens: §.[\\w,]+",
		"(?:§.)*Submerges In: (?:§.)*[\\w\\s?]+"
	),
	DOJO(
		"Dojo",
		"(?:§.)*Challenge: (?:§.)*.+",
		"(?:§.)*Difficulty: (?:§.)*.+",
		"(?:§.)*Points: (?:§.)*[\\w.]+.*",
		"(?:§.)*Time: (?:§.)*[\\w.]+.*"
	),
	DARK_AUCTION("Dark Auction", STARTING_IN, TIME_LEFT, "Current Item:"),
	JACOB_CONTEST("Jacob's Contest", "§eJacob's Contest"),
	JACOB_MEDALS("Jacob's Medals", "§[6fc]§l(?:GOLD|SILVER|BRONZE) §fmedals: §[6fc][\\d.,]+"),
	TRAPPER("Trapper", "(?:§.)*Pelts: (?:§.)*[\\d,]+.*", "(?:§.)*Tracker Mob Location:"),
	GARDEN(
		"Garden",
		"\\s*§cLocked.*",
		"\\s*(?:§.)*(?:Barn )?Pasting§7: (?:§.)*[\\d,.]+%?",
		"\\s*(?:§.)*Cleanup(?:§.)*: (?:§.)*.*"
	),
	FLIGHT_DURATION("Flight Duration", "\\s*Flight Duration: §a(?::?\\d{1,3})*"),
	WINTER(
		"Winter",
		"(?:§.)*Event Start: §.[\\d:]+",
		"(?:§.)*Next Wave: (?:§.)*(?:[\\d:]+|Soon!)",
		"(?:§.)*Wave \\d+",
		"(?:§.)*Magma Cubes Left: §.-?\\d+",
		"(?:§.)*Your Total Damage: §.[\\d+,.]+.*",
		"(?:§.)*Your Cube Damage: §.[\\d+,.]+"
	),
	NEW_YEAR("New Year", "§dNew Year Event!§f \\d*:?\\d+"),
	SPOOKY("Spooky", "§6Spooky Festival§f \\d*:?\\d+"),
	BROODMOTHER("Broodmother", "§4Broodmother§7: §[e64](?:Slain|Dormant|Soon|Awakening|Imminent|Alive!)"),
	MINING(
		"Mining Events",
		"§9Wind Compass",
		"\\s*(?:§.|[⋖⋗≈])+\\s*(?:§.|[⋖⋗≈])*\\s*",
		"Nearby Players: §.(?:\\d+|N/A)(?: §cMAX)?",
		"Event: §.§[lL].*",
		"Zone: §.*",
		"Remaining: §a(?:\\d+ Tasty Mithril|FULL)",
		"Your Tasty Mithril: §c\\d+.*",
		"Tickets: §a\\d+ §7\\(\\d+(?:\\.\\d)?%\\)",
		"Pool: §6\\d+",
		"Your kills: §c\\d+ ☠(?: §a\\(\\+\\d+\\))?",
		"Remaining: §a\\d+ goblins?",
		"Event Bonus: §6\\+\\d+[☘]",
		"Fossil Dust: (?:§f)*[\\d.,]+.*",
		"Find tickets on the|ground and bring them|to the raffle box",
		"§7Give Tasty Mithril to Don!",
		"§7Kill goblins!",
		"(?:§.)*Not started.*"
	),
	GALATEA(
		"Galatea",
		"(?:§f)?Whispers: §[36][\\w,.]+.*",
		"\\s*§aHOTF§f: §a[\\w,.]+.*",
		"§eAgatha's Contest §a.*",
		"§eMiria's Contest §a.*"
	),
	SAFARI("Safari", "Captured Mobs: §e\\d+"),
	DAMAGE("Damage", "(?:Protector|Dragon) HP: §a[\\d,.]* §c$HEALTH", "Your Damage: §c[\\d,.]+"),
	MAGMA_BOSS(
		"Magma Boss",
		"§7Boss: §[c6e]\\d+%",
		"§7Damage Soaked:",
		"§6Kill the Magmas:",
		"(?:(?:§.)*▎+)+.*",
		"§cThe boss is (?:re)?forming!",
		"§7Boss Health:",
		"§.[\\w,.]+§f/§a10M§c$HEALTH"
	),
	CARNIVAL(
		"Carnival",
		"§eCarnival§f \\d+(?::\\d+)*",
		"(?:§f)*Carnival Tokens: §e[\\d,]+",
		"§.§l(?:Catch a Fish|Fruit Digging|Zombie Shootout)",
		TIME_LEFT,
		"(?:§f)?Catch Streak: §.\\d+",
		"(?:§f)?Fruits: §.\\d+§./§.\\d+",
		"(?:§f)?Accuracy: §.\\d+(?:\\.\\d+)?%",
		"(?:§f)?Kills: §.\\d+",
		"(?:§f)?Score: §.\\d+.*"
	),
	RIFT(
		"Rift",
		"Effigies: (?:(?:§[7c])?⧯)*",
		"§6Hot Dog Contest",
		TIME_LEFT,
		"Eaten: §.\\d+/\\d+",
		"Time spent sitting|with Ävaeìkx: .*",
		"Hay Eaten: §.[\\d,.]+/[\\d,.]+",
		"Clues: §.\\d+/\\d+",
		"§eFirst Up|Find and talk with Barry",
		"Protestors handled: §b\\d+/\\d+",
		"§c§lTIME SLICED!",
		"\\s*Big damage in: §d[\\w\\s]+",
		"(?:§f)?Rift Dimension"
	),
	ESSENCE("Essence", "\\s*.*Essence: §.-?\\d+(?::?,\\d{3})*(?:\\.\\d+)?"),
	QUEUE(
		"Queue",
		"Queued:.*",
		"Tier: §e.*",
		"Position: (?:§.)*#\\d+ (?:§.)*Since: .*",
		"§aWaiting on party leader!"
	),
	ANNIVERSARY("Anniversary", "(?:§d\\d+(?:st|nd|rd|th) Anniversary|§bCentury Raffle)§f (?:\\d|:)+"),
	ACTIVE_TABLIST("Active Tablist Events", "§aTraveling Zoo§f \\d*:\\d+"),
	STARTING_SOON_TABLIST("Starting Soon Tablist Events"),
	REDSTONE("Redstone", "\\s*(?:§.)*⚡ §cRedstone: (?:§.)*\\d+%");

	internal val matchers: Array<Matcher> = Array(patterns.size) { matcher(patterns[it]) }

	internal fun matching(line: String): Int {
		for (index in matchers.indices) if (matchers[index].reset(line).matches()) return index
		return -1
	}

	companion object {
		val labels: List<String> = entries.map { it.label }

		private val byLabel: Map<String, SidebarEvent> = entries.associateBy { it.label }

		fun of(label: String): SidebarEvent? = byLabel[label]
	}
}

internal object SidebarEvents {
	private const val CARNIVAL_HEADER = 0
	private const val WIND_COMPASS = 0
	private const val WIND_ARROW = 1
	private const val NEARBY_PLAYERS = 2
	private const val ZONE_EVENT = 3
	private const val ZONE_NAME = 4
	private const val FREEZING_BONUS = 11
	private const val FOSSIL_DUST = 12
	private const val RESET_CODE = "§r"
	private const val SERVER_CODE = "§8"
	private const val EVENT_PREFIX = "Event: "
	private const val ZONE_PREFIX = "Zone: "
	private const val CANDY_PREFIX = "Your Candy: "
	private const val CANDY_LABEL = "§7Your Candy: "
	private const val CANDY_MISSING = "§cCandy not found"
	private const val BETTER_TOGETHER = "§dBetter Together"
	private const val WAVE_SOON = "Soon!"
	private const val ANNIVERSARY_EVENT = "SkyBlock Anniversary"

	private val MITHRIL = 5..6
	private val RAFFLE = 7..8
	private val GOBLINS = 9..10

	private val blockedTablistEvents = arrayOf("Spooky Festival", "Carnival", "New Year Celebration")

	private val events = SidebarEvent.entries
	private val blocks = Array(events.size) { ArrayList<String>() }
	private val follows = Array(events.size) { followCounts(events[it]) }

	private val footer = matcher("§e(?:www|alpha)\\.hypixel\\.net")
	private val eventName = matcher("(?:§.)*Event: (?<event>.*)")
	private val eventEnds = matcher("Ends In: (?<time>.*)")
	private val eventStarts = matcher("Starts In: (?<time>.*)")

	private val broken = arrayOf(
		matcher("§.§l⚡ §cRedston"),
		matcher("§ce: §e§b\\d+%"),
		matcher("Starting in: §a0 §c[\\d:]+"),
		matcher("(?:§.)*᠅ §.(?:Gemstone|Mithril|Glacite)(?: Powder)?.*")
	)

	private val claimOnly = IntArray(events.size).also {
		it[SidebarEvent.BROODMOTHER.ordinal] = 1
		it[SidebarEvent.MINING.ordinal] = 4
		it[SidebarEvent.RIFT.ordinal] = 1
		it[SidebarEvent.ACTIVE_TABLIST.ordinal] = 1
	}

	fun lines(event: SidebarEvent): List<String> = when {
		!onIsland(event) -> blocks[event.ordinal].also { it.clear() }
		event == SidebarEvent.BROODMOTHER -> broodmother()
		event == SidebarEvent.ACTIVE_TABLIST || event == SidebarEvent.STARTING_SOON_TABLIST -> tablistEvent(event)
		else -> blocks[event.ordinal]
	}

	internal fun read(lines: List<String>, claimed: BooleanArray) {
		for (event in events) {
			if (!onIsland(event)) continue
			val block = blocks[event.ordinal]
			gather(event, lines, claimed, block)
			shape(event, block)
		}
	}

	fun onIsland(event: SidebarEvent): Boolean {
		val island = SkyBlockLocation.island
		return when (event) {
			SidebarEvent.VOTING, SidebarEvent.CARNIVAL -> island == Island.HUB
			SidebarEvent.DUNGEONS -> island == Island.CATACOMBS
			SidebarEvent.KUUDRA -> island == Island.KUUDRA
			SidebarEvent.DOJO, SidebarEvent.MAGMA_BOSS -> island == Island.CRIMSON_ISLE
			SidebarEvent.DARK_AUCTION -> island == Island.DARK_AUCTION
			SidebarEvent.TRAPPER -> island == Island.THE_FARMING_ISLANDS
			SidebarEvent.GARDEN -> island.gardenIsland
			SidebarEvent.FLIGHT_DURATION -> island.personalIsland
			SidebarEvent.WINTER -> island == Island.JERRYS_WORKSHOP
			SidebarEvent.BROODMOTHER -> island == Island.SPIDERS_DEN
			SidebarEvent.MINING -> island.advancedMining
			SidebarEvent.DAMAGE -> island == Island.THE_END
			SidebarEvent.RIFT -> island == Island.THE_RIFT
			SidebarEvent.REDSTONE -> island.privateIsland
			else -> true
		}
	}

	internal fun sweep(lines: List<String>, claimed: BooleanArray) {
		for (index in lines.indices) {
			if (claimed[index]) continue
			for (matcher in broken) {
				if (!matcher.reset(lines[index]).matches()) continue
				claimed[index] = true
				break
			}
		}
	}

	internal fun reset() {
		for (block in blocks) block.clear()
	}

	private fun gather(event: SidebarEvent, lines: List<String>, claimed: BooleanArray, block: ArrayList<String>) {
		if (event == SidebarEvent.MINING) {
			for (index in lines.indices) if (event.matching(lines[index]) >= 0) claimed[index] = true
			return mining(event, lines, block)
		}
		val counts = follows[event.ordinal]
		val shown = event.matchers.size - claimOnly[event.ordinal]
		for (index in lines.indices) {
			val at = event.matching(lines[index])
			if (at < 0) continue
			claimed[index] = true
			if (at >= shown) continue
			block += lines[index].removePrefix(" ")
			for (offset in 1..counts[at]) {
				val next = index + offset
				if (next >= lines.size) break
				if (footer.reset(lines[next]).matches()) continue
				claimed[next] = true
				block += lines[next].removePrefix(" ")
			}
		}
		if (event == SidebarEvent.CARNIVAL && first(event, CARNIVAL_HEADER, lines) == null) block.clear()
	}

	private fun shape(event: SidebarEvent, block: ArrayList<String>) = when (event) {
		SidebarEvent.SERVER_CLOSE -> replace(block) { it.substringBefore(SERVER_CODE) }
		SidebarEvent.DUNGEONS -> replace(block) { it.removePrefix(RESET_CODE) }
		SidebarEvent.GARDEN, SidebarEvent.FLIGHT_DURATION -> replace(block) { it.trim() }
		SidebarEvent.WINTER -> dropFinishedWaves(block)
		SidebarEvent.SPOOKY -> candy(block)
		else -> Unit
	}

	private fun mining(event: SidebarEvent, lines: List<String>, block: ArrayList<String>) {
		val compass = first(event, WIND_COMPASS, lines)
		val arrow = first(event, WIND_ARROW, lines)
		if (compass != null && arrow != null) {
			block += compass.removePrefix(" ")
			block += arrow.removePrefix(" ")
		}
		first(event, NEARBY_PLAYERS, lines)?.let {
			block += BETTER_TOGETHER
			block += " ${it.removePrefix(" ")}"
		}
		first(event, ZONE_EVENT, lines)?.let {
			block += it.removePrefix(" ").removePrefix(EVENT_PREFIX)
			first(event, ZONE_NAME, lines)?.let { zone -> block += "in ${zone.removePrefix(" ").removePrefix(ZONE_PREFIX)}" }
		}
		allOf(event, MITHRIL, lines, block)
		allOf(event, RAFFLE, lines, block)
		allOf(event, GOBLINS, lines, block)
		first(event, FREEZING_BONUS, lines)?.let { block += it.removePrefix(" ") }
		first(event, FOSSIL_DUST, lines)?.let { block += it.removePrefix(" ") }
	}

	private fun broodmother(): List<String> {
		val block = blocks[SidebarEvent.BROODMOTHER.ordinal]
		block.clear()
		for (line in TabWidgetState.lines(TabWidget.BROODMOTHER)) block += line.trim()
		return block
	}

	private fun tablistEvent(event: SidebarEvent): List<String> {
		val block = blocks[event.ordinal]
		block.clear()
		val header = TabWidgetState.lines(TabWidget.EVENT).firstOrNull() ?: return block
		if (!eventName.reset(header).matches()) return block
		val name = eventName.group("event")
		val active = event == SidebarEvent.ACTIVE_TABLIST
		if (active && blocked(withoutCodes(name))) return block
		val timer = if (active) eventEnds else eventStarts
		for (line in TabWidgetState.stripped(TabWidget.EVENT)) {
			if (!timer.reset(line).matches()) continue
			block += name
			block += if (active) " Ends in: §e${timer.group("time")}" else " Starts in: §e${timer.group("time")}"
			return block
		}
		return block
	}

	private fun blocked(name: String): Boolean = name in blockedTablistEvents || name.endsWith(ANNIVERSARY_EVENT)

	private fun candy(block: ArrayList<String>) {
		if (block.isEmpty()) return
		block += CANDY_LABEL
		val tail = TablistState.strippedFooter
		if (tail.isEmpty()) return
		for (line in tail.lineSequence()) {
			if (!line.startsWith(CANDY_PREFIX)) continue
			block += line.removePrefix(CANDY_PREFIX)
			return
		}
		block += CANDY_MISSING
	}

	private fun dropFinishedWaves(block: ArrayList<String>) {
		var kept = 0
		for (index in block.indices) {
			if (block[index].endsWith(WAVE_SOON)) continue
			block[kept++] = block[index]
		}
		while (block.size > kept) block.removeAt(block.size - 1)
	}

	private fun replace(block: ArrayList<String>, transform: (String) -> String) {
		for (index in block.indices) block[index] = transform(block[index])
	}

	private fun first(event: SidebarEvent, at: Int, lines: List<String>): String? {
		val matcher = event.matchers[at]
		for (line in lines) if (matcher.reset(line).matches()) return line
		return null
	}

	private fun allOf(event: SidebarEvent, range: IntRange, lines: List<String>, block: ArrayList<String>) {
		for (line in lines) {
			for (at in range) {
				if (!event.matchers[at].reset(line).matches()) continue
				block += line.removePrefix(" ")
				break
			}
		}
	}

	private fun followCounts(event: SidebarEvent): IntArray {
		val counts = IntArray(event.matchers.size)
		when (event) {
			SidebarEvent.DARK_AUCTION -> counts[2] = 1
			SidebarEvent.JACOB_CONTEST -> counts[0] = 3
			SidebarEvent.TRAPPER -> counts[1] = 1
			SidebarEvent.GALATEA -> {
				counts[2] = 2
				counts[3] = 2
			}

			else -> Unit
		}
		return counts
	}
}
