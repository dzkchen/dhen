package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileRiftTest {
	@Test
	fun `the rift's own section carries the sitting, the eyes, the souls and the grubber stacks`() {
		val rift = decode(MEMBER)

		assertEquals(1800, rift.secondsSitting)
		assertEquals(setOf("blue", "green"), rift.unlockedEyes)
		assertEquals(setOf("living_memory", "chicken_n_egg"), rift.foundSouls)
		assertEquals(3, rift.grubberStacks)
	}

	@Test
	fun `the cats found and the montezuma assembled from them come off the same section`() {
		val rift = decode(MEMBER)
		val montezuma = rift.montezuma!!

		assertEquals(setOf("BLACK", "CALICO"), rift.foundCats)
		assertEquals("MONTEZUMA", montezuma.type)
		assertEquals("LEGENDARY", montezuma.tier)
		assertEquals(25353230.0, montezuma.exp)
	}

	@Test
	fun `every secured trophy keeps the visit it was won on`() {
		val trophies = decode(MEMBER).trophies

		assertEquals(2, trophies.size)
		assertEquals("wyldly_supreme", trophies[0].type)
		assertEquals(1712000000000L, trophies[0].timestamp)
		assertEquals(4, trophies[0].visits)
	}

	@Test
	fun `a trophy with no type is dropped rather than carried as a blank row`() {
		val member = """{"rift":{"gallery":{"secured_trophies":[{"visits":2},{"type":"chicken_n_egg","visits":9}]}}}"""

		assertEquals(listOf("chicken_n_egg"), decode(member).trophies.map(RiftTrophy::type))
	}

	@Test
	fun `the visits and the lifetime motes come from the player stats, not the rift section`() {
		val rift = decode(MEMBER)

		assertEquals(41, rift.visits)
		assertEquals(96000, rift.lifetimeMotes)
	}

	@Test
	fun `a player whose stats mention the rift but whose rift section is missing keeps their motes`() {
		val rift = decode("""{"player_stats":{"rift":{"visits":2,"lifetime_motes_earned":300}}}""")

		assertEquals(2, rift.visits)
		assertEquals(300, rift.lifetimeMotes)
		assertNull(rift.montezuma)
		assertTrue(rift.foundSouls.isEmpty())
	}

	@Test
	fun `a rift section with no matching stats still decodes what it does carry`() {
		val rift = decode("""{"rift":{"castle":{"grubber_stacks":5}}}""")

		assertEquals(5, rift.grubberStacks)
		assertEquals(0, rift.visits)
	}

	@Test
	fun `a profile that has never been through the mirror decodes to nothing`() {
		assertNull(RiftProfiles.of(DataFixture.json("""{"player_stats":{"deaths":4}}""")))
	}

	@Test
	fun `a dead cat entry with no pet in it still reports the cats that were found`() {
		val rift = decode("""{"rift":{"dead_cats":{"found_cats":["BLACK"]}}}""")

		assertNull(rift.montezuma)
		assertEquals(setOf("BLACK"), rift.foundCats)
	}

	private fun decode(member: String): RiftProfile = RiftProfiles.of(DataFixture.json(member))!!

	private companion object {
		private val MEMBER = """{
			"rift": {
				"village_plaza": {"lonely": {"seconds_sitting": 1800}},
				"wither_cage": {"killed_eyes": ["blue", "green"]},
				"enigma": {"found_souls": ["living_memory", "chicken_n_egg"]},
				"castle": {"grubber_stacks": 3},
				"dead_cats": {
					"found_cats": ["BLACK", "CALICO"],
					"montezuma": {"type": "MONTEZUMA", "tier": "LEGENDARY", "exp": 25353230, "candyUsed": 0}
				},
				"gallery": {"secured_trophies": [
					{"type": "wyldly_supreme", "timestamp": 1712000000000, "visits": 4},
					{"type": "chicken_n_egg", "timestamp": 1713000000000, "visits": 9}
				]}
			},
			"player_stats": {"rift": {"visits": 41, "lifetime_motes_earned": 96000}}
		}"""
	}
}
