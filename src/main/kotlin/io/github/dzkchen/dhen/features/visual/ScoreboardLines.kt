package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.data.quiver.QuiverState
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.grouped

internal enum class ScoreboardLine(val label: String) {
	LOBBY_CODE("Lobby Code"),
	SEPARATOR_1("Separator 1"),
	DATE("Date"),
	TIME("Time"),
	ISLAND("Island"),
	LOCATION("Location"),
	SEPARATOR_2("Separator 2"),
	PURSE("Purse"),
	BITS("Bits"),
	SEPARATOR_3("Separator 3"),
	QUIVER("Quiver"),
	SEPARATOR_4("Separator 4"),
	SLAYER("Slayer"),
	PARTY("Party"),
	FOOTER("Footer"),
	EXTRA("Extra");

	companion object {
		val labels: List<String> = entries.map { it.label }

		private val byLabel: Map<String, ScoreboardLine> = entries.associateBy { it.label }

		fun of(label: String): ScoreboardLine? = byLabel[label]
	}
}

internal class ScoreboardComposer(private val clock: NanoClock = NanoClock.SYSTEM) {
	private val purse = NumberDiff(PURSE_COLOR)
	private val bits = NumberDiff(BITS_COLOR)
	private val composed = ArrayList<String>()

	fun sampled() {
		val now = clock.nanoTime()
		purse.sample(SidebarValues.purse, now)
		bits.sample(SidebarValues.bits, now)
	}

	fun faded(): Boolean {
		val now = clock.nanoTime()
		val purseFaded = purse.faded(now)
		val bitsFaded = bits.faded(now)
		return purseFaded || bitsFaded
	}

	fun compose(enabled: List<String>): List<String> {
		composed.clear()
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

		ScoreboardLine.LOBBY_CODE -> SkyBlockLocation.serverName?.let { composed += "$LOBBY_COLOR$it" } ?: Unit
		ScoreboardLine.DATE -> SidebarValues.date?.let { composed += it } ?: Unit
		ScoreboardLine.TIME -> SidebarValues.time?.let { composed += it } ?: Unit
		ScoreboardLine.ISLAND -> SkyBlockLocation.island.displayName?.let { composed += "$ISLAND_PREFIX$it" } ?: Unit
		ScoreboardLine.LOCATION -> SidebarValues.location?.let { composed += it } ?: Unit
		ScoreboardLine.PURSE -> purseLine()
		ScoreboardLine.BITS -> bitsLine()
		ScoreboardLine.QUIVER -> quiverLine()
		ScoreboardLine.SLAYER -> composed += SidebarValues.slayer
		ScoreboardLine.PARTY -> partyLines()
		ScoreboardLine.FOOTER -> SidebarValues.footer?.let { composed += it } ?: Unit
		ScoreboardLine.EXTRA -> extraLines()
	}

	private fun purseLine() {
		if (SidebarValues.purse == SidebarValues.ABSENT || inRift()) return
		composed += "${LABEL_COLOR}Purse: $PURSE_COLOR${SidebarValues.purseText}${purse.suffix}"
	}

	private fun bitsLine() {
		if (SidebarValues.bits == SidebarValues.ABSENT || inDungeon() || inKuudra()) return
		composed += "${LABEL_COLOR}Bits: $BITS_COLOR${SidebarValues.bitsText}${bits.suffix}"
	}

	private fun quiverLine() {
		if (inRift()) return
		val arrow = QuiverState.currentArrow ?: return
		if (arrow == QuiverArrow.NONE) return
		composed += "$LABEL_COLOR${arrow.displayName}: $LABEL_COLOR${grouped(QuiverState.currentAmount.toLong())}"
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

	private fun extraLines() {
		if (SidebarValues.unknown.isEmpty()) return
		composed += UNDETECTED_HEADING
		composed += SidebarValues.unknown
	}

	private fun inRift(): Boolean = SkyBlockLocation.island == Island.THE_RIFT

	private fun inDungeon(): Boolean = SkyBlockLocation.island == Island.CATACOMBS

	private fun inKuudra(): Boolean = SkyBlockLocation.island == Island.KUUDRA

	private class NumberDiff(private val color: String) {
		private var previous = SidebarValues.ABSENT
		private var until = 0L

		var suffix: String = ""
			private set

		fun sample(value: Long, now: Long) {
			if (value == SidebarValues.ABSENT) {
				previous = SidebarValues.ABSENT
				suffix = ""
				return
			}
			val change = if (previous == SidebarValues.ABSENT) 0L else value - previous
			previous = value
			if (change == 0L) return
			suffix = " $DIM_COLOR($color${if (change > 0) "+" else ""}${grouped(change)}$DIM_COLOR)"
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
		const val DIFF_NANOS = 5_000_000_000L
		const val LABEL_COLOR = "§f"
		const val DIM_COLOR = "§7"
		const val LOBBY_COLOR = "§8"
		const val PURSE_COLOR = "§6"
		const val BITS_COLOR = "§b"
		const val PARTY_COLOR = "§9§l"
		const val ISLAND_PREFIX = "§7㋖ §a"
		const val LEADER_MARK = " §e♚"
		const val UNDETECTED_HEADING = "§cUndetected Lines:"
	}
}
