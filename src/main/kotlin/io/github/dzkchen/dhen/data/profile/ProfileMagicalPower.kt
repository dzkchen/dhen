package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ApiInventory
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import net.minecraft.world.item.ItemStack

internal object ProfileMagicalPower {
	private const val HEGEMONY = "HEGEMONY_ARTIFACT"
	private const val ABIPHONE = "ABICASE_"
	private const val HAT_FAMILY = "PARTY_HAT"
	private const val PRISM_POWER = 11
	private const val POWER_PER_TUNING = 10
	private const val BALLOON_HAT_FAMILY = "BALLOON_HAT"
	private val unusableLine = Regex("^[^A-Za-z]*Requires .+\\.$")

	fun of(member: JsonObject, abiphoneContacts: Int, consumedRiftPrism: Boolean): Int? {
		val stacks = ApiInventory.stacks(talismanBag(member)) ?: return null
		val strongest = HashMap<String, Int>()
		for (stack in stacks) {
			val data = SkyBlockItems.customData(stack) ?: continue
			if (SkyBlockItems.lore(stack).any { unusableLine.matches(withoutCodes(it.string)) }) continue
			val item = SkyBlockItem.parse(data)
			val family = if (item.id.startsWith(HAT_FAMILY) || item.id.startsWith(BALLOON_HAT_FAMILY)) HAT_FAMILY else item.id
			val power = powerOf(item, stack, abiphoneContacts)
			if (power > (strongest[family] ?: 0)) strongest[family] = power
		}
		return strongest.values.sum() + if (consumedRiftPrism) PRISM_POWER else 0
	}

	fun assumed(tunings: Map<String, Int>, power: Int?): Int =
		if (power != null && power != 0) power else tunings.values.sum() * POWER_PER_TUNING

	private fun talismanBag(member: JsonObject): String? =
		member.obj("inventory")?.obj("bag_contents")?.obj("talisman_bag")?.text("data")

	private fun powerOf(item: SkyBlockItem, stack: ItemStack, abiphoneContacts: Int): Int {
		val rarity = item.rarity(stack).magicalPower
		return when {
			item.id == HEGEMONY -> rarity * 2
			item.id.startsWith(ABIPHONE) -> rarity + abiphoneContacts / 2
			else -> rarity
		}
	}
}
