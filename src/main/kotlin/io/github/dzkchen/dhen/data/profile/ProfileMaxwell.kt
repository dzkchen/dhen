package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

class MaxwellProfile internal constructor(
	val tunings: Map<String, Int>,
	val selectedPower: String?,
	val highestMagicalPower: Int,
	val bagUpgrades: Int,
	val abiphoneContacts: Int,
	val consumedRiftPrism: Boolean
)

internal object MaxwellProfiles {
	fun tunings(member: JsonObject): Map<String, Int> =
		member.obj("accessory_bag_storage")?.obj("tuning")?.obj("slot_0").ints()

	fun of(member: JsonObject): MaxwellProfile? {
		val storage = member.obj("accessory_bag_storage") ?: return null
		return MaxwellProfile(
			tunings = tunings(member),
			selectedPower = storage.text("selected_power"),
			highestMagicalPower = storage.int("highest_magical_power"),
			bagUpgrades = storage.int("bag_upgrades_purchased"),
			abiphoneContacts = CrimsonIsleProfiles.abiphoneContacts(member),
			consumedRiftPrism = RiftProfiles.consumedPrism(member)
		)
	}
}
