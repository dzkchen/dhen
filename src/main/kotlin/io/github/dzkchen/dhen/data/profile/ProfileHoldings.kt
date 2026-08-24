package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ApiInventory
import io.github.dzkchen.dhen.data.item.HeldItem
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.value.NetworthCategory
import io.github.dzkchen.dhen.data.value.NetworthSource
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import net.minecraft.world.item.ItemStack

private const val PURSE = "Purse"
private const val SOLO_BANK = "Solo Bank"
private const val PROFILE_BANK = "Profile Bank"
private const val EQUIPPED_SET = "equipped_set"
private val LOADOUT_SECTIONS = listOf("armor", "equipment")

class ProfileHoldings internal constructor(
	val inventory: List<HeldItem>,
	val armor: List<HeldItem>,
	val equipment: List<HeldItem>,
	val enderChest: List<HeldItem>,
	val backpacks: List<HeldItem>,
	val talismans: List<HeldItem>,
	val fishingBag: List<HeldItem>,
	val quiver: List<HeldItem>,
	val potionBag: List<HeldItem>,
	val personalVault: List<HeldItem>,
	val candy: List<HeldItem>,
	val carnivalMasks: List<HeldItem>,
	val loadout: List<HeldItem>,
	val sacks: Map<String, Long>,
	val pets: List<PetInfo>,
	val purse: Long?,
	val soloBank: Long?,
	val profileBank: Long?,
	val inventoryApi: Boolean
) : NetworthSource {
	override fun items(category: NetworthCategory): List<HeldItem> = when (category) {
		NetworthCategory.INVENTORY -> inventory
		NetworthCategory.ARMOR -> armor
		NetworthCategory.EQUIPMENT -> equipment
		NetworthCategory.ENDERCHEST -> enderChest
		NetworthCategory.BACKPACKS -> backpacks
		NetworthCategory.TALISMAN_BAG -> talismans
		NetworthCategory.FISHING_BAG -> fishingBag
		NetworthCategory.QUIVER_BAG -> quiver
		NetworthCategory.PERSONAL_VAULT -> personalVault
		NetworthCategory.LOADOUT -> loadout
		else -> emptyList()
	}

	override fun sacks(): Map<String, Long> = sacks

	override fun pets(): List<PetInfo> = pets

	override fun currency(): Map<String, Long> = buildMap {
		purse?.let { put(PURSE, it) }
		soloBank?.let { put(SOLO_BANK, it) }
		profileBank?.let { put(PROFILE_BANK, it) }
	}

	internal companion object {
		fun of(profile: JsonObject, member: JsonObject): ProfileHoldings {
			val inventory = member.obj("inventory")
			val bags = inventory?.obj("bag_contents")
			val shared = member.obj("shared_inventory")
			return ProfileHoldings(
				inventory = held(inventory, "inv_contents"),
				armor = held(inventory, "inv_armor"),
				equipment = held(inventory, "equipment_contents"),
				enderChest = held(inventory, "ender_chest_contents"),
				backpacks = backpacks(inventory?.obj("backpack_contents")),
				talismans = held(bags, "talisman_bag"),
				fishingBag = held(bags, "fishing_bag"),
				quiver = held(bags, "quiver"),
				potionBag = held(bags, "potion_bag"),
				personalVault = held(inventory, "personal_vault_contents"),
				candy = held(shared, "candy_inventory_contents"),
				carnivalMasks = held(shared, "carnival_mask_inventory_contents"),
				loadout = loadout(member.obj("loadout")),
				sacks = sacks(inventory?.obj("sacks_counts")),
				pets = pets(member.obj("pets_data")?.array("pets")),
				purse = member.obj("currencies")?.number("coin_purse")?.toLong(),
				soloBank = member.obj("profile")?.number("bank_account")?.toLong(),
				profileBank = profile.obj("banking")?.number("balance")?.toLong(),
				inventoryApi = ProfileSlices.inventoryApi(member)
			)
		}

		private fun held(owner: JsonObject?, key: String): List<HeldItem> = held(slots(owner, key))

		private fun held(stacks: List<ItemStack>): List<HeldItem> =
			if (stacks.isEmpty()) emptyList() else buildList(stacks.size) { stacks.mapTo(this, HeldItem::of) }

		private fun slots(owner: JsonObject?, key: String): List<ItemStack> =
			ApiInventory.stacks(owner?.obj(key)?.text("data")) ?: emptyList()

		private fun backpacks(pages: JsonObject?): List<HeldItem> {
			if (pages == null) return emptyList()
			return held(pages.keySet().filter { it.toIntOrNull() != null }.sortedBy(String::toInt).flatMap { slots(pages, it) })
		}

		private fun loadout(loadout: JsonObject?): List<HeldItem> {
			if (loadout == null) return emptyList()
			val stacks = ArrayList<ItemStack>()
			for (section in LOADOUT_SECTIONS) {
				val sets = loadout.obj(section) ?: continue
				for (name in sets.keySet()) {
					if (name == EQUIPPED_SET) continue
					val set = sets.obj(name) ?: continue
					for (slot in set.keySet()) stacks += slots(set, slot)
				}
			}
			return held(stacks)
		}

		private fun sacks(counts: JsonObject?): Map<String, Long> = buildMap {
			for (marketId in counts.keys()) {
				val amount = counts?.number(marketId)?.toLong() ?: continue
				if (amount > 0L) put(marketId, amount)
			}
		}

		private fun pets(pets: JsonArray?): List<PetInfo> =
			buildList { pets?.mapNotNullTo(this) { (it as? JsonObject)?.let(SkyBlockItem.Companion::petInfoOf) } }
	}
}
