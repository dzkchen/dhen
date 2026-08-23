package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileChocolateFactoryTest {
	@Test
	fun `the chocolate on hand, the lifetime total and the run since prestige are three different numbers`() {
		val factory = decode(MEMBER)

		assertEquals(4200000L, factory.chocolate)
		assertEquals(91000000000L, factory.totalChocolate)
		assertEquals(58000000L, factory.chocolateSincePrestige)
		assertEquals(5, factory.prestigeLevel)
	}

	@Test
	fun `every employee comes back at the level they were hired up to`() {
		assertEquals(mapOf("rabbit_bro" to 210, "rabbit_cousin" to 145), decode(MEMBER).employees)
	}

	@Test
	fun `a rabbit entry that is not a count is dropped rather than counted as zero`() {
		val rabbits = decode(MEMBER).rabbits

		assertEquals(mapOf("sigma" to 3, "cocoa" to 1), rabbits)
		assertNull(rabbits["collected_eggs"])
	}

	@Test
	fun `the barn holds eighteen rabbits before a single upgrade and two more for each one after`() {
		assertEquals(7, decode(MEMBER).barnCapacityLevel)
		assertEquals(32, decode(MEMBER).barnCapacity)
		assertEquals(18, decode("""{"events":{"easter":{}}}""").barnCapacity)
	}

	@Test
	fun `the three upgrade counters stay apart`() {
		val factory = decode(MEMBER)

		assertEquals(88, factory.clickUpgrades)
		assertEquals(12, factory.chocolateMultiplierUpgrades)
		assertEquals(6, factory.rabbitRarityUpgrades)
	}

	@Test
	fun `the time tower and the hitmen come back with their own numbers`() {
		val factory = decode(MEMBER)
		val tower = factory.timeTower!!
		val hitman = factory.hitman!!

		assertEquals(2, tower.charges)
		assertEquals(9, tower.level)
		assertEquals(1712000000000L, tower.activationTime)
		assertEquals(28, hitman.slots)
		assertEquals(140, hitman.uncollectedEggs)
	}

	@Test
	fun `the last time the factory was opened is kept, so a stale total can be recognised`() {
		assertEquals(1713000000000L, decode(MEMBER).lastViewed)
	}

	@Test
	fun `a factory that has bought neither the tower nor the hitmen reports neither`() {
		val factory = decode("""{"events":{"easter":{"chocolate":40}}}""")

		assertEquals(40L, factory.chocolate)
		assertNull(factory.timeTower)
		assertNull(factory.hitman)
	}

	@Test
	fun `a profile that never opened the factory decodes to nothing`() {
		assertNull(ChocolateFactoryProfiles.of(DataFixture.json("""{"events":{"anniversary":{}}}""")))
	}

	private fun decode(member: String): ChocolateFactoryProfile =
		ChocolateFactoryProfiles.of(DataFixture.json(member))!!

	private companion object {
		private const val MEMBER = """{
			"events": {
				"easter": {
					"chocolate": 4200000,
					"total_chocolate": 91000000000,
					"chocolate_since_prestige": 58000000,
					"chocolate_level": 5,
					"employees": {"rabbit_bro": 210, "rabbit_cousin": 145},
					"rabbits": {"sigma": 3, "cocoa": 1, "collected_eggs": {"breakfast": 2}},
					"rabbit_barn_capacity_level": 7,
					"click_upgrades": 88,
					"chocolate_multiplier_upgrades": 12,
					"rabbit_rarity_upgrades": 6,
					"time_tower": {"charges": 2, "level": 9, "activation_time": 1712000000000},
					"rabbit_hitmen": {"rabbit_hitmen_slots": 28, "missed_uncollected_eggs": 140},
					"last_viewed_chocolate_factory": 1713000000000
				}
			}
		}"""
	}
}
