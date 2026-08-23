package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj

private const val BARN_CAPACITY_BASE = 18
private const val BARN_CAPACITY_PER_LEVEL = 2

class TimeTower internal constructor(val charges: Int, val level: Int, val activationTime: Long)

class RabbitHitman internal constructor(val slots: Int, val uncollectedEggs: Int)

class ChocolateFactoryProfile internal constructor(
	val chocolate: Long,
	val totalChocolate: Long,
	val chocolateSincePrestige: Long,
	val prestigeLevel: Int,
	val employees: Map<String, Int>,
	val rabbits: Map<String, Int>,
	val barnCapacityLevel: Int,
	val clickUpgrades: Int,
	val chocolateMultiplierUpgrades: Int,
	val rabbitRarityUpgrades: Int,
	val timeTower: TimeTower?,
	val hitman: RabbitHitman?,
	val lastViewed: Long
) {
	val barnCapacity: Int = barnCapacityLevel * BARN_CAPACITY_PER_LEVEL + BARN_CAPACITY_BASE
}

internal object ChocolateFactoryProfiles {
	fun of(member: JsonObject): ChocolateFactoryProfile? {
		val easter = member.obj("events")?.obj("easter") ?: return null
		return ChocolateFactoryProfile(
			chocolate = easter.long("chocolate"),
			totalChocolate = easter.long("total_chocolate"),
			chocolateSincePrestige = easter.long("chocolate_since_prestige"),
			prestigeLevel = easter.int("chocolate_level"),
			employees = easter.obj("employees").ints(),
			rabbits = easter.obj("rabbits").numericInts(),
			barnCapacityLevel = easter.int("rabbit_barn_capacity_level"),
			clickUpgrades = easter.int("click_upgrades"),
			chocolateMultiplierUpgrades = easter.int("chocolate_multiplier_upgrades"),
			rabbitRarityUpgrades = easter.int("rabbit_rarity_upgrades"),
			timeTower = timeTower(easter.obj("time_tower")),
			hitman = hitman(easter.obj("rabbit_hitmen")),
			lastViewed = easter.long("last_viewed_chocolate_factory")
		)
	}

	private fun timeTower(tower: JsonObject?): TimeTower? = tower?.let {
		TimeTower(
			charges = it.int("charges"),
			level = it.int("level"),
			activationTime = it.long("activation_time")
		)
	}

	private fun hitman(hitmen: JsonObject?): RabbitHitman? = hitmen?.let {
		RabbitHitman(
			slots = it.int("rabbit_hitmen_slots"),
			uncollectedEggs = it.int("missed_uncollected_eggs")
		)
	}
}
