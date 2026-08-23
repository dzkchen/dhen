package io.github.dzkchen.dhen.data.item

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.dzkchen.dhen.data.repo.ItemRepo
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
		val id = runCatching { UUID.fromString(skullOwner.getStringOr("Id", "")) }.getOrNull()
			?: UUID.nameUUIDFromBytes(value.toByteArray())
		val name = skullOwner.getStringOr("Name", "")
		val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", value)))
		return ResolvableProfile.createResolved(GameProfile(id, name, properties))
	}
}
