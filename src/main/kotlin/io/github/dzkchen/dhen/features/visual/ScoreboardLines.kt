package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.PowderKind
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SidebarEvent
import io.github.dzkchen.dhen.data.SidebarEvents
import io.github.dzkchen.dhen.data.SidebarField
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.cookie.CookieState
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.maxwell.MaxwellState
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.data.quiver.QuiverState
import io.github.dzkchen.dhen.util.EpochClock
import io.github.dzkchen.dhen.util.NO_DIGITS
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.countdown
import io.github.dzkchen.dhen.util.digits
import io.github.dzkchen.dhen.util.formatted

internal enum class ScoreboardLine(val label: String) {
	LOBBY_CODE("Lobby Code"),
	SEPARATOR_1("Separator 1"),
	DATE("Date"),
	TIME("Time"),
	ISLAND("Island"),
	PLAYER_COUNT("Player Count"),
	LOCATION("Location"),
	VISITING("Visiting"),
	PROFILE("Profile"),
	SEPARATOR_2("Separator 2"),
	PURSE("Purse"),
	MOTES("Motes"),
	BANK("Bank"),
	BITS("Bits"),
	COPPER("Copper"),
	SOWDUST("Sowdust"),
	GEMS("Gems"),
	HEAT("Heat"),
	COLD("Cold"),
	NORTH_STARS("North Stars"),
	SOULFLOW("Soulflow"),
	SEPARATOR_3("Separator 3"),
	EVENTS("Events"),
	COOKIE("Cookie Buff"),
	QUIVER("Quiver"),
	POWER("Power"),
	TUNING("Tuning"),
	SEPARATOR_4("Separator 4"),
	OBJECTIVE("Objective"),
	SLAYER("Slayer"),
	POWDER("Powder"),
	MAYOR("Mayor"),
	PARTY("Party"),
	FOOTER("Footer"),
	EXTRA("Extra"),
	SKYBLOCK_XP("SkyBlock XP");

	companion object {
		val labels: List<String> = entries.map { it.label }

		private val byLabel: Map<String, ScoreboardLine> = entries.associateBy { it.label }

		fun of(label: String): ScoreboardLine? = byLabel[label]
	}
}

