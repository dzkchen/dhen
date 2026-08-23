package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.long
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
			visits = stats.int("visits"),
			lifetimeMotes = stats.int("lifetime_motes_earned"),
			secondsSitting = rift?.obj("village_plaza")?.obj("lonely").int("seconds_sitting"),
			grubberStacks = rift?.obj("castle").int("grubber_stacks"),
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
			timestamp = trophy.long("timestamp"),
			visits = trophy.int("visits")
		)
	} ?: emptyList()
}
