package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ApiInventory
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

class HeldStacks internal constructor(private val held: List<ItemStack>) {
	fun copies(): List<ItemStack> = held.map(ItemStack::copy)
}

class ProfileHoldings internal constructor(
	val inventory: HeldStacks,
	val armor: HeldStacks,
	val equipment: HeldStacks,
	val enderChest: HeldStacks,
	val backpacks: HeldStacks,
	val talismans: HeldStacks,
	val fishingBag: HeldStacks,
	val quiver: HeldStacks,
	val potionBag: HeldStacks,
	val personalVault: HeldStacks,
	val candy: HeldStacks,
	val carnivalMasks: HeldStacks,
	val loadout: HeldStacks,
	val sacks: Map<String, Long>,
	val pets: List<PetInfo>,
	val purse: Long?,
	val soloBank: Long?,
	val profileBank: Long?,
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
		else -> NONE
	}.copies()

	override fun sacks(): Map<String, Long> = sacks

	override fun pets(): List<PetInfo> = pets

	override fun currency(): Map<String, Long> = buildMap {
		purse?.let { put(PURSE, it) }
		soloBank?.let { put(SOLO_BANK, it) }
		profileBank?.let { put(PROFILE_BANK, it) }
	}

	internal companion object {
		private val NONE = HeldStacks(emptyList())

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
				purse = member.obj("currencies")?.number("coin_purse")?.toLong(),
				soloBank = member.obj("profile")?.number("bank_account")?.toLong(),
				profileBank = profile.obj("banking")?.number("balance")?.toLong(),
				inventoryApi = ProfileSlices.inventoryApi(member)
			)
		}

		private fun stacks(owner: JsonObject?, key: String): HeldStacks = HeldStacks(slots(owner, key))

		private fun slots(owner: JsonObject?, key: String): List<ItemStack> =
			ApiInventory.stacks(owner?.obj(key)?.text("data")) ?: emptyList()

		private fun backpacks(pages: JsonObject?): HeldStacks {
			if (pages == null) return NONE
			return HeldStacks(pages.keySet().filter { it.toIntOrNull() != null }.sortedBy(String::toInt).flatMap { slots(pages, it) })
		}

		private fun loadout(loadout: JsonObject?): HeldStacks {
			if (loadout == null) return NONE
			val held = ArrayList<ItemStack>()
			for (section in LOADOUT_SECTIONS) {
				val sets = loadout.obj(section) ?: continue
				for (name in sets.keySet()) {
					if (name == EQUIPPED_SET) continue
					val set = sets.obj(name) ?: continue
					for (slot in set.keySet()) held += slots(set, slot)
				}
			}
			return HeldStacks(held)
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