internal class ScoreboardComposer(
	private val clock: NanoClock = NanoClock.SYSTEM,
	private val epoch: EpochClock = EpochClock.SYSTEM
) {
	private val purse = NumberDiff(PURSE_COLOR) { SidebarValues.number(SidebarField.PURSE) }
	private val bits = NumberDiff(BITS_COLOR) { SidebarValues.number(SidebarField.BITS) }
	private val motes = NumberDiff(MOTES_COLOR) { SidebarValues.number(SidebarField.MOTES) }
	private val copper = NumberDiff(COPPER_COLOR) { digits(copperText()) }
	private val sowdust = NumberDiff(SOWDUST_COLOR) { digits(sowdustText()) }
	private val soulflow = NumberDiff(SOULFLOW_COLOR) { digits(soulflowText()) }
	private val trackers = arrayOf(purse, bits, motes, copper, sowdust, soulflow)
	private val composed = ArrayList<String>()
	private var countdownShown = false
	private var profileType = NORMAL_PROFILE
	private var lastSecond = -1L
	private var enabledEvents: List<String> = emptyList()
	private var allActiveEvents = true

	fun sampled() {
		val now = clock.nanoTime()
		for (tracker in trackers) tracker.sample(now)
	}

	fun faded(): Boolean {
		val now = clock.nanoTime()
		var faded = false
		for (tracker in trackers) if (tracker.faded(now)) faded = true
		return faded
	}

	fun ticked(): Boolean {
		if (!countdownShown) return false
		val second = epoch.epochMillis() / MILLIS_PER_SECOND
		if (second == lastSecond) return false
		lastSecond = second
		return true
	}

	fun compose(enabled: List<String>, events: List<String>, allActive: Boolean): List<String> {
		composed.clear()
		countdownShown = false
		enabledEvents = events
		allActiveEvents = allActive
		if (!SkyBlockLocation.inSkyBlock) {
			composed += ScoreboardState.lines
			return composed
		}
		for (label in enabled) {
			val line = ScoreboardLine.of(label) ?: continue
			val before = composed.size
			append(line)
			if (opensOnAnEmptyLine(before)) while (composed.size > before) composed.removeAt(composed.size - 1)
		}
		while (composed.isNotEmpty() && composed.first().isBlank()) composed.removeAt(0)
		while (composed.isNotEmpty() && composed.last().isBlank()) composed.removeAt(composed.size - 1)
		return composed
	}

	private fun opensOnAnEmptyLine(before: Int): Boolean =
		composed.size > before && before > 0 && composed[before].isEmpty() && composed[before - 1].isEmpty()

	private fun append(line: ScoreboardLine) = when (line) {
		ScoreboardLine.SEPARATOR_1,
		ScoreboardLine.SEPARATOR_2,
		ScoreboardLine.SEPARATOR_3,
		ScoreboardLine.SEPARATOR_4 -> composed += ""

		ScoreboardLine.LOBBY_CODE -> lobbyCodeLine()
		ScoreboardLine.DATE -> SidebarValues.text(SidebarField.DATE)?.let { composed += it } ?: Unit
		ScoreboardLine.TIME -> SidebarValues.text(SidebarField.TIME)?.let { composed += it } ?: Unit
		ScoreboardLine.ISLAND -> SkyBlockLocation.island.displayName?.let { composed += "$ISLAND_PREFIX$it" } ?: Unit
		ScoreboardLine.PLAYER_COUNT -> playerCountLine()
		ScoreboardLine.LOCATION -> SidebarValues.text(SidebarField.LOCATION)?.let { composed += it } ?: Unit
		ScoreboardLine.VISITING -> visitingLine()
		ScoreboardLine.PROFILE -> profileLine()
		ScoreboardLine.PURSE -> purseLine()
		ScoreboardLine.MOTES -> motesLine()
		ScoreboardLine.BANK -> bankLine()
		ScoreboardLine.BITS -> bitsLine()
		ScoreboardLine.COPPER -> gardenNumber("Copper", copperText(), COPPER_COLOR, copper)
		ScoreboardLine.SOWDUST -> gardenNumber("Sowdust", sowdustText(), SOWDUST_COLOR, sowdust)
		ScoreboardLine.GEMS -> gemsLine()
		ScoreboardLine.HEAT -> heatLine()
		ScoreboardLine.COLD -> coldLine()
		ScoreboardLine.NORTH_STARS -> northStarsLine()
		ScoreboardLine.SOULFLOW -> soulflowLine()
		ScoreboardLine.EVENTS -> eventLines()
		ScoreboardLine.COOKIE -> cookieLine()
		ScoreboardLine.QUIVER -> quiverLine()
		ScoreboardLine.POWER -> powerLine()
		ScoreboardLine.TUNING -> tuningLines()
		ScoreboardLine.OBJECTIVE -> composed += SidebarValues.objective
		ScoreboardLine.SLAYER -> composed += SidebarValues.slayer
		ScoreboardLine.POWDER -> powderLines()
		ScoreboardLine.MAYOR -> mayorLines()
		ScoreboardLine.PARTY -> partyLines()
		ScoreboardLine.FOOTER -> SidebarValues.text(SidebarField.FOOTER)?.let { composed += it } ?: Unit
		ScoreboardLine.EXTRA -> extraLines()
		ScoreboardLine.SKYBLOCK_XP -> skyBlockLevelLines()
	}

	private fun lobbyCodeLine() {
		val date = SidebarValues.text(SidebarField.LOBBY_CODE)
		val server = SkyBlockLocation.serverName
		if (date == null) return server?.let { composed += "$LOBBY_COLOR$it" } ?: Unit
		composed += if (server == null) "$DIM_COLOR$date" else "$DIM_COLOR$date $LOBBY_COLOR$server"
	}

	private fun playerCountLine() {
		val players = widgetAmount(TabWidget.PLAYER_LIST) + widgetAmount(TabWidget.GUESTS) +
			if (inDungeon()) widgetAmount(TabWidget.DUNGEON_PARTY) else 0
		if (players <= 0) return
		val visible = SidebarValues.maxVisitors
		val most = if (visible != NO_DIGITS) visible
		else if (SkyBlockLocation.serverName?.startsWith(MEGA_SERVER) == true) MEGA_PLAYERS else ISLAND_PLAYERS
		composed += "${LABEL_COLOR}Players: $PLAYERS_COLOR$players$DIM_COLOR/$PLAYERS_COLOR$most"
	}

	private fun visitingLine() {
		if (!onPersonalIsland()) return
		SidebarValues.text(SidebarField.VISITING)?.let { composed += it }
	}

	private fun profileLine() {
		if (!SkyBlockLocation.isGuest) profileType = readProfileType()
		composed += profileType
	}

	private fun readProfileType(): String {
		val marker = SidebarValues.text(SidebarField.PROFILE_TYPE) ?: ""
		val title = ScoreboardState.strippedTitle
		return when {
			marker.contains(IRONMAN_MARK) || title.contains(IRONMAN_MARK) -> "$DIM_COLOR$IRONMAN_MARK Ironman"
			marker.contains(STRANDED_MARK) || title.contains(STRANDED_MARK) -> "§a$STRANDED_MARK Stranded"
			marker.contains(BINGO_MARK) || title.contains(BINGO_MARK) -> "§e❤ Bingo"
			else -> NORMAL_PROFILE
		}
	}

	private fun purseLine() {
		val text = SidebarValues.text(SidebarField.PURSE) ?: return
		if (inRift()) return
		composed += "${LABEL_COLOR}Purse: $PURSE_COLOR$text${purse.suffix}"
	}

	private fun motesLine() {
		val text = SidebarValues.text(SidebarField.MOTES) ?: return
		if (!inRift()) return
		composed += "${LABEL_COLOR}Motes: $MOTES_COLOR$text${motes.suffix}"
	}

	private fun bankLine() {
		if (inRift()) return
		val amount = TabWidgetState.capture(TabWidget.BANK, "amount") ?: return
		val personal = TabWidgetState.capture(TabWidget.BANK, "personal")
		val shared = if (personal == null) "" else " $DIM_COLOR/ $PURSE_COLOR$personal"
		composed += "${LABEL_COLOR}Bank: $PURSE_COLOR$amount$shared"
	}

	private fun bitsLine() {
		val text = SidebarValues.text(SidebarField.BITS) ?: return
		if (inDungeon() || inKuudra()) return
		composed += "${LABEL_COLOR}Bits: $BITS_COLOR$text${bits.suffix}"
	}

	private fun gardenNumber(label: String, text: String?, color: String, diff: NumberDiff) {
		if (text == null || !inGarden()) return
		composed += "$LABEL_COLOR$label: $color$text${diff.suffix}"
	}

	private fun gemsLine() {
		if (inRift() || inDungeon() || inKuudra()) return
		val text = SidebarValues.text(SidebarField.GEMS) ?: TabWidgetState.capture(TabWidget.GEMS, "gems") ?: return
		composed += "${LABEL_COLOR}Gems: $GEMS_COLOR$text"
	}

	private fun heatLine() {
		val text = SidebarValues.text(SidebarField.HEAT) ?: return
		if (!inCrystalHollows()) return
		composed += "${LABEL_COLOR}Heat: $text"
	}

	private fun coldLine() {
		val cold = SidebarValues.number(SidebarField.COLD)
		if (cold == NO_DIGITS || !inColdArea()) return
		composed += "${LABEL_COLOR}Cold: $COLD_COLOR-$cold❄"
	}

	private fun northStarsLine() {
		val text = SidebarValues.text(SidebarField.NORTH_STARS) ?: return
		if (!inWorkshop()) return
		composed += "${LABEL_COLOR}North Stars: $MOTES_COLOR$text"
	}

	private fun soulflowLine() {
		if (inRift()) return
		val text = soulflowText() ?: return
		composed += "${LABEL_COLOR}Soulflow: $SOULFLOW_COLOR$text${soulflow.suffix}"
	}

	private fun eventLines() {
		for (label in enabledEvents) {
			val event = SidebarEvent.of(label) ?: continue
			if (!eventShown(event)) continue
			val lines = SidebarEvents.lines(event)
			if (lines.isEmpty()) continue
			composed += lines
			if (!allActiveEvents) return
		}
	}

	private fun eventShown(event: SidebarEvent): Boolean = when (event) {
		SidebarEvent.DOJO -> ScoreboardState.area in DOJO_AREAS
		SidebarEvent.MAGMA_BOSS -> ScoreboardState.area == MAGMA_CHAMBER
		else -> true
	}

	private fun cookieLine() {
		val expiry = CookieState.expiry
		val left = expiry - epoch.epochMillis()
		val running = expiry != CookieState.UNKNOWN && left > 0
		if (running) countdownShown = true
		composed += "${COOKIE_COLOR}Cookie Buff$LABEL_COLOR: " + when {
			expiry == CookieState.UNKNOWN -> "§cOpen SB Menu!"
			!running -> "§cNot Active"
			else -> countdown(left)
		}
	}

	private fun quiverLine() {
		if (inRift()) return
		val arrow = QuiverState.currentArrow ?: return
		if (arrow == QuiverArrow.NONE) return
		val amount = if (QuiverState.infiniteArrows) INFINITE else formatted(QuiverState.currentAmount.toLong())
		composed += "$LABEL_COLOR${arrow.displayName}: $LABEL_COLOR$amount"
	}

	private fun powerLine() {
		if (inRift()) return
		val power = MaxwellState.power
		if (power == null) {
			composed += NO_BAGS
			return
		}
		val magical = MaxwellState.magicalPower
		val total = if (magical == MaxwellState.ABSENT) ""
		else " $DIM_COLOR($PURSE_COLOR${formatted(magical.toLong())}$DIM_COLOR)"
		composed += "${LABEL_COLOR}Power: $POWER_COLOR$power$total"
	}

	private fun tuningLines() {
		if (inRift()) return
		val tunings = MaxwellState.tunings
		if (tunings == null) {
			composed += NO_MAXWELL
			return
		}
		if (tunings.isEmpty()) {
			composed += NO_TUNINGS
			return
		}
		composed += if (tunings.size == 1) "Tuning:" else "Tunings:"
		for (index in 0 until minOf(TUNING_LINES, tunings.size)) {
			val tuning = tunings[index]
			composed += "$DIM_COLOR- $LABEL_COLOR${tuning.name}: ${tuning.icon}${tuning.color}${tuning.amount}"
		}
	}

	private fun powderLines() {
		if (!inAdvancedMining()) return
		var opened = false
		for (kind in PowderKind.entries) {
			val amount = SidebarValues.powder(kind) ?: continue
			if (!opened) {
				composed += POWDER_HEADING
				opened = true
			}
			composed += "$DIM_COLOR- $LABEL_COLOR${kind.displayName}: ${kind.color}$amount"
		}
	}

	private fun mayorLines() {
		if (inRift()) return
		val mayor = MayorService.mayor ?: return
		val now = epoch.epochMillis()
		countdownShown = true
		composed += "${mayorColor(mayor.name)}${mayor.name}$DIM_COLOR (§e${countdown(MayorService.nextElectionAt - now)}$DIM_COLOR)"
		for (perk in mayor.perks) composed += "$DIM_COLOR- §e$perk"
		val minister = MayorService.minister
		if (minister != null) {
			composed += "${mayorColor(minister)}$minister"
			MayorService.ministerPerk?.let { composed += "$DIM_COLOR- §e$it" }
		}
		val extra = MayorService.perkpocalypsePerk ?: return
		composed += "$PURSE_COLOR$extra$DIM_COLOR (${PURSE_COLOR}${countdown(MayorService.perkpocalypseUntil - now)}$DIM_COLOR)"
	}

	private fun partyLines() {
		if (inDungeon()) return
		val members = PartyState.members
		if (!PartyState.inParty || members.isEmpty()) return
		composed += "${PARTY_COLOR}Party $LABEL_COLOR(${members.size})"
		val leader = PartyState.leader?.takeIf { it in members }
		if (leader != null) composed += "$DIM_COLOR- $LABEL_COLOR$leader$LEADER_MARK"
		var listed = 0
		for (name in members) {
			if (listed >= MAX_PARTY_MEMBERS) break
			if (name == leader) continue
			composed += "$DIM_COLOR- $LABEL_COLOR$name"
			listed++
		}
	}

	private fun skyBlockLevelLines() {
		val level = TabWidgetState.capture(TabWidget.SB_LEVEL, "level") ?: return
		val experience = TabWidgetState.capture(TabWidget.SB_LEVEL, "xp") ?: return
		val band = (level.toIntOrNull() ?: 0).coerceIn(0, TOP_LEVEL) / LEVEL_BAND
		composed += "${LABEL_COLOR}SB Level: ${LEVEL_COLORS[band]}$level"
		composed += "${LABEL_COLOR}XP: $BITS_COLOR$experience§3/$BITS_COLOR$MAX_LEVEL_XP"
	}

	private fun extraLines() {
		if (SidebarValues.unknown.isEmpty()) return
		composed += UNDETECTED_HEADING
		composed += SidebarValues.unknown
	}

	private fun copperText(): String? =
		SidebarValues.text(SidebarField.COPPER) ?: TabWidgetState.capture(TabWidget.COPPER, "copper")

	private fun sowdustText(): String? =
		SidebarValues.text(SidebarField.SOWDUST) ?: TabWidgetState.capture(TabWidget.SOWDUST, "sowdust")

	private fun soulflowText(): String? = TabWidgetState.capture(TabWidget.SOULFLOW, "amount")

	private fun widgetAmount(widget: TabWidget): Long =
		digits(TabWidgetState.capture(widget, "amount")).coerceAtLeast(0L)

	private fun mayorColor(name: String): String = when (name) {
		"Aatrox" -> "§3"
		"Cole" -> "§e"
		"Diana" -> "§2"
		"Diaz" -> PURSE_COLOR
		"Marina", "Aura" -> BITS_COLOR
		"Foxy", "Scorpius", "Jerry", "Derpy" -> MOTES_COLOR
		else -> "§c"
	}

	private fun island(): Island = SkyBlockLocation.island

	private fun inRift(): Boolean = island() == Island.THE_RIFT

	private fun inDungeon(): Boolean = island() == Island.CATACOMBS

	private fun inKuudra(): Boolean = island() == Island.KUUDRA

	private fun inGarden(): Boolean = island().gardenIsland

	private fun onPersonalIsland(): Boolean = island().personalIsland

	private fun inCrystalHollows(): Boolean = island() == Island.CRYSTAL_HOLLOWS

	private fun inWorkshop(): Boolean = island() == Island.JERRYS_WORKSHOP

	private fun inAdvancedMining(): Boolean = island().advancedMining

	private fun inColdArea(): Boolean = when (island()) {
		Island.DWARVEN_MINES, Island.MINESHAFT -> true
		Island.CRITTER_SAFARI -> ScoreboardState.area == ICY_BIOME
		else -> false
	}

	private class NumberDiff(private val color: String, private val value: () -> Long) {
		private var previous = NO_DIGITS
		private var until = 0L

		var suffix: String = ""
			private set

		fun sample(now: Long) {
			val value = value()
			if (value == NO_DIGITS) {
				previous = NO_DIGITS
				suffix = ""
				return
			}
			val change = if (previous == NO_DIGITS) 0L else value - previous
			previous = value
			if (change == 0L) return
			suffix = " $DIM_COLOR($color${if (change > 0) "+" else ""}${formatted(change)}$DIM_COLOR)"
			until = now + DIFF_NANOS
		}

		fun faded(now: Long): Boolean {
			if (suffix.isEmpty() || now < until) return false
			suffix = ""
			return true
		}
	}

	private companion object {
		const val MAX_PARTY_MEMBERS = 4
		const val TUNING_LINES = 2
		const val LEVEL_BAND = 40
		const val TOP_LEVEL = 480
		const val MAX_LEVEL_XP = "100"
		const val MEGA_PLAYERS = 80L
		const val ISLAND_PLAYERS = 24L
		const val MILLIS_PER_SECOND = 1_000L
		const val DIFF_NANOS = 5_000_000_000L
		const val MEGA_SERVER = "mega"
		const val ICY_BIOME = "Icy Biome"
		const val MAGMA_CHAMBER = "Magma Chamber"
		const val INFINITE = "∞"
		const val IRONMAN_MARK = "♲"
		const val STRANDED_MARK = "☀"
		const val BINGO_MARK = "Ⓑ"
		const val LABEL_COLOR = "§f"
		const val DIM_COLOR = "§7"
		const val LOBBY_COLOR = "§8"
		const val PURSE_COLOR = "§6"
		const val BITS_COLOR = "§b"
		const val MOTES_COLOR = "§d"
		const val COPPER_COLOR = "§c"
		const val SOWDUST_COLOR = "§2"
		const val GEMS_COLOR = "§a"
		const val COLD_COLOR = "§b"
		const val POWER_COLOR = "§a"
		const val SOULFLOW_COLOR = "§3"
		const val COOKIE_COLOR = "§d"
		const val PLAYERS_COLOR = "§a"
		const val PARTY_COLOR = "§9§l"
		const val ISLAND_PREFIX = "§7㋖ §a"
		const val LEADER_MARK = " §e♚"
		const val POWDER_HEADING = "§9§lPowder"
		const val NORMAL_PROFILE = "§eNormal"
		const val UNDETECTED_HEADING = "§cUndetected Lines:"
		const val NO_BAGS = "§cOpen \"Your Bags\"!"
		const val NO_MAXWELL = "§cTalk to \"Maxwell\"!"
		const val NO_TUNINGS = "§cNo Maxwell Tunings :("
		val DOJO_AREAS = arrayOf("Dojo", "Dojo Arena")
		val LEVEL_COLORS = arrayOf(
			"§7", "§f", "§e", "§a", "§2", "§b", "§3", "§9", "§d", "§5", "§6", "§c", "§4"
		)
	}
}
