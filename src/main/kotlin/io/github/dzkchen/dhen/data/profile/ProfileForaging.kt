package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.texts
import java.util.Locale

private const val CLAIMED_MILESTONES = "milestone_tier_claimed"

enum class Whisper {
	FOREST, DESERT;

	internal val apiKey: String = name.lowercase(Locale.ROOT)
}

class ForagingDaily internal constructor(
	val treesCut: Int,
	val treesCutDay: Int,
	val logsCut: Set<String>,
	val logsCutDay: Int,
	val gifts: Int
)

class ForagingProfile internal constructor(
	val tree: SkillTree,
	val whispers: Map<Whisper, CurrencyReserve>,
	val daily: ForagingDaily,
	val personalBests: Map<String, Int>,
	val treeGifts: Map<String, Int>,
	val claimedGiftMilestones: Map<String, Int>,
	val fishFamily: Set<String>
)

internal object ForagingProfiles {
	private val FAMILY = SkillTreeFamily.HEART_OF_THE_FOREST

	fun of(member: JsonObject): ForagingProfile? {
		val tree = member.obj("skill_tree")
		val core = member.obj("foraging_core")
		val foraging = member.obj("foraging")
		if (core == null && foraging == null && !SkillTrees.mentions(tree, FAMILY)) return null
		val heart = SkillTrees.of(tree, FAMILY)
		val gifts = foraging?.obj("tree_gifts")
		return ForagingProfile(
			tree = heart,
			whispers = whispers(core?.obj("whispers"), heart),
			daily = daily(core),
			personalBests = foraging?.obj("starlyn")?.obj("personal_bests").numericInts(),
			treeGifts = gifts.numericInts(),
			claimedGiftMilestones = gifts?.obj(CLAIMED_MILESTONES).numericInts(),
			fishFamily = foraging?.array("fish_family").texts().toSet()
		)
	}

	private fun whispers(whispers: JsonObject?, tree: SkillTree): Map<Whisper, CurrencyReserve> =
		Whisper.entries.associateWith { whisper ->
			val purse = whispers?.obj(whisper.apiKey)
			tree.reserve(purse?.number("total")) { slot -> purse?.obj(slot.toString())?.number("spent") }
		}

	private fun daily(core: JsonObject?): ForagingDaily = ForagingDaily(
		treesCut = core.int("daily_trees_cut"),
		treesCutDay = core.int("daily_trees_cut_day"),
		logsCut = core?.array("daily_log_cut").texts().toSet(),
		logsCutDay = core.int("daily_log_cut_day"),
		gifts = core.int("daily_gifts")
	)
}
