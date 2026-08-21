package io.github.dzkchen.dhen.data.profile

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StringTag
import java.io.ByteArrayOutputStream
import java.util.Base64

internal object BagFixture {
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
}
