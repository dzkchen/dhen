package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.profile.BagFixture.bag
import io.github.dzkchen.dhen.data.profile.BagFixture.slot
import io.github.dzkchen.dhen.util.obj
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds

class ProfileSliceTest {
	@Test
	fun `a full profiles reply decodes into every field of the dungeon slice`() {
		val dungeons = ProfileSlices.of(UUID, fullReply())!!.dungeons!!

		assertEquals(1000.0, dungeons.catacombsExperience)
		assertEquals(6, dungeons.catacombsLevel)
		assertEquals(mapOf("healer" to 1, "mage" to 2), dungeons.classLevels)
		assertEquals(0.6, dungeons.classAverage)
		assertEquals("healer", dungeons.selectedClass)
		assertEquals(42L, dungeons.secrets)
		assertEquals(15, dungeons.bloodMobKills)
		assertEquals(setOf(0, 1, 2), dungeons.catacombs.keys)
		assertEquals(5, dungeons.catacombs.getValue(0).completions)
		assertEquals(1000.milliseconds, dungeons.catacombs.getValue(1).bestS)
		assertEquals(900.milliseconds, dungeons.catacombs.getValue(1).bestSPlus)
		assertNull(dungeons.catacombs.getValue(0).bestS)
		assertNull(dungeons.catacombs[9])
		assertEquals(1, dungeons.masterCatacombs.getValue(3).completions)
		assertEquals(1500.milliseconds, dungeons.masterCatacombs.getValue(3).bestS)
		assertEquals(1400.milliseconds, dungeons.masterCatacombs.getValue(3).bestSPlus)
	}

	@Test
	fun `the entrance floor does not count as a run and secrets per run survives a player with none`() {
		val dungeons = ProfileSlices.of(UUID, fullReply())!!.dungeons!!

		assertEquals(6, dungeons.runs)
		assertEquals(7.0, dungeons.secretsPerRun)
		assertEquals(0.0, DungeonSlice(0.0, 0, emptyMap(), 0.0, null, 9, 0, noFloors(), noFloors()).secretsPerRun)
	}

	@Test
	fun `catacombs experience becomes a level, and levels past the table are worth two hundred million each`() {
		assertEquals(0, CatacombsLevels.of(-1.0))
		assertEquals(0, CatacombsLevels.of(0.0))
		assertEquals(0, CatacombsLevels.of(49.0))
		assertEquals(1, CatacombsLevels.of(50.0))
		assertEquals(50, CatacombsLevels.of(569_809_640.0))
		assertEquals(51, CatacombsLevels.of(769_809_640.0))
		assertEquals(52, CatacombsLevels.of(969_809_640.0))
	}

	@Test
	fun `two copies of one accessory count once, at the higher rarity`() {
		assertEquals(22, MagicalPower.of(member(bag(slot("TEST_TALISMAN", lore = listOf("§f§lCOMMON")), slot("TEST_TALISMAN", lore = listOf("§d§lMYTHIC"))))))
	}

	@Test
	fun `an accessory the player cannot use yet is worth nothing`() {
		val locked = slot("REQ_TALISMAN", lore = listOf("§a§lUNCOMMON", "§7§4☠ §cRequires §5Rift Level 5§c."))

		assertEquals(0, MagicalPower.of(member(bag(locked))))
	}

	@Test
	fun `hegemony counts double and an abicase adds half the contact list`() {
		assertEquals(44, MagicalPower.of(member(bag(slot("HEGEMONY_ARTIFACT", lore = listOf("§d§lMYTHIC"))))))
		assertEquals(7, MagicalPower.of(member(bag(slot("ABICASE", lore = listOf("§a§lUNCOMMON"))), contacts = 5)))
	}

	@Test
	fun `every cosmetic hat collapses into one contribution`() {
		val hats = bag(slot("PARTY_HAT_CRAB_YELLOW", lore = listOf("§9§lRARE")), slot("BALLOON_HAT_2024", lore = listOf("§9§lRARE")))

		assertEquals(8, MagicalPower.of(member(hats)))
	}

