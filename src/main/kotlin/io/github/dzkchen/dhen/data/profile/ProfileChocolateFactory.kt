package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.number
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
			chocolate = easter.number("chocolate")?.toLong() ?: 0L,
			totalChocolate = easter.number("total_chocolate")?.toLong() ?: 0L,
			chocolateSincePrestige = easter.number("chocolate_since_prestige")?.toLong() ?: 0L,
			prestigeLevel = easter.number("chocolate_level")?.toInt() ?: 0,
			employees = easter.obj("employees").ints(),
			rabbits = easter.obj("rabbits").numericInts(),
			barnCapacityLevel = easter.number("rabbit_barn_capacity_level")?.toInt() ?: 0,
			clickUpgrades = easter.number("click_upgrades")?.toInt() ?: 0,
			chocolateMultiplierUpgrades = easter.number("chocolate_multiplier_upgrades")?.toInt() ?: 0,
			rabbitRarityUpgrades = easter.number("rabbit_rarity_upgrades")?.toInt() ?: 0,
			timeTower = timeTower(easter.obj("time_tower")),
			hitman = hitman(easter.obj("rabbit_hitmen")),
			lastViewed = easter.number("last_viewed_chocolate_factory")?.toLong() ?: 0L
		)
	}

	private fun timeTower(tower: JsonObject?): TimeTower? = tower?.let {
		TimeTower(
			charges = it.number("charges")?.toInt() ?: 0,
			level = it.number("level")?.toInt() ?: 0,
			activationTime = it.number("activation_time")?.toLong() ?: 0L
		)
	}

	private fun hitman(hitmen: JsonObject?): RabbitHitman? = hitmen?.let {
		RabbitHitman(
			slots = it.number("rabbit_hitmen_slots")?.toInt() ?: 0,
			uncollectedEggs = it.number("missed_uncollected_eggs")?.toInt() ?: 0
		)
	}
}
