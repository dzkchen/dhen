package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

private const val TOTAL_CAUGHT = "total_caught"

class TrophyCatch internal constructor(val type: String, val tier: String)

class ItemsFished internal constructor(
	val total: Int,
	val normal: Int,
	val treasure: Int,
	val largeTreasure: Int,
	val trophyFish: Int
)

class FishingProfile internal constructor(
	val itemsFished: ItemsFished,
	val treasuresCaught: Int,
	val festivalSharksKilled: Int,
	val trophyCounts: Map<String, Int>,
	val trophiesCaught: Int,
	val lastTrophy: TrophyCatch?,
	val trophyRewards: List<Int>
)

internal object FishingProfiles {
	fun of(member: JsonObject): FishingProfile? {
		val trophies = member.obj("trophy_fish")
		val fished = member.obj("player_stats")?.obj("items_fished")
		val treasures = member.obj("player_data")?.number("fishing_treasure_caught")
		val sharks = member.obj("leveling")?.number("fishing_festival_sharks_killed")
		if (trophies == null && fished == null && treasures == null && sharks == null) return null
		return FishingProfile(
			itemsFished = ItemsFished(
				total = fished?.number("total")?.toInt() ?: 0,
				normal = fished?.number("normal")?.toInt() ?: 0,
				treasure = fished?.number("treasure")?.toInt() ?: 0,
				largeTreasure = fished?.number("large_treasure")?.toInt() ?: 0,
				trophyFish = fished?.number("trophy_fish")?.toInt() ?: 0
			),
			treasuresCaught = treasures?.toInt() ?: 0,
			festivalSharksKilled = sharks?.toInt() ?: 0,
			trophyCounts = trophies.numericInts().filterKeys { it != TOTAL_CAUGHT },
			trophiesCaught = trophies?.number(TOTAL_CAUGHT)?.toInt() ?: 0,
			lastTrophy = lastTrophy(trophies?.text("last_caught")),
			trophyRewards = rewards(trophies?.array("rewards"))
		)
	}

	private fun lastTrophy(caught: String?): TrophyCatch? {
		val type = caught?.substringBefore("/", "")?.ifEmpty { null } ?: return null
		return TrophyCatch(type, caught.substringAfter("/"))
	}

	private fun rewards(rewards: JsonArray?): List<Int> =
		rewards?.mapNotNull { reward -> reward.numberOrNull()?.toInt()?.takeIf { it != 0 } } ?: emptyList()
}