	@Test
	fun `a consumed rift prism adds eleven`() {
		assertEquals(14, MagicalPower.of(member(bag(slot("TEST_TALISMAN", lore = listOf("§f§lCOMMON"))), prism = true)))
	}

	@Test
	fun `an unreadable talisman bag falls back to ten a tuning point, and a readable one does not`() {
		val unreadable = member("not base64 at all", prism = true, tuning = mapOf("health" to 5, "strength" to 3))
		assertNull(MagicalPower.of(unreadable))
		assertEquals(80, MagicalPower.assumed(unreadable, MagicalPower.of(unreadable)))

		val readable = member(bag(slot("TEST_TALISMAN", lore = listOf("§a§lUNCOMMON"))), tuning = mapOf("health" to 5))
		assertEquals(5, MagicalPower.of(readable))
		assertEquals(5, MagicalPower.assumed(readable, MagicalPower.of(readable)))
	}

	@Test
	fun `an empty talisman bag is nought magical power, not an unreadable one`() {
		val empty = member(bag(), tuning = mapOf("health" to 4))

		assertEquals(0, MagicalPower.of(empty))
		assertEquals(40, MagicalPower.assumed(empty, MagicalPower.of(empty)))
		assertEquals(0, ProfileSlices.of(UUID, reply(empty))!!.magicalPower)
		assertNull(ProfileSlices.of(UUID, reply(member("not base64 at all")))!!.magicalPower)
	}

	@Test
	fun `the inventory API reads as off until the ender chest comes back with it`() {
		assertFalse(ProfileSlices.of(UUID, replyWithEnderChest(null))!!.inventoryApi)
		assertFalse(ProfileSlices.of(UUID, replyWithEnderChest(""))!!.inventoryApi)
		assertTrue(ProfileSlices.of(UUID, replyWithEnderChest("dGVzdA=="))!!.inventoryApi)
	}

	@Test
	fun `a bag that is blank, not base64, not compressed or cut short reads as unreadable`() {
		assertNull(ApiInventory.stacks(""))
		assertNull(ApiInventory.stacks(null))
		assertNull(ApiInventory.stacks("not base64 at all"))
		assertNull(ApiInventory.stacks(Base64.getEncoder().encodeToString("plain bytes".toByteArray())))
		assertNull(ApiInventory.stacks(bag(slot("TEST_TALISMAN", lore = listOf("§f§lCOMMON"))).let { it.substring(0, it.length / 2) }))
		assertEquals(emptyList<ItemStack>(), ApiInventory.stacks(bag()))
	}

	@Test
	fun `the status reply reads as online, offline, or not readable at all`() {
		val online = ProfileSlices.status(json("""{"session":{"online":true,"gameType":"SKYBLOCK","mode":"dynamic","map":"Hub"}}"""))
		assertEquals(OnlineReading.ONLINE, online.reading)
		assertEquals("SKYBLOCK", online.gameType)
		assertEquals("dynamic", online.mode)
		assertEquals("Hub", online.map)

		val offline = ProfileSlices.status(json("""{"session":{"online":false}}"""))
		assertEquals(OnlineReading.OFFLINE, offline.reading)
		assertNull(offline.gameType)

		assertEquals(OnlineReading.UNKNOWN, ProfileSlices.status(null).reading)
		assertEquals(OnlineReading.UNKNOWN, ProfileSlices.status(json("""{"success":true}""")).reading)
	}

	@Test
	fun `the account-wide secrets count comes from the treasure hunter achievement`() {
		assertEquals(99L, ProfileSlices.secrets(json("""{"player":{"achievements":{"skyblock_treasure_hunter":99}}}""")))
		assertNull(ProfileSlices.secrets(json("""{"player":{"achievements":{}}}""")))
		assertNull(ProfileSlices.secrets(json("""{"player":{}}""")))
	}

