package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.texts

class RiftTrophy internal constructor(val type: String, val timestamp: Long, val visits: Int)

class RiftProfile internal constructor(
	val visits: Int,
	val lifetimeMotes: Int,
	val secondsSitting: Int,
	val grubberStacks: Int,
	val unlockedEyes: Set<String>,
	val foundSouls: Set<String>,
	val foundCats: Set<String>,
	val montezuma: PetInfo?,
	val trophies: List<RiftTrophy>
)

internal object RiftProfiles {
	fun of(member: JsonObject): RiftProfile? {
		val rift = member.obj("rift")
		val stats = member.obj("player_stats")?.obj("rift")
		if (rift == null && stats == null) return null
		val deadCats = rift?.obj("dead_cats")
		return RiftProfile(
			visits = stats?.number("visits")?.toInt() ?: 0,
			lifetimeMotes = stats?.number("lifetime_motes_earned")?.toInt() ?: 0,
			secondsSitting = rift?.obj("village_plaza")?.obj("lonely")?.number("seconds_sitting")?.toInt() ?: 0,
			grubberStacks = rift?.obj("castle")?.number("grubber_stacks")?.toInt() ?: 0,
			unlockedEyes = rift?.obj("wither_cage")?.array("killed_eyes").texts().toSet(),
			foundSouls = rift?.obj("enigma")?.array("found_souls").texts().toSet(),
			foundCats = deadCats?.array("found_cats").texts().toSet(),
			montezuma = deadCats?.obj("montezuma")?.let(SkyBlockItem.Companion::petInfoOf),
			trophies = trophies(rift?.obj("gallery")?.array("secured_trophies"))
		)
	}

	private fun trophies(secured: JsonArray?): List<RiftTrophy> = secured?.mapNotNull { entry ->
		val trophy = entry as? JsonObject ?: return@mapNotNull null
		val type = trophy.text("type") ?: return@mapNotNull null
		RiftTrophy(
			type = type,
			timestamp = trophy.number("timestamp")?.toLong() ?: 0L,
			visits = trophy.number("visits")?.toInt() ?: 0
		)
	} ?: emptyList()
}
