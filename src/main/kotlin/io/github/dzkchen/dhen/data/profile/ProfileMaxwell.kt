package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.number
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
	fun of(member: JsonObject): MaxwellProfile? {
		val storage = member.obj("accessory_bag_storage") ?: return null
		return MaxwellProfile(
			tunings = MagicalPower.tuning(member).ints(),
			selectedPower = storage.text("selected_power"),
			highestMagicalPower = storage.number("highest_magical_power")?.toInt() ?: 0,
			bagUpgrades = storage.number("bag_upgrades_purchased")?.toInt() ?: 0,
			abiphoneContacts = MagicalPower.contacts(member),
			consumedRiftPrism = MagicalPower.consumedPrism(member)
		)
	}
}
