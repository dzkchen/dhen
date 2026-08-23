package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.profile.BagFixture.bag
import io.github.dzkchen.dhen.data.profile.BagFixture.member
import io.github.dzkchen.dhen.data.profile.BagFixture.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ProfileMagicalPowerTest {
	@Test
	fun `two copies of one accessory count once, at the higher rarity`() {
		assertEquals(22, power(member(bag(slot("TEST_TALISMAN", lore = listOf("§f§lCOMMON")), slot("TEST_TALISMAN", lore = listOf("§d§lMYTHIC"))))))
	}

	@Test
	fun `an accessory the player cannot use yet is worth nothing`() {
		val locked = slot("REQ_TALISMAN", lore = listOf("§a§lUNCOMMON", "§7§4☠ §cRequires §5Rift Level 5§c."))

		assertEquals(0, power(member(bag(locked))))
	}

	@Test
	fun `hegemony counts double and an abicase adds half the contact list`() {
		assertEquals(44, power(member(bag(slot("HEGEMONY_ARTIFACT", lore = listOf("§d§lMYTHIC"))))))
		assertEquals(7, power(member(bag(slot("ABICASE", lore = listOf("§a§lUNCOMMON"))), contacts = 5)))
	}

	@Test
	fun `every cosmetic hat collapses into one contribution`() {
		val hats = bag(slot("PARTY_HAT_CRAB_YELLOW", lore = listOf("§9§lRARE")), slot("BALLOON_HAT_2024", lore = listOf("§9§lRARE")))

		assertEquals(8, power(member(hats)))
	}

	@Test
	fun `a consumed rift prism adds eleven`() {
		assertEquals(14, power(member(bag(slot("TEST_TALISMAN", lore = listOf("§f§lCOMMON"))), prism = true)))
	}

	@Test
	fun `an unreadable talisman bag falls back to ten a tuning point, and a readable one does not`() {
		val unreadable = member("not base64 at all", prism = true, tuning = mapOf("health" to 5, "strength" to 3))
		assertNull(power(unreadable))
		assertEquals(80, assumedPower(unreadable))

		val readable = member(bag(slot("TEST_TALISMAN", lore = listOf("§a§lUNCOMMON"))), tuning = mapOf("health" to 5))
		assertEquals(5, power(readable))
		assertEquals(5, assumedPower(readable))
	}

	@Test
	fun `an empty talisman bag is nought magical power, not an unreadable one`() {
		val empty = member(bag(), tuning = mapOf("health" to 4))

		assertEquals(0, power(empty))
		assertEquals(40, assumedPower(empty))
	}

	private fun power(member: JsonObject): Int? =
		MagicalPower.of(member, CrimsonIsleProfiles.abiphoneContacts(member), RiftProfiles.consumedPrism(member))

	private fun assumedPower(member: JsonObject): Int =
		MagicalPower.assumed(MaxwellProfiles.tunings(member), power(member))

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
