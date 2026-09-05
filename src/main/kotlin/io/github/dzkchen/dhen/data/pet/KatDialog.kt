package io.github.dzkchen.dhen.data.pet

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class KatUpgrade(val pet: String, val rarity: String)

internal enum class KatSpecial { FLOWER, BOUQUET, RESET }

internal object KatDialog {
	const val FLOWER_REDUCTION_MS = 86_400_000L
	const val BOUQUET_REDUCTION_MS = 432_000_000L

	private const val KAT = "Kat"
	private const val NAME_GROUP = 1
	private const val DIALOG_GROUP = 2
	private const val PET_GROUP = "pet"
	private const val RARITY_GROUP = "rarity"
	private const val DURATION_GROUP = "duration"
	private const val AMOUNT_GROUP = 1
	private const val UNIT_GROUP = 2
	private const val MILLIS = 1_000L

	private const val FLOWER_LINE = "A flower? For me? How sweet!"
	private const val BOUQUET_LINE = "A bouquet? For me? How sweet!"
	private const val RESET_LINE = "If you have any other pets you'd like to upgrade, you know where to find me!"

	private val npcLine: Matcher = Pattern.compile("^\\[NPC]\\s+([^:]+):\\s*(.+)$").matcher("")
	private val upgradeLine: Matcher = Pattern.compile(
		"^I(?:['’])ll get your (?<pet>.+) upgraded to (?<rarity>[A-Za-z]+) in no time[!.]?$"
	).matcher("")
	private val remindLine: Matcher = Pattern.compile(
		"^I(?:['’])ll remind you when your (?<pet>.+) is done[!.]?$",
		Pattern.CASE_INSENSITIVE
	).matcher("")
	private val comeBackLine: Matcher = Pattern.compile(
		"^Come back in (?<duration>.+) to pick it up[!.]?$",
		Pattern.CASE_INSENSITIVE
	).matcher("")
	private val remindInLine: Matcher = Pattern.compile(
		"^I(?:['’])ll remind you in (?<duration>.+)[!.]?$",
		Pattern.CASE_INSENSITIVE
	).matcher("")
	private val durationPart: Matcher = Pattern.compile(
		"(\\d+)\\s*(day|days|hour|hours|houre|houres|minute|minutes|second|seconds)",
		Pattern.CASE_INSENSITIVE
	).matcher("")

	fun spokenByKat(stripped: String): String? {
		if (!npcLine.reset(stripped).matches()) return null
		if (npcLine.group(NAME_GROUP).trim() != KAT) return null
		return npcLine.group(DIALOG_GROUP).trim()
	}

	fun upgradeStart(dialog: String): KatUpgrade? {
		if (!upgradeLine.reset(dialog).matches()) return null
		val rarity = rarity(upgradeLine.group(RARITY_GROUP)) ?: return null
		return KatUpgrade(upgradeLine.group(PET_GROUP).trim(), rarity)
	}

	fun reminderStart(dialog: String): String? =
		if (remindLine.reset(dialog).matches()) remindLine.group(PET_GROUP).trim() else null

	fun duration(dialog: String): Long {
		val raw = when {
			comeBackLine.reset(dialog).matches() -> comeBackLine.group(DURATION_GROUP)
			remindInLine.reset(dialog).matches() -> remindInLine.group(DURATION_GROUP)
			else -> return 0L
		}
		return parseDuration(raw)
	}

	fun parseDuration(raw: String): Long {
		durationPart.reset(raw)
		var seconds = 0L
		var found = false
		while (durationPart.find()) {
			val amount = durationPart.group(AMOUNT_GROUP).toLongOrNull() ?: continue
			if (amount <= 0L) continue
			seconds += amount * unitSeconds(durationPart.group(UNIT_GROUP))
			found = true
		}
		return if (found) seconds * MILLIS else 0L
	}

	fun special(dialog: String): KatSpecial? = when (dialog) {
		FLOWER_LINE -> KatSpecial.FLOWER
		BOUQUET_LINE -> KatSpecial.BOUQUET
		RESET_LINE -> KatSpecial.RESET
		else -> null
	}

	fun rarity(raw: String): String? {
		val rarity = raw.uppercase(Locale.ROOT)
		return if (rarity in RARITIES) rarity else null
	}

	fun rarityCode(rarity: String): String = when (rarity) {
		"UNCOMMON" -> "§a"
		"RARE" -> "§9"
		"EPIC" -> "§5"
		"LEGENDARY" -> "§6"
		"MYTHIC" -> "§d"
		else -> "§f"
	}

	private fun unitSeconds(unit: String): Long = when (unit.lowercase(Locale.ROOT)) {
		"day", "days" -> 86_400L
		"hour", "hours", "houre", "houres" -> 3_600L
		"minute", "minutes" -> 60L
		else -> 1L
	}

	private val RARITIES = setOf("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
}
