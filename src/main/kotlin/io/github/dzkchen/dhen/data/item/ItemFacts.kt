package io.github.dzkchen.dhen.data.item

import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.matcher
import net.minecraft.world.item.ItemStack

object ItemFacts {
	const val SKYBLOCK_MENU = "SKYBLOCK_MENU"
	const val NO_CATEGORY = ""

	val WEARABLE = setOf(
		"HELMET",
		"CHESTPLATE",
		"LEGGINGS",
		"BOOTS",
		"NECKLACE",
		"BELT",
		"CLOAK",
		"GLOVES",
		"BRACELET",
		"CARNIVAL_MASK"
	)

	fun cleanName(stack: ItemStack): String = withoutCodes(stack.hoverName.string)

	fun isSkyBlockMenu(stack: ItemStack): Boolean = SkyBlockItems.of(stack).id == SKYBLOCK_MENU

	fun isVanilla(stack: ItemStack): Boolean = SkyBlockItems.of(stack).id.isEmpty()

	fun isSack(stack: ItemStack): Boolean =
		SkyBlockItems.of(stack).id.endsWith(SACK_SUFFIX) && cleanName(stack).endsWith(SACK_NAME)

	fun isSoulbound(stack: ItemStack): Boolean = hasLoreLine(stack, SOULBOUND)

	fun isAnySoulbound(stack: ItemStack): Boolean = isSoulbound(stack) || hasLoreLine(stack, COOP_SOULBOUND)

	fun isRiftTransferable(stack: ItemStack): Boolean = matchesPlainLore(stack, riftTransferable.get())

	fun isRiftExportable(stack: ItemStack): Boolean = matchesPlainLore(stack, riftExportable.get())

	fun category(stack: ItemStack): String {
		val lore = SkyBlockItems.rawLore(stack)
		val matcher = rarityLine.get()
		for (index in lore.indices.reversed()) {
			val line = lore[index].string
			if (notRarityLine.get().reset(line).matches()) continue
			if (!matcher.reset(line).matches()) continue
			return matcher.group(1).orEmpty().replace(' ', '_')
		}
		return NO_CATEGORY
	}

	fun hasLoreLine(stack: ItemStack, text: String): Boolean {
		val lore = SkyBlockItems.rawLore(stack)
		for (index in lore.indices) if (withoutCodes(lore[index].string) == text) return true
		return false
	}

	fun loreContains(stack: ItemStack, fragment: String): Boolean {
		val lore = SkyBlockItems.rawLore(stack)
		for (index in lore.indices) if (lore[index].string.contains(fragment)) return true
		return false
	}

	fun codedLoreHas(stack: ItemStack, fragment: String): Boolean {
		val lore = SkyBlockItems.rawLore(stack)
		for (index in lore.indices) if (legacyCodes(lore[index]).contains(fragment)) return true
		return false
	}

	private fun matchesPlainLore(stack: ItemStack, matcher: java.util.regex.Matcher): Boolean {
		val lore = SkyBlockItems.rawLore(stack)
		for (index in lore.indices) if (matcher.reset(withoutCodes(lore[index].string)).matches()) return true
		return false
	}

	private const val SACK_SUFFIX = "_SACK"
	private const val SACK_NAME = " Sack"
	private const val SOULBOUND = "* Soulbound *"
	private const val COOP_SOULBOUND = "* Co-op Soulbound *"

	private val rarities: String =
		ItemRarity.entries.filter { it != ItemRarity.NONE }.joinToString("|") { it.loreName }

	private val rarityLine = matcher(
		"^(?:Rarity: )?(?:a )?(?:SHINY )?(?:$rarities)(?: DUNGEON)? ?([A-Z].*?|)(?: a)?(?: \\(ID \\w\\d+\\))?$"
	)

	private val notRarityLine = matcher("^RARE CROPS?$")

	private val riftTransferable = matcher("X Rift-Transferable X")

	private val riftExportable = matcher("X Rift-Export(?:able|ed) X")
}
