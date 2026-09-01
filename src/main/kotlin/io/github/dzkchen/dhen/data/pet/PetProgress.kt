package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.PetLevelProgress

internal object PetProgress {
	private const val TIER_BOOST = "PET_ITEM_TIER_BOOST"
	private val tiers = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

	fun of(info: PetInfo): PetLevelProgress =
		ItemRepo.constants.petProgress(info.type, info.tier, info.exp, curveTier(info))

	private fun curveTier(info: PetInfo): String {
		if (info.heldItem != TIER_BOOST) return info.tier
		val index = tiers.indexOf(info.tier)
		return if (index in 0 until tiers.lastIndex) tiers[index + 1] else info.tier
	}
}
