package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRecipe
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RecipeKind

internal class CraftCost(private val source: PriceSource) {
	private val known = HashMap<String, Double>()
	private val walking = HashSet<String>()

	private var cuts = 0

	fun of(marketId: String): Double = of(marketId, 0)

	private fun of(marketId: String, depth: Int): Double {
		known[marketId]?.let { return it }
		if (depth >= MAX_INGREDIENT_DEPTH || !walking.add(marketId)) {
			cuts++
			return 0.0
		}
		val cutsBefore = cuts
		var cheapest = 0.0
		for (recipe in ItemRepo.item(marketId)?.recipes.orEmpty()) {
			if (recipe.kind !in MADE_BY_HAND) continue
			val price = price(recipe, depth)
			if (price > 0.0 && (cheapest == 0.0 || price < cheapest)) cheapest = price
		}
		walking.remove(marketId)
		if (cuts == cutsBefore) known[marketId] = cheapest
		return cheapest
	}

	private fun price(recipe: ItemRecipe, depth: Int): Double {
		var total = 0.0
		for (ingredient in recipe.ingredients) {
			if (!ingredient.present) continue
			val price = Prices.priceOr(ingredient.id, source, Double.NaN)
			total += (if (price.isNaN()) of(ingredient.id, depth + 1) else price) * ingredient.count
		}
		return total / recipe.output.count
	}

	private companion object {
		private const val MAX_INGREDIENT_DEPTH = 24

		private val MADE_BY_HAND = setOf(RecipeKind.CRAFTING, RecipeKind.FORGE)
	}
}
