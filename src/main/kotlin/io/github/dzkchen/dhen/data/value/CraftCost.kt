package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRecipe
import io.github.dzkchen.dhen.data.repo.ItemRepo

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
			val price = price(recipe, depth)
			if (price > 0.0 && (cheapest == 0.0 || price < cheapest)) cheapest = price
		}
		walking.remove(marketId)
		if (cuts == cutsBefore) known[marketId] = cheapest
		return cheapest
	}

	private fun price(recipe: ItemRecipe, depth: Int): Double {
		var total = 0.0
		for ((ingredient, amount) in recipe.ingredients) {
			val price = Prices.priceOr(ingredient, source, Double.NaN)
			total += (if (price.isNaN()) of(ingredient, depth + 1) else price) * amount
		}
		return total / recipe.output
	}

	private companion object {
		private const val MAX_INGREDIENT_DEPTH = 24
	}
}
