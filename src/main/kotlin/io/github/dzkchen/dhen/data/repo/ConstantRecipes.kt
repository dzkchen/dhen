package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.event.withoutCodes
import java.util.Locale

private const val ESSENCE = "ESSENCE_"
private const val DUNGEON = "DUNGEON "
private const val SHINY = "SHINY "

private val RARITY_WORDS: List<String> =
	ItemRarity.entries.map { it.loreName }.sortedByDescending(String::length)

private val REFORGE_TYPES: Map<String, List<String>> = mapOf(
	"SWORD" to listOf("SWORD"),
	"LONGSWORD" to listOf("SWORD"),
	"WAND" to listOf("SWORD"),
	"BOW" to listOf("BOW"),
	"SHORTBOW" to listOf("BOW"),
	"HELMET" to listOf("ARMOR", "HELMET"),
	"CHESTPLATE" to listOf("ARMOR", "CHESTPLATE"),
	"LEGGINGS" to listOf("ARMOR"),
	"BOOTS" to listOf("ARMOR"),
	"PICKAXE" to listOf("PICKAXE"),
	"DRILL" to listOf("PICKAXE"),
	"CHISEL" to listOf("PICKAXE"),
	"HOE" to listOf("FARMING_TOOL", "HOE"),
	"SHOVEL" to listOf("FARMING_TOOL"),
	"SHEARS" to listOf("FARMING_TOOL"),
	"WATERING CAN" to listOf("FARMING_TOOL"),
	"FARMING TOOL" to listOf("FARMING_TOOL"),
	"AXE" to listOf("AXE"),
	"ROD" to listOf("ROD", "FISHING_ROD"),
	"FISHING ROD" to listOf("ROD", "FISHING_ROD"),
	"FISHING NET" to listOf("ROD", "FISHING_ROD"),
	"ACCESSORY" to listOf("ACCESSORY"),
	"HATCESSORY" to listOf("ACCESSORY"),
	"CARNIVAL MASK" to listOf("ACCESSORY"),
	"BELT" to listOf("EQUIPMENT", "BELT"),
	"CLOAK" to listOf("EQUIPMENT", "CLOAK"),
	"NECKLACE" to listOf("EQUIPMENT"),
	"GLOVES" to listOf("EQUIPMENT"),
	"BRACELET" to listOf("EQUIPMENT"),
	"VACUUM" to listOf("VACUUM"),
	"PET" to listOf("PET")
)

internal fun essenceUpgrades(constants: RepoConstants, known: Set<String>): List<ItemRecipe> {
	val upgrades = ArrayList<ItemRecipe>()
	for (id in constants.starredIds) {
		if (id !in known) continue
		val upgraded = ItemIngredient(id, 1)
		for ((index, tier) in constants.starTiers(id).withIndex()) {
			val paid = ArrayList<ItemIngredient>(tier.materials.size + 1)
			paid += ItemIngredient(ESSENCE + tier.essence, tier.essenceAmount)
			tier.materials.mapTo(paid) { ItemIngredient(it.key, it.value) }
			val star = index + 1
			upgrades += ItemRecipe(
				RecipeKind.ESSENCE_UPGRADE, id, paid, upgraded, 0, EssenceStar(star, tier.essence)
			)
		}
	}
	return upgrades
}

internal class ReforgeIndex private constructor(private val byType: Map<String, List<ItemRecipe>>) {
	val recipes: List<ItemRecipe> = byType.values.flatten().distinct()

	fun matching(item: RepoItem): List<ItemRecipe> {
		val matched = LinkedHashSet<ItemRecipe>()
		for (type in REFORGE_TYPES[loreType(item.lore)].orEmpty()) byType[type]?.let(matched::addAll)
		byType[item.id]?.let(matched::addAll)
		byType[item.itemId.uppercase(Locale.ROOT)]?.let(matched::addAll)
		return matched.toList()
	}

	internal companion object {
		val EMPTY = ReforgeIndex(emptyMap())

		fun of(constants: RepoConstants): ReforgeIndex {
			val byType = HashMap<String, MutableList<ItemRecipe>>()
			for (reforge in constants.stoneReforges + constants.blacksmithReforges) {
				val recipe = recipe(reforge)
				for (type in reforge.itemTypes) byType.getOrPut(type, ::ArrayList).add(recipe)
			}
			return if (byType.isEmpty()) EMPTY else ReforgeIndex(byType)
		}

		private fun recipe(reforge: Reforge): ItemRecipe {
			val applied = if (reforge.stone.isEmpty()) emptyList() else listOf(ItemIngredient(reforge.stone, 1))
			return ItemRecipe(
				RecipeKind.REFORGE,
				reforge.stone.ifEmpty { reforge.reforge },
				applied,
				ItemIngredient.NONE,
				0,
				ReforgeCard(
					reforge.reforge,
					reforge.stone,
					reforge.itemTypes,
					reforge.rarities,
					reforge.stats,
					reforge.ability,
					reforge.costs
				)
			)
		}
	}
}

internal fun loreType(lore: List<String>): String {
	val last = withoutCodes(lore.lastOrNull() ?: return "").uppercase(Locale.ROOT).trim().removePrefix(SHINY)
	val typed = RARITY_WORDS.firstOrNull(last::startsWith)?.let { last.substring(it.length) } ?: last
	return typed.trim().removePrefix(DUNGEON).trim()
}
