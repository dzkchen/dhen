package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ApiInventory
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.value.NetworthCategory
import io.github.dzkchen.dhen.data.value.NetworthSource
import io.github.dzkchen.dhen.util.array
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
	val inventory: List<ItemStack>,
	val armor: List<ItemStack>,
	val equipment: List<ItemStack>,
	val enderChest: List<ItemStack>,
	val backpacks: List<ItemStack>,
	val talismans: List<ItemStack>,
	val fishingBag: List<ItemStack>,
	val quiver: List<ItemStack>,
	val potionBag: List<ItemStack>,
	val personalVault: List<ItemStack>,
	val candy: List<ItemStack>,
	val carnivalMasks: List<ItemStack>,
	val loadout: List<ItemStack>,
	val sacks: Map<String, Long>,
	val pets: List<PetInfo>,
	val purse: Long,
	val soloBank: Long,
	val profileBank: Long,
	val inventoryApi: Boolean
) : NetworthSource {
	override fun items(category: NetworthCategory): List<ItemStack> = when (category) {
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

	override fun currency(): Map<String, Long> =
		linkedMapOf(PURSE to purse, SOLO_BANK to soloBank, PROFILE_BANK to profileBank)

	internal companion object {
		fun of(profile: JsonObject, member: JsonObject): ProfileHoldings {
			val inventory = member.obj("inventory")
			val bags = inventory?.obj("bag_contents")
			val shared = member.obj("shared_inventory")
			return ProfileHoldings(
				inventory = stacks(inventory, "inv_contents"),
				armor = stacks(inventory, "inv_armor"),
				equipment = stacks(inventory, "equipment_contents"),
				enderChest = stacks(inventory, "ender_chest_contents"),
				backpacks = backpacks(inventory?.obj("backpack_contents")),
				talismans = stacks(bags, "talisman_bag"),
				fishingBag = stacks(bags, "fishing_bag"),
				quiver = stacks(bags, "quiver"),
				potionBag = stacks(bags, "potion_bag"),
				personalVault = stacks(inventory, "personal_vault_contents"),
				candy = stacks(shared, "candy_inventory_contents"),
				carnivalMasks = stacks(shared, "carnival_mask_inventory_contents"),
				loadout = loadout(member.obj("loadout")),
				sacks = sacks(inventory?.obj("sacks_counts")),
				pets = pets(member.obj("pets_data")?.array("pets")),
				purse = member.obj("currencies")?.number("coin_purse")?.toLong() ?: 0L,
				soloBank = member.obj("profile")?.number("bank_account")?.toLong() ?: 0L,
				profileBank = profile.obj("banking")?.number("balance")?.toLong() ?: 0L,
				inventoryApi = ProfileSlices.inventoryApi(member)
			)
		}

		private fun stacks(owner: JsonObject?, key: String): List<ItemStack> =
			ApiInventory.stacks(owner?.obj(key)?.text("data")) ?: emptyList()

		private fun backpacks(pages: JsonObject?): List<ItemStack> {
			if (pages == null) return emptyList()
			return pages.keySet().filter { it.toIntOrNull() != null }.sortedBy(String::toInt).flatMap { stacks(pages, it) }
		}

		private fun loadout(loadout: JsonObject?): List<ItemStack> {
			if (loadout == null) return emptyList()
			val held = ArrayList<ItemStack>()
			for (section in LOADOUT_SECTIONS) {
				val sets = loadout.obj(section) ?: continue
				for (name in sets.keySet()) {
					if (name == EQUIPPED_SET) continue
					val set = sets.obj(name) ?: continue
					for (slot in set.keySet()) held += stacks(set, slot)
				}
			}
			return held
		}

		private fun sacks(counts: JsonObject?): Map<String, Long> {
			if (counts == null) return emptyMap()
			val held = LinkedHashMap<String, Long>(counts.size())
			for (marketId in counts.keySet()) {
				val amount = counts.number(marketId)?.toLong() ?: continue
				if (amount > 0L) held[marketId] = amount
			}
			return held
		}

		private fun pets(pets: JsonArray?): List<PetInfo> =
			pets?.mapNotNull { (it as? JsonObject)?.let(SkyBlockItem.Companion::petInfoOf) } ?: emptyList()
	}
}