	@Test
	fun `nothing there and there but empty are told apart`() {
		assertNull(ProfileSlices.of(UUID, json("""{"success":true,"profiles":[]}""")))
		assertNull(ProfileSlices.of(UUID, json("""{"success":true,"profiles":[$UNSELECTED]}""")))
		assertNull(ProfileSlices.of(OTHER_UUID, reply(member())))

		val bare = ProfileSlices.of(UUID, reply(member()))
		assertNotNull(bare)
		assertNull(bare!!.dungeons)
		assertEquals("profile-1", bare.profileId)
		assertEquals("Apple", bare.cuteName)
	}

	private fun noFloors() = DungeonFloors(emptyMap())

	private fun member(
		talismanBag: String? = null,
		contacts: Int = 0,
		prism: Boolean = false,
		tuning: Map<String, Int>? = null,
		enderChest: String? = null
	): JsonObject {
		val contactList = JsonArray()
		repeat(contacts) { contactList.add("contact $it") }
		val member = json("""{"rift":{"access":{"consumed_prism":$prism}},"nether_island_player_data":{"abiphone":{}}}""")
		member.obj("nether_island_player_data")!!.obj("abiphone")!!.add("active_contacts", contactList)
		if (talismanBag != null || enderChest != null) {
			val inventory = JsonObject()
			if (talismanBag != null) inventory.add("bag_contents", json("""{"talisman_bag":{"data":"$talismanBag"}}"""))
			if (enderChest != null) inventory.add("ender_chest_contents", json("""{"data":"$enderChest"}"""))
			member.add("inventory", inventory)
		}
		if (tuning != null) {
			val points = JsonObject()
			for ((stat, value) in tuning) points.addProperty(stat, value)
			val slots = JsonObject()
			slots.add("slot_0", points)
			member.add("accessory_bag_storage", JsonObject().also { it.add("tuning", slots) })
		}
		return member
	}

	private fun reply(member: JsonObject): JsonObject {
		val profile = json(SELECTED)
		profile.obj("members")!!.add(DASHLESS, member)
		val profiles = JsonArray()
		profiles.add(profile)
		return JsonObject().also {
			it.addProperty("success", true)
			it.add("profiles", profiles)
		}
	}

	private fun replyWithEnderChest(data: String?): JsonObject = reply(member(enderChest = data))

	private fun fullReply(): JsonObject = reply(member(bag()).also {
		it.add("dungeons", json(DUNGEONS))
		it.add("player_stats", json("""{"kills":{"watcher_summon_undead":10.0,"master_watcher_summon_undead":5.0}}"""))
	})

	private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

	private companion object {
		private const val UUID = "123e4567-e89b-12d3-a456-426614174000"
		private const val OTHER_UUID = "00000000-0000-0000-0000-000000000000"
		private const val DASHLESS = "123e4567e89b12d3a456426614174000"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private const val SELECTED =
			"""{"profile_id":"profile-1","cute_name":"Apple","selected":true,"members":{}}"""
		private const val UNSELECTED =
			"""{"profile_id":"profile-2","cute_name":"Banana","selected":false,"members":{}}"""

		private const val DUNGEONS = """{
			"dungeon_types":{
				"catacombs":{
					"experience":1000.0,
					"tier_completions":{"0":5,"1":2,"2":3,"total":99},
					"fastest_time_s":{"1":1000,"2":2000},
					"fastest_time_s_plus":{"1":900}
				},
				"master_catacombs":{
					"tier_completions":{"3":1},
					"fastest_time_s":{"3":1500},
					"fastest_time_s_plus":{"3":1400}
				}
			},
			"player_classes":{"healer":{"experience":50.0},"mage":{"experience":125.0}},
			"selected_dungeon_class":"healer",
			"secrets":42
		}"""
	}
}
