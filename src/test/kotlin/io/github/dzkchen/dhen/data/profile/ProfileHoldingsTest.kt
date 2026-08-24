package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.RepoBackedTest
import io.github.dzkchen.dhen.data.item.ApiInventory
import io.github.dzkchen.dhen.data.item.HeldItem
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.profile.BagFixture.bag
import io.github.dzkchen.dhen.data.profile.BagFixture.slot
import io.github.dzkchen.dhen.data.value.Networth
import io.github.dzkchen.dhen.data.value.NetworthCategory
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class ProfileHoldingsTest : RepoBackedTest() {
	@Test
	fun `a slot keeps its name, its lore, how many it holds and what SkyBlock item it is`() {
		val stack = ApiInventory.stacks(bag(slot("HYPERION", "§6Hyperion", lore = listOf("§7Damage: §c+260"), count = 5)))!!.single()

		assertEquals("§6Hyperion", stack.get(DataComponents.CUSTOM_NAME)?.string)
		assertEquals(listOf("§7Damage: §c+260"), SkyBlockItems.lore(stack).map { it.string })
		assertEquals(5, stack.count)
		assertEquals("HYPERION", SkyBlockItem.parse(SkyBlockItems.customData(stack)!!).id)
	}

	@Test
	fun `an empty slot keeps its place, so pages and armour rows still line up`() {
		val stacks = ApiInventory.stacks(bag(CompoundTag(), slot("BOOTS"), CompoundTag()))!!

		assertEquals(3, stacks.size)
		assertTrue(stacks[0].isEmpty)
		assertEquals("BOOTS", SkyBlockItem.parse(SkyBlockItems.customData(stacks[1])!!).id)
		assertTrue(stacks[2].isEmpty)
	}

	@Test
	fun `an item the repo knows gets its own icon and one it does not gets a barrier`() {
		installRepo("HYPERION" to """{"internalname":"HYPERION","itemid":"minecraft:diamond_sword"}""")

		assertEquals(Items.DIAMOND_SWORD, ApiInventory.stacks(bag(slot("HYPERION")))!!.single().item)
		assertEquals(Items.BARRIER, ApiInventory.stacks(bag(slot("NOT_IN_THE_REPO")))!!.single().item)
	}

	@Test
	fun `a slot carrying a head texture becomes a player head wearing it`() {
		val stack = ApiInventory.stacks(bag(head()))!!.single()

		assertEquals(Items.PLAYER_HEAD, stack.item)
		assertEquals(TEXTURE, SkyBlockItems.skullTexture(stack))
	}

	@Test
	fun `backpack pages read in page order and a page that is not numbered is skipped`() {
		val holdings = holdings(
			member = DataFixture.json(
				"""{"inventory":{"backpack_contents":{
					"2":{"data":"${bag(slot("SECOND"))}"},
					"scrambled":{"data":"${bag(slot("NEVER"))}"},
					"0":{"data":"${bag(slot("FIRST"))}"}}}}"""
			)
		)

		assertEquals(listOf("FIRST", "SECOND"), holdings.backpacks.map { it.item.id })
	}

	@Test
	fun `every inventory is priced under its own heading`() {
		val holdings = holdings(
			member = DataFixture.json(
				"""{"inventory":{
					"inv_contents":{"data":"${bag(slot("INV"))}"},
					"inv_armor":{"data":"${bag(slot("WORN"))}"},
					"equipment_contents":{"data":"${bag(slot("GEAR"))}"},
					"ender_chest_contents":{"data":"${bag(slot("ENDER"))}"},
					"personal_vault_contents":{"data":"${bag(slot("VAULT"))}"},
					"backpack_contents":{"0":{"data":"${bag(slot("PACK"))}"}},
					"bag_contents":{
						"talisman_bag":{"data":"${bag(slot("TALI"))}"},
						"fishing_bag":{"data":"${bag(slot("FISH"))}"},
						"quiver":{"data":"${bag(slot("ARROW"))}"}}}}"""
			)
		)
		val filed = mapOf(
			NetworthCategory.INVENTORY to "INV",
			NetworthCategory.ARMOR to "WORN",
			NetworthCategory.EQUIPMENT to "GEAR",
			NetworthCategory.ENDERCHEST to "ENDER",
			NetworthCategory.PERSONAL_VAULT to "VAULT",
			NetworthCategory.BACKPACKS to "PACK",
			NetworthCategory.TALISMAN_BAG to "TALI",
			NetworthCategory.FISHING_BAG to "FISH",
			NetworthCategory.QUIVER_BAG to "ARROW"
		)

		for ((category, id) in filed) assertEquals(id, holdings.items(category).single().item.id, category.name)
	}

	@Test
	fun `a sack nobody has anything in is left out`() {
		val holdings = holdings(
			member = DataFixture.json("""{"inventory":{"sacks_counts":{"ENCHANTED_DIAMOND":5,"EMPTY_SACK":0}}}""")
		)

		assertEquals(mapOf("ENCHANTED_DIAMOND" to 5L), holdings.sacks)
	}

	@Test
	fun `a pet reads back whole, and one missing its rarity is dropped`() {
		val holdings = holdings(
			member = DataFixture.json(
				"""{"pets_data":{"pets":[
					{"type":"ENDER_DRAGON","tier":"LEGENDARY","exp":25353230,"heldItem":"PET_ITEM_TIER_BOOST","candyUsed":3},
					{"type":"GOLDEN_DRAGON"}]}}"""
			)
		)

		val pet = holdings.pets.single()
		assertEquals("ENDER_DRAGON", pet.type)
		assertEquals("LEGENDARY", pet.tier)
		assertEquals("PET_ITEM_TIER_BOOST", pet.heldItem)
		assertEquals(3, pet.candyUsed)
		assertNull(pet.skin)
	}

	@Test
	fun `the wardrobe and the equipment sets both count as loadout`() {
		val holdings = holdings(
			member = DataFixture.json(
				"""{"loadout":{
					"armor":{"equipped_set":0,"1":{"id":1,"HELMET":{"data":"${bag(slot("HELM"))}"},"BOOTS":{"data":"${bag(slot("BOOTS"))}"}}},
					"equipment":{"equipped_set":0,"1":{"id":1,"EQUIPMENT_SLOT_1":{"data":"${bag(slot("CLOAK"))}"}}}}}"""
			)
		)

		assertEquals(listOf("HELM", "BOOTS", "CLOAK"), holdings.loadout.map { it.item.id })
	}

	@Test
	fun `the three coin balances keep the names the networth tab shows`() {
		val holdings = holdings(
			profile = DataFixture.json("""{"banking":{"balance":300}}"""),
			member = DataFixture.json("""{"currencies":{"coin_purse":100},"profile":{"bank_account":200}}""")
		)

		assertEquals(mapOf("Purse" to 100L, "Solo Bank" to 200L, "Profile Bank" to 300L), holdings.currency())
	}

	@Test
	fun `a hidden bank balance is left out rather than reading as zero coins`() {
		val holdings = holdings(
			profile = JsonObject(),
			member = DataFixture.json("""{"currencies":{"coin_purse":100}}""")
		)

		assertEquals(mapOf("Purse" to 100L), holdings.currency())
	}

	@Test
	fun `a reader that changes a stack it was handed does not change what the next reader gets`() {
		val holdings = holdings(
			member = DataFixture.json("""{"inventory":{"inv_contents":{"data":"${bag(slot("HYPERION", "§6Hyperion", count = 3))}"}}}""")
		)

		holdings.items(NetworthCategory.INVENTORY).single().stack().also {
			it.count = 64
			it.set(DataComponents.CUSTOM_NAME, Component.literal("§cMine now"))
		}

		val next = holdings.items(NetworthCategory.INVENTORY).single()
		assertEquals(3, next.count)
		assertEquals("§6Hyperion", next.stack().get(DataComponents.CUSTOM_NAME)?.string)
		assertEquals(3, holdings.inventory.single().stack().count)
		assertThrows(UnsupportedOperationException::class.java) { (holdings.inventory as MutableList).clear() }
	}

	@Test
	fun `the sack counts and the pet list cannot be changed by whoever is handed them`() {
		val holdings = holdings(
			member = DataFixture.json(
				"""{"inventory":{"sacks_counts":{"ENCHANTED_DIAMOND":5}},
					"pets_data":{"pets":[{"type":"ENDER_DRAGON","tier":"LEGENDARY","exp":1}]}}"""
			)
		)

		assertThrows(UnsupportedOperationException::class.java) { (holdings.sacks as MutableMap)["GOLD"] = 1L }
		assertThrows(UnsupportedOperationException::class.java) { (holdings.pets as MutableList).clear() }
		assertEquals(mapOf("ENCHANTED_DIAMOND" to 5L), holdings.sacks)
		assertEquals(1, holdings.pets.size)
	}

	@Test
	fun `two heads whose owner ids are unreadable still tell each other apart by texture`() {
		val stacks = ApiInventory.stacks(bag(head(ownerId = "not a uuid"), head(ownerId = "", texture = OTHER_TEXTURE)))!!

		assertNotEquals(SkyBlockItems.skullId(stacks[0]), SkyBlockItems.skullId(stacks[1]))
		assertEquals(SkyBlockItems.skullId(stacks[0]), SkyBlockItems.skullId(ApiInventory.stacks(bag(head(ownerId = "")))!!.single()))
	}

	@Test
	fun `a player with the inventory API turned off reads as empty rather than failing`() {
		val holdings = holdings(member = JsonObject())

		assertEquals(emptyList<HeldItem>(), holdings.inventory)
		assertEquals(emptyList<HeldItem>(), holdings.enderChest)
		assertEquals(emptyList<HeldItem>(), holdings.talismans)
		assertFalse(holdings.inventoryApi)
	}

	@Test
	fun `the profile prices as a whole, item and purse together`() {
		installRepo(
			"HYPERION" to """{"internalname":"HYPERION","displayname":"§6Hyperion"}""",
			"ENCHANTED_DIAMOND" to """{"internalname":"ENCHANTED_DIAMOND","displayname":"§aEnchanted Diamond"}"""
		)
		DataFixture.installPrices(scope, LOWEST_BINS)

		val holdings = holdings(
			member = DataFixture.json(
				"""{"inventory":{
					"inv_contents":{"data":"${bag(slot("HYPERION", "§6Hyperion"))}"},
					"sacks_counts":{"ENCHANTED_DIAMOND":2}},
					"currencies":{"coin_purse":5}}"""
			)
		)
		val report = Networth.of(holdings, PriceSource.LOWEST_BIN)!!

		assertEquals(mapOf("Hyperion" to 1000L), report.categories[NetworthCategory.INVENTORY])
		assertEquals(mapOf("Enchanted Diamond" to 2000L), report.categories[NetworthCategory.SACKS])
		assertEquals(3005L, report.total)
	}

	private fun installRepo(vararg items: Pair<String, String>) =
		DataFixture.installRepo(scope, repoRoot, items.toMap())

	private fun holdings(profile: JsonObject = DataFixture.json("""{"banking":{}}"""), member: JsonObject): ProfileHoldings =
		ProfileHoldings.of(profile, member)

	private fun head(ownerId: String = BagFixture.VIEWED_UUID, texture: String = TEXTURE): CompoundTag {
		val texturesEntry = CompoundTag()
		texturesEntry.putString("Value", texture)
		val textures = ListTag()
		textures.add(texturesEntry)
		val properties = CompoundTag()
		properties.put("textures", textures)
		val owner = CompoundTag()
		owner.putString("Id", ownerId)
		owner.putString("Name", "Dhen")
		owner.put("Properties", properties)
		return slot("SKULL", "§fA Head").also { it.getCompoundOrEmpty("tag").put("SkullOwner", owner) }
	}


	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private const val TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjIn19fQ=="
		private const val OTHER_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUveHl6In19fQ=="

		private val LOWEST_BINS = """
			{
			  "HYPERION": 1000.0,
			  "ENCHANTED_DIAMOND": 1000.0
			}
		""".trimIndent()
	}
}
