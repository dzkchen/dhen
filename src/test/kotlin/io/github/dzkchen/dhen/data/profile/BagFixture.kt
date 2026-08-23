package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.util.obj
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StringTag
import java.io.ByteArrayOutputStream
import java.util.Base64

internal object BagFixture {
	const val VIEWED = "123e4567e89b12d3a456426614174000"

	const val VIEWED_UUID = "123e4567-e89b-12d3-a456-426614174000"

	const val CATACOMBS =
		"""{"experience":1000.0,"tier_completions":{"0":5,"1":2,"2":3,"total":99},""" +
			""""fastest_time_s":{"1":1000,"2":2000},"fastest_time_s_plus":{"1":900}}"""

	fun member(
		talismanBag: String? = null,
		contacts: Int = 0,
		prism: Boolean = false,
		tuning: Map<String, Int>? = null,
		enderChest: String? = null
	): JsonObject {
		val contactList = JsonArray()
		repeat(contacts) { contactList.add("contact $it") }
		val member = DataFixture.json("""{"rift":{"access":{"consumed_prism":$prism}},"nether_island_player_data":{"abiphone":{}}}""")
		member.obj("nether_island_player_data")!!.obj("abiphone")!!.add("active_contacts", contactList)
		if (talismanBag != null || enderChest != null) {
			val inventory = JsonObject()
			if (talismanBag != null) inventory.add("bag_contents", DataFixture.json("""{"talisman_bag":{"data":"$talismanBag"}}"""))
			if (enderChest != null) inventory.add("ender_chest_contents", DataFixture.json("""{"data":"$enderChest"}"""))
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

	fun reply(member: JsonObject): JsonObject {
		val profile = DataFixture.json(SELECTED)
		profile.obj("members")!!.add(VIEWED, member)
		val profiles = JsonArray()
		profiles.add(profile)
		return JsonObject().also {
			it.addProperty("success", true)
			it.add("profiles", profiles)
		}
	}

	fun slot(id: String, name: String = id, lore: List<String> = emptyList(), count: Int = 1): CompoundTag {
		val extras = CompoundTag()
		extras.putString("id", id)
		val display = CompoundTag()
		display.putString("Name", name)
		if (lore.isNotEmpty()) {
			val lines = ListTag()
			for (line in lore) lines.add(StringTag.valueOf(line))
			display.put("Lore", lines)
		}
		val tag = CompoundTag()
		tag.put("ExtraAttributes", extras)
		tag.put("display", display)
		val slot = CompoundTag()
		slot.putByte("Count", count.toByte())
		slot.put("tag", tag)
		return slot
	}

	fun bag(vararg slots: CompoundTag): String {
		val list = ListTag()
		for (slot in slots) list.add(slot)
		val root = CompoundTag()
		root.put("i", list)
		val bytes = ByteArrayOutputStream()
		NbtIo.writeCompressed(root, bytes)
		return Base64.getEncoder().encodeToString(bytes.toByteArray())
	}

	private const val SELECTED =
		"""{"profile_id":"profile-1","cute_name":"Apple","selected":true,"members":{}}"""
}
