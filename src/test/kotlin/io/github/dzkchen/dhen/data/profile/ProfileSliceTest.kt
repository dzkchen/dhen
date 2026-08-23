package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.item.ApiInventory
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.profile.BagFixture.CATACOMBS
import io.github.dzkchen.dhen.data.profile.BagFixture.VIEWED_UUID
import io.github.dzkchen.dhen.data.profile.BagFixture.bag
import io.github.dzkchen.dhen.data.profile.BagFixture.member
import io.github.dzkchen.dhen.data.profile.BagFixture.reply
import io.github.dzkchen.dhen.data.profile.BagFixture.slot
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ProfileSliceTest {
	@Suppress("AssertBetweenInconvertibleTypes")
	@Test
	fun `a full profiles reply decodes into every field of the dungeon slice`() {
		val dungeons = ProfileSlices.of(VIEWED_UUID, fullReply())!!.dungeons!!

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
		val dungeons = ProfileSlices.of(VIEWED_UUID, fullReply())!!.dungeons!!

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
	fun `an empty talisman bag is nought magical power on the slice, an unreadable one none at all`() {
		assertEquals(0, ProfileSlices.of(VIEWED_UUID, reply(member(bag())))!!.magicalPower)
		assertNull(ProfileSlices.of(VIEWED_UUID, reply(member("not base64 at all")))!!.magicalPower)
	}

	@Test
	fun `the inventory API reads as off until the ender chest comes back with it`() {
		assertFalse(ProfileSlices.of(VIEWED_UUID, replyWithEnderChest(null))!!.inventoryApi)
		assertFalse(ProfileSlices.of(VIEWED_UUID, replyWithEnderChest(""))!!.inventoryApi)
		assertTrue(ProfileSlices.of(VIEWED_UUID, replyWithEnderChest("dGVzdA=="))!!.inventoryApi)
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
		val online = ProfileSlices.status(DataFixture.json("""{"session":{"online":true,"gameType":"SKYBLOCK","mode":"dynamic","map":"Hub"}}"""))
		assertEquals(OnlineReading.ONLINE, online.reading)
		assertEquals("SKYBLOCK", online.gameType)
		assertEquals("dynamic", online.mode)
		assertEquals("Hub", online.map)

		val offline = ProfileSlices.status(DataFixture.json("""{"session":{"online":false}}"""))
		assertEquals(OnlineReading.OFFLINE, offline.reading)
		assertNull(offline.gameType)

		assertEquals(OnlineReading.UNKNOWN, ProfileSlices.status(null).reading)
		assertEquals(OnlineReading.UNKNOWN, ProfileSlices.status(DataFixture.json("""{"success":true}""")).reading)
	}

	@Test
	fun `the account-wide secrets count comes from the treasure hunter achievement`() {
		assertEquals(99L, ProfileSlices.secrets(DataFixture.json("""{"player":{"achievements":{"skyblock_treasure_hunter":99}}}""")))
		assertNull(ProfileSlices.secrets(DataFixture.json("""{"player":{"achievements":{}}}""")))
		assertNull(ProfileSlices.secrets(DataFixture.json("""{"player":{}}""")))
	}

	@Test
	fun `nothing there and there but empty are told apart`() {
		assertNull(ProfileSlices.of(VIEWED_UUID, DataFixture.json("""{"success":true,"profiles":[]}""")))
		assertNull(ProfileSlices.of(VIEWED_UUID, DataFixture.json("""{"success":true,"profiles":[$UNSELECTED]}""")))
		assertNull(ProfileSlices.of(OTHER_UUID, reply(member())))

		val bare = ProfileSlices.of(VIEWED_UUID, reply(member()))
		assertNotNull(bare)
		assertNull(bare!!.dungeons)
		assertEquals("profile-1", bare.profileId)
		assertEquals("Apple", bare.cuteName)
	}

	private fun noFloors() = DungeonFloors(emptyMap())

	private fun replyWithEnderChest(data: String?): JsonObject = reply(member(enderChest = data))

	private fun fullReply(): JsonObject = reply(member(bag()).also {
		it.add("dungeons", DataFixture.json(DUNGEONS))
		it.add("player_stats", DataFixture.json("""{"kills":{"watcher_summon_undead":10.0,"master_watcher_summon_undead":5.0}}"""))
	})

	private companion object {
		private const val OTHER_UUID = "00000000-0000-0000-0000-000000000000"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private const val UNSELECTED =
			"""{"profile_id":"profile-2","cute_name":"Banana","selected":false,"members":{}}"""

		private const val DUNGEONS = """{
			"dungeon_types":{
				"catacombs":$CATACOMBS,
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
