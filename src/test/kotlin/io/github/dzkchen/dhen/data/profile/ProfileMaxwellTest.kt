package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileMaxwellTest {
	@Test
	fun `the accessory bag carries the selected power, the highest reached and the upgrades bought`() {
		val maxwell = decode(MEMBER)

		assertEquals("fortuitous", maxwell.selectedPower)
		assertEquals(1234, maxwell.highestMagicalPower)
		assertEquals(5, maxwell.bagUpgrades)
	}

	@Test
	fun `the tuning points come back per stat, not summed away`() {
		assertEquals(mapOf("health" to 20, "attack_speed" to -10), decode(MEMBER).tunings)
	}

	@Test
	fun `the tuning points Maxwell reports are the ones magical power is assumed from`() {
		val member = DataFixture.json(MEMBER)

		assertEquals(10, MaxwellProfiles.of(member)!!.tunings.values.sum())
		assertEquals(100, ProfileMagicalPower.assumed(MaxwellProfiles.tunings(member), null))
	}

	@Test
	fun `the abiphone contacts and the rift prism are read once and reported here too`() {
		val maxwell = decode(MEMBER)

		assertEquals(3, maxwell.abiphoneContacts)
		assertTrue(maxwell.consumedRiftPrism)
	}

	@Test
	fun `a bag with nothing tuned still decodes, reporting no points rather than throwing`() {
		val maxwell = decode("""{"accessory_bag_storage":{"bag_upgrades_purchased":1}}""")

		assertTrue(maxwell.tunings.isEmpty())
		assertNull(maxwell.selectedPower)
		assertEquals(0, maxwell.highestMagicalPower)
		assertEquals(0, maxwell.abiphoneContacts)
		assertFalse(maxwell.consumedRiftPrism)
	}

	@Test
	fun `a player who has never opened the accessory bag decodes to nothing`() {
		assertNull(MaxwellProfiles.of(DataFixture.json("""{"rift":{"access":{"consumed_prism":true}}}""")))
	}

	private fun decode(member: String): MaxwellProfile = MaxwellProfiles.of(DataFixture.json(member))!!

	private companion object {
		private const val MEMBER = """{
			"accessory_bag_storage": {
				"tuning": {"slot_0": {"health": 20, "attack_speed": -10}},
				"selected_power": "fortuitous",
				"highest_magical_power": 1234,
				"bag_upgrades_purchased": 5
			},
			"nether_island_player_data": {"abiphone": {"active_contacts": [{}, {}, {}]}},
			"rift": {"access": {"consumed_prism": true}}
		}"""
	}
}
