package io.github.dzkchen.dhen.data.profile

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonObject
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID

internal object ApiInventory {
	private const val NBT_QUOTA = 8L * 1024 * 1024
	private val FALLBACK_UUID = UUID(0L, 0L)

	fun stacks(blob: String?): List<ItemStack>? {
		if (blob.isNullOrBlank()) return null
		val root = runCatching {
			NbtIo.readCompressed(ByteArrayInputStream(Base64.getDecoder().decode(blob)), NbtAccounter.create(NBT_QUOTA))
		}.getOrNull() ?: return null
		val slots = root.getListOrEmpty("i")
		val stacks = ArrayList<ItemStack>(slots.size)
		for (index in slots.indices) {
			val slot = slots.getCompoundOrEmpty(index)
			val tag = slot.getCompoundOrEmpty("tag")
			if (tag.isEmpty) {
				stacks += ItemStack.EMPTY
				continue
			}
			val extra = tag.getCompoundOrEmpty("ExtraAttributes")
			val skullOwner = tag.getCompoundOrEmpty("SkullOwner")
			val count = slot.getByteOr("Count", 1).toInt().coerceAtLeast(1)
			val stack = ItemStack(if (skullOwner.isEmpty) chooseItem(extra) else Items.PLAYER_HEAD, count)
			if (!extra.isEmpty) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(extra))
			val display = tag.getCompoundOrEmpty("display")
			val name = display.getStringOr("Name", "")
			if (name.isNotEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
			val loreTag = display.getListOrEmpty("Lore")
			if (!loreTag.isEmpty) {
				val lines = List(loreTag.size) { line -> Component.literal(loreTag.getStringOr(line, "")) }
				stack.set(DataComponents.LORE, ItemLore(lines))
			}
			headProfile(skullOwner)?.let { stack.set(DataComponents.PROFILE, it) }
			stacks += stack
		}
		return stacks
	}

	private fun chooseItem(extra: CompoundTag): Item {
		val vanilla = ItemRepo.item(extra.getStringOr("id", ""))?.itemId?.let(Identifier::tryParse) ?: return Items.BARRIER
		return BuiltInRegistries.ITEM.getOptional(vanilla).orElse(null) ?: Items.BARRIER
	}

	private fun headProfile(skullOwner: CompoundTag): ResolvableProfile? {
		val textures = skullOwner.getCompoundOrEmpty("Properties").getListOrEmpty("textures")
		if (textures.isEmpty) return null
		val value = textures.getCompoundOrEmpty(0).getStringOr("Value", "")
		if (value.isEmpty()) return null
		val id = runCatching { UUID.fromString(skullOwner.getStringOr("Id", "")) }.getOrNull() ?: FALLBACK_UUID
		val name = skullOwner.getStringOr("Name", "")
		val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", value)))
		return ResolvableProfile.createResolved(GameProfile(id, name, properties))
	}
}

internal object MagicalPower {
	private const val HEGEMONY = "HEGEMONY_ARTIFACT"
	private const val ABIPHONE = "ABICASE"
	private const val HAT_FAMILY = "PARTY_HAT"
	private const val PRISM_POWER = 11
	private const val POWER_PER_TUNING = 10
	private const val BALLOON_HAT_FAMILY = "BALLOON_HAT"
	private val unusableLine = Regex("^[^A-Za-z]*Requires .+\\.$")

	fun of(member: JsonObject): Int? {
		val stacks = ApiInventory.stacks(talismanBag(member)) ?: return null
		val contacts = member.obj("nether_island_player_data")?.obj("abiphone")?.array("active_contacts")?.size() ?: 0
		val strongest = HashMap<String, Int>()
		for (stack in stacks) {
			val data = SkyBlockItems.customData(stack) ?: continue
			if (SkyBlockItems.lore(stack).any { unusableLine.matches(withoutCodes(it.string)) }) continue
			val item = SkyBlockItem.parse(data)
			val family = if (item.id.startsWith(HAT_FAMILY) || item.id.startsWith(BALLOON_HAT_FAMILY)) HAT_FAMILY else item.id
			val power = powerOf(item, stack, contacts)
			if (power > (strongest[family] ?: 0)) strongest[family] = power
		}
		return strongest.values.sum() + if (member.obj("rift")?.obj("access")?.flag("consumed_prism") == true) PRISM_POWER else 0
	}

	fun assumed(member: JsonObject, power: Int?): Int {
		if (power != null && power != 0) return power
		val tuning = member.obj("accessory_bag_storage")?.obj("tuning")?.obj("slot_0") ?: return 0
		return tuning.keySet().sumOf { tuning.number(it)?.toInt() ?: 0 } * POWER_PER_TUNING
	}

	private fun talismanBag(member: JsonObject): String? =
		member.obj("inventory")?.obj("bag_contents")?.obj("talisman_bag")?.text("data")

	private fun powerOf(item: SkyBlockItem, stack: ItemStack, contacts: Int): Int {
		val rarity = item.rarity(stack).magicalPower
		return when (item.id) {
			HEGEMONY -> rarity * 2
			ABIPHONE -> rarity + contacts / 2
			else -> rarity
		}
	}
}
