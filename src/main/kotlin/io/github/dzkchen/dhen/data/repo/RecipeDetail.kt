package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.Island

sealed interface RecipeDetail

data object PlainRecipe : RecipeDetail

class MobDrop internal constructor(val mob: String, val render: String, val chance: String) : RecipeDetail

class TradeRange internal constructor(val minimum: Int, val maximum: Int) : RecipeDetail {
	val variable: Boolean get() = maximum > minimum
}

class NpcPlace internal constructor(
	val island: Island,
	private val mode: String,
	val x: Int,
	val y: Int,
	val z: Int,
	val links: List<String>
) : RecipeDetail {
	val located: Boolean get() = x != 0 || y != 0 || z != 0

	val where: String get() = island.displayName ?: mode
}

class WikiCard internal constructor(val links: List<String>, val requirement: String, val slayer: String) :
	RecipeDetail

class EssenceStar internal constructor(val star: Int, val essence: String) : RecipeDetail

class ReforgeCard internal constructor(
	val reforge: String,
	val stone: String,
	val itemTypes: List<String>,
	val rarities: List<String>,
	val stats: Map<String, Map<String, Double>>,
	val ability: Map<String, String>,
	val costs: Map<String, Long>
) : RecipeDetail {
	val blacksmith: Boolean get() = stone.isEmpty()
}

class MutationPlot internal constructor(
	val name: String,
	val rarity: String,
	val size: Int,
	val surface: String,
	val watered: Boolean,
	val stages: Int,
	val copper: Int,
	private val rows: List<List<String>>,
	val spreading: List<SpreadingCondition>,
	val effects: List<MutationEffect>,
	val mechanic: String
) : RecipeDetail {
	fun cell(row: Int, column: Int): String =
		rows.getOrNull(row)?.getOrNull(column) ?: EMPTY

	fun grown(row: Int, column: Int): Boolean = cell(row, column) == TARGET

	fun plantedAt(row: Int, column: Int): String {
		val cell = cell(row, column)
		return if (cell.startsWith(PLANTED)) cell.substring(PLANTED.length) else ""
	}

	internal companion object {
		const val EMPTY = "EMPTY"
		const val TARGET = "TARGET"
		const val PLANTED = "INGREDIENT:"
	}
}

class SpreadingCondition internal constructor(val id: String, val count: Int, val text: String)

class MutationEffect internal constructor(val name: String, val description: String)
