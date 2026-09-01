package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.pet.PetLines
import io.github.dzkchen.dhen.event.legacyCodes
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class PetNametags {
	private val nametag: Matcher = Pattern.compile(NAMETAG).matcher("")
	private val ids = IntArray(CAPACITY) { NO_ENTITY }
	private val sources = arrayOfNulls<Component>(CAPACITY)
	private val rewrites = arrayOfNulls<Component>(CAPACITY)
	private var hidesLevel = false
	private var hidesMaxLevel = false
	private var evictionHand = 0

	fun rewrite(entityId: Int, nameTag: Component, hideLevel: Boolean, hideMaxLevel: Boolean): Component {
		if (hideLevel != hidesLevel || hideMaxLevel != hidesMaxLevel) {
			hidesLevel = hideLevel
			hidesMaxLevel = hideMaxLevel
			ids.fill(NO_ENTITY)
		}
		val home = entityId and MASK
		for (step in 0 until PROBE) {
			val slot = (home + step) and MASK
			if (ids[slot] == entityId) return recall(slot, entityId, nameTag)
			if (ids[slot] == NO_ENTITY) return place(slot, entityId, nameTag)
		}
		return place((home + (evictionHand++ and (PROBE - 1))) and MASK, entityId, nameTag)
	}

	internal fun rewritten(styled: String, hideLevel: Boolean, hideMaxLevel: Boolean): Component? {
		if (!nametag.reset(styled).matches()) return null
		val level = nametag.group(LEVEL).toIntOrNull() ?: PetLines.UNKNOWN_LEVEL
		val hidden = hideLevel || (hideMaxLevel && PetLines.maxed(level))
		val named = nametag.group(RARITY) + nametag.group(PET) + (nametag.group(SKIN) ?: "")
		return Component.literal(if (hidden) named else nametag.group(START) + GAP + named)
	}

	private fun recall(slot: Int, entityId: Int, nameTag: Component): Component {
		val source = sources[slot]
		if (source === nameTag || source == nameTag) return rewrites[slot] ?: nameTag
		return place(slot, entityId, nameTag)
	}

	private fun place(slot: Int, entityId: Int, nameTag: Component): Component {
		val rewritten = rewritten(legacyCodes(nameTag), hidesLevel, hidesMaxLevel)
		ids[slot] = entityId
		sources[slot] = nameTag
		rewrites[slot] = rewritten
		return rewritten ?: nameTag
	}

	private companion object {
		private const val GAP = " "
		private const val NAMETAG =
			"(?<start>§8\\[§7Lv(?<lvl>\\d+)§8]) (?<rarity>§.)(?<pet>[\\w\\s]+)(?<skin>§. ✦)?"
		private const val CAPACITY = 64
		private const val MASK = CAPACITY - 1
		private const val PROBE = 4
		private const val NO_ENTITY = -1
		private const val START = "start"
		private const val LEVEL = "lvl"
		private const val RARITY = "rarity"
		private const val PET = "pet"
		private const val SKIN = "skin"
	}
}

internal class PetSlotLevels(slots: Int) {
	private val names = arrayOfNulls<Component>(slots)
	private val levels = IntArray(slots)

	fun of(slot: Int, stack: ItemStack): Int {
		val name = stack.hoverName
		if (slot < 0 || slot >= names.size) return PetLines.level(name.string)
		val held = names[slot]
		if (held === name || held == name) return levels[slot]
		val level = PetLines.level(name.string)
		names[slot] = name
		levels[slot] = level
		return level
	}
}
