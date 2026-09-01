package io.github.dzkchen.dhen.data.pet

import java.util.regex.Matcher
import java.util.regex.Pattern

internal object PetLines {
	private val summonLine: Matcher = Pattern.compile("§aYou summoned your (?<pet>.*)§a!").matcher("")
	private val autopetLine: Matcher =
		Pattern.compile("§cAutopet §eequipped your §7\\[Lvl [^]]*] (?<pet>.*)§e! §a§lVIEW RULE").matcher("")
	private val despawnLine: Matcher = Pattern.compile("§aYou despawned your .*§a!").matcher("")
	private val petsMenuTitle: Matcher =
		Pattern.compile("(?:\\(\\d+/\\d+\\) )?Pets(?:: \".*\")?(?: \\(\\d+/\\d+\\))? ?").matcher("")
	private val levelPrefix: Matcher = Pattern.compile(".*?\\[Lvl [^]]*]").matcher("")
	private val loadoutLine: Matcher = Pattern.compile("\\[Lvl (\\d+)] (.+)$").matcher("")
	private val levelNumber: Matcher = Pattern.compile("\\[Lvl (\\d+)]").matcher("")

	fun summoned(styled: String): String? = petIn(summonLine, styled)

	fun autopetted(styled: String): String? = petIn(autopetLine, styled)

	fun despawned(styled: String): Boolean = despawnLine.reset(styled).matches()

	fun petsMenu(title: String): Boolean = petsMenuTitle.reset(title).matches()

	fun withoutLevel(hoverName: String): String = levelPrefix.reset(hoverName).replaceFirst("").trim()

	fun level(hoverName: String): Int =
		if (levelNumber.reset(hoverName).find()) {
			levelNumber.group(LEVEL_GROUP).toIntOrNull() ?: UNKNOWN_LEVEL
		} else {
			UNKNOWN_LEVEL
		}

	fun maxed(level: Int): Boolean = level == MAX_LEVEL || level == DRAGON_MAX_LEVEL

	fun maxed(level: Int, petType: String): Boolean =
		level == if (petType == DRAGON_TYPE) DRAGON_MAX_LEVEL else MAX_LEVEL

	fun loadoutPet(lore: String): String? =
		if (loadoutLine.reset(lore).find()) loadoutLine.group(LOADOUT_NAME_GROUP) else null

	private fun petIn(matcher: Matcher, styled: String): String? =
		if (matcher.reset(styled).find()) matcher.group(PET_GROUP) else null

	internal const val UNKNOWN_LEVEL = 0

	private const val PET_GROUP = "pet"
	private const val LOADOUT_NAME_GROUP = 2
	private const val LEVEL_GROUP = 1
	private const val MAX_LEVEL = 100
	private const val DRAGON_MAX_LEVEL = 200
	private const val DRAGON_TYPE = "GOLDEN_DRAGON"
}
