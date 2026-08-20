package io.github.dzkchen.dhen.data.item

import io.github.dzkchen.dhen.event.legacyCodes
import net.minecraft.ChatFormatting
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

enum class ItemRarity(val baseColor: ChatFormatting, val magicalPower: Int) {
	NONE(ChatFormatting.GRAY, 0),
	COMMON(ChatFormatting.WHITE, 3),
	UNCOMMON(ChatFormatting.GREEN, 5),
	RARE(ChatFormatting.BLUE, 8),
	EPIC(ChatFormatting.DARK_PURPLE, 12),
	LEGENDARY(ChatFormatting.GOLD, 16),
	MYTHIC(ChatFormatting.LIGHT_PURPLE, 22),
	DIVINE(ChatFormatting.AQUA, 0),
	SUPREME(ChatFormatting.DARK_RED, 0),
	ULTIMATE(ChatFormatting.DARK_RED, 0),
	SPECIAL(ChatFormatting.RED, 3),
	VERY_SPECIAL(ChatFormatting.RED, 5);

	val loreName: String = name.replace('_', ' ')

	val colorCode: String = baseColor.toString()

	companion object {
		private val lorePattern: Pattern = Pattern.compile(
			entries.joinToString("|") { "((?:${it.colorCode}§l)+(?:SHINY )?${it.loreName})" }
		)

		private val petNamePattern: Pattern = Pattern.compile("§7\\[Lvl \\d+](?: §8\\[.*])? (§[0-9a-fk-or]).+")

		private val loreMatcher = ThreadLocal.withInitial { lorePattern.matcher("") }

		private val petNameMatcher = ThreadLocal.withInitial { petNamePattern.matcher("") }

		internal fun byColorCode(code: String): ItemRarity? = entries.find { it.colorCode == code }

		internal fun of(stack: ItemStack): ItemRarity {
			val lore = SkyBlockItems.lore(stack)
			for (index in lore.indices.reversed()) fromLoreLine(legacyCodes(lore[index]))?.let { return it }
			return fromPetName(legacyCodes(stack.hoverName)) ?: NONE
		}

		internal fun fromLoreLine(line: String): ItemRarity? {
			val matcher = loreMatcher.get().reset(line)
			if (!matcher.find()) return null
			for (group in 1..matcher.groupCount()) if (matcher.start(group) >= 0) return entries[group - 1]
			return null
		}

		internal fun fromPetName(name: String): ItemRarity? {
			val matcher = petNameMatcher.get().reset(name)
			if (!matcher.find()) return null
			return byColorCode(name.substring(matcher.start(1), matcher.end(1)))
		}
	}
}
