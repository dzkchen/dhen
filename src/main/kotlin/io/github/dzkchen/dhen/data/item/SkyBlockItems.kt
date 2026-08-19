package io.github.dzkchen.dhen.data.item

import com.mojang.authlib.GameProfile
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

object SkyBlockItems {
	private const val CAPACITY = 512
	private const val MASK = CAPACITY - 1
	private const val PROBE = 4
	private const val TEXTURES = "textures"

	private val keys = arrayOfNulls<CustomData>(CAPACITY)
	private val records = arrayOfNulls<SkyBlockItem>(CAPACITY)
	private var evictionHand = 0

	internal val cachedRecords: Int get() = keys.count { it != null }

	fun of(stack: ItemStack): SkyBlockItem {
		val data = customData(stack) ?: return SkyBlockItem.NONE
		return of(data)
	}

	fun rarity(stack: ItemStack): ItemRarity = of(stack).rarity(stack)

	fun lore(stack: ItemStack): List<Component> = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines()

	fun skullTexture(stack: ItemStack): String? = profile(stack)?.properties?.get(TEXTURES)?.firstOrNull()?.value

	fun skullId(stack: ItemStack): String? = profile(stack)?.id?.toString()

	fun hasGlint(stack: ItemStack): Boolean = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == true

	fun memo(slots: Int): SlotMemo = SlotMemo(slots)

	internal fun of(data: CustomData): SkyBlockItem =
		stored(data) ?: SkyBlockItem.parse(data).also { store(data, it) }

	internal fun customData(stack: ItemStack): CustomData? {
		if (stack.isEmpty) return null
		val data = stack.get(DataComponents.CUSTOM_DATA)
		return if (data == null || data.isEmpty) null else data
	}

	private fun profile(stack: ItemStack): GameProfile? = stack.get(DataComponents.PROFILE)?.partialProfile()

	private fun stored(data: CustomData): SkyBlockItem? {
		val home = home(data)
		for (step in 0 until PROBE) {
			val slot = (home + step) and MASK
			if (keys[slot] === data) return records[slot]
		}
		return null
	}

	private fun store(data: CustomData, record: SkyBlockItem) {
		val home = home(data)
		for (step in 0 until PROBE) {
			val slot = (home + step) and MASK
			if (keys[slot] == null) {
				place(slot, data, record)
				return
			}
		}
		place((home + (evictionHand++ and (PROBE - 1))) and MASK, data, record)
	}

	private fun place(slot: Int, data: CustomData, record: SkyBlockItem) {
		keys[slot] = data
		records[slot] = record
	}

	private fun home(data: CustomData): Int {
		val hash = System.identityHashCode(data)
		return (hash xor (hash ushr 16)) and MASK
	}
}

class SlotMemo internal constructor(slots: Int) {
	private val keys = arrayOfNulls<CustomData>(slots)
	private val records = arrayOfNulls<SkyBlockItem>(slots)

	fun of(slot: Int, stack: ItemStack): SkyBlockItem {
		if (slot < 0 || slot >= records.size) return SkyBlockItems.of(stack)
		val fresh = SkyBlockItems.customData(stack)
		val held = keys[slot]
		val record = records[slot]
		if (record != null) {
			if (held === fresh) return record
			if (fresh != null && fresh == held) {
				keys[slot] = fresh
				return record
			}
		}
		val parsed = if (fresh == null) SkyBlockItem.NONE else SkyBlockItems.of(fresh)
		keys[slot] = fresh
		records[slot] = parsed
		return parsed
	}
}
