package io.github.dzkchen.dhen.data.item

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

internal object ItemFixture {
	const val HELD_UUID = "3e0d0b3a-6d2e-4a1e-9c1d-2b9a1f0c7e55"

	fun bootstrap() {
		bootstrapMinecraft()
		BuiltInRegistries.ITEM.stream().forEach(::bindComponents)
	}

	fun vanilla(): ItemStack = ItemStack(probe)

	fun customData(build: CompoundTag.() -> Unit): CustomData = CustomData.of(CompoundTag().apply(build))

	fun stack(build: CompoundTag.() -> Unit): ItemStack =
		ItemStack(probe).also { it.set(DataComponents.CUSTOM_DATA, customData(build)) }

	fun identified(id: String): ItemStack = stack { putString("id", id) }

	fun lored(vararg lines: String): ItemStack =
		identified("SPIRIT_SCEPTRE").also { it.set(DataComponents.LORE, ItemLore(lines.map(Component::literal))) }

	fun named(name: String): ItemStack =
		identified("PET").also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	private val probe: Item get() = Items.DIAMOND_SWORD

	private fun bindComponents(item: Item) {
		val holder = BuiltInRegistries.ITEM.wrapAsHolder(item) as Holder.Reference<Item>
		if (!holder.areComponentsBound()) holder.bindComponents(vanillaDefaults(item))
	}

	private fun vanillaDefaults(item: Item): DataComponentMap = DataComponentMap.builder()
		.set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
		.build()
}
