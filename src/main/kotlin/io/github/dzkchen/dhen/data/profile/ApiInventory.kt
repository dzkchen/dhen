package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import java.io.ByteArrayInputStream
import java.util.Base64

internal class ApiItem(val name: String, val id: String, val lore: List<String>)

internal object ApiInventory {
	private const val NBT_QUOTA = 8L * 1024 * 1024

	fun items(blob: String): List<ApiItem>? {
		if (blob.isBlank()) return null
		val root = runCatching {
			NbtIo.readCompressed(ByteArrayInputStream(Base64.getDecoder().decode(blob)), NbtAccounter.create(NBT_QUOTA))
		}.getOrNull() ?: return null
		val slots = root.getListOrEmpty("i")
		val items = ArrayList<ApiItem>(slots.size)
		for (slot in slots.indices) {
			val tag = slots.getCompoundOrEmpty(slot).getCompoundOrEmpty("tag")
			if (tag.isEmpty) continue
			val display = tag.getCompoundOrEmpty("display")
			val lore = display.getListOrEmpty("Lore")
			items += ApiItem(
				name = display.getStringOr("Name", ""),
				id = tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""),
				lore = List(lore.size) { line -> lore.getStringOr(line, "") }
			)
		}
		return items
	}
}

internal object MagicalPower {
	private const val HEGEMONY = "HEGEMONY_ARTIFACT"
	private const val ABIPHONE = "ABICASE"
	private const val HAT_FAMILY = "PARTY_HAT"
	private const val PRISM_POWER = 11
	private const val POWER_PER_TUNING = 10
	private const val REQUIREMENT_WORD = "Requires"

	private const val BALLOON_HAT_FAMILY = "BALLOON_HAT"
	private val unusableLine = Regex("^[^A-Za-z]*Requires .+\\.$")

	fun of(member: JsonObject): Int? {
		val items = ApiInventory.items(talismanBag(member)) ?: return null
		val contacts = member.obj("nether_island_player_data")?.obj("abiphone")?.array("active_contacts")?.size() ?: 0
		val strongest = HashMap<String, Int>()
		for (item in items) {
			if (item.lore.any { it.contains(REQUIREMENT_WORD) && unusableLine.matches(withoutCodes(it)) }) continue
			val family = if (item.id.startsWith(HAT_FAMILY) || item.id.startsWith(BALLOON_HAT_FAMILY)) HAT_FAMILY else item.id
			val power = powerOf(item, contacts)
			if (power > (strongest[family] ?: 0)) strongest[family] = power
		}
		return strongest.values.sum() + if (member.obj("rift")?.obj("access")?.flag("consumed_prism") == true) PRISM_POWER else 0
	}

	fun assumed(member: JsonObject, power: Int?): Int {
		if (power != null && power != 0) return power
		val tuning = member.obj("accessory_bag_storage")?.obj("tuning")?.obj("slot_0") ?: return 0
		return tuning.keySet().sumOf { tuning.number(it)?.toInt() ?: 0 } * POWER_PER_TUNING
	}

	private fun talismanBag(member: JsonObject): String =
		member.obj("inventory")?.obj("bag_contents")?.obj("talisman_bag")?.text("data") ?: ""

	private fun powerOf(item: ApiItem, contacts: Int): Int {
		val rarity = rarityOf(item)?.magicalPower ?: 0
		return when (item.id) {
			HEGEMONY -> rarity * 2
			ABIPHONE -> rarity + contacts / 2
			else -> rarity
		}
	}

	private fun rarityOf(item: ApiItem): ItemRarity? =
		item.lore.asReversed().firstNotNullOfOrNull(ItemRarity::fromLoreLine) ?: ItemRarity.fromPetName(item.name)
}
